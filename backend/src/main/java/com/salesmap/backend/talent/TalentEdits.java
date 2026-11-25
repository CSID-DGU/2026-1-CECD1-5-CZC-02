package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 확인 화면에서 사람이 고친 값을 resume_profile에 반영한다.
 * 고칠 수 있는 칸: name · birthYear · grade · skills · industries.
 * 총 경력·투입 가능일·프로젝트는 원문 기간으로 규칙이 계산한 값이라 화면에서 직접 고치지 않는다.
 * 사람이 넣은 값은 source=HUMAN, evidence는 원문이 아니라는 표시 문장으로 둔다.
 */
final class TalentEdits {
    static final String HUMAN_EVIDENCE = "확인 화면에서 사람이 입력";
    static final Set<String> EDITABLE = Set.of("name", "birthYear", "grade", "skills", "industries");
    static final Set<String> GRADES = Set.of("JUNIOR", "INTERMEDIATE", "ADVANCED", "EXPERT");
    static final Set<String> CATEGORIES = Set.of("LANGUAGE", "FRAMEWORK", "DBMS", "OS", "MACHINE", "TOOL", "MIDDLEWARE", "OTHER");
    private static final List<String> MISSING_ORDER = List.of("name", "birthYear", "grade", "projects", "skills",
            "industries", "totalCareer", "availableFrom", "availableFrom.date");
    private static final int MAX_ITEMS = 200;
    private static final int MAX_TEXT = 100;

    private TalentEdits() {}

    /** 바뀐 칸 하나. 서비스가 RevisionService로 수정 기록(screen=RESUME_REVIEW)을 남긴다. */
    record Change(String fieldName, JsonNode proposedValue, JsonNode finalValue, String proposedBy) {}

    static List<Change> apply(ObjectNode profile, JsonNode patch, ObjectMapper mapper) {
        if (patch == null || !patch.isObject() || patch.isEmpty())
            throw new IllegalArgumentException("고칠 값을 보내 주세요.");
        patch.fieldNames().forEachRemaining(key -> {
            if (!EDITABLE.contains(key)) throw new IllegalArgumentException("고칠 수 없는 칸입니다: " + key);
        });
        String fallback = "LLM".equals(profile.path("generatedBy").asText()) ? "LLM" : "RULE";
        List<Change> changes = new ArrayList<>();

        if (patch.has("name")) {
            String value = cleanText(patch.get("name"), "이름");
            editScalar(profile, "name", value == null ? null : mapper.getNodeFactory().textNode(value), changes, fallback);
        }
        if (patch.has("birthYear")) {
            JsonNode node = patch.get("birthYear");
            if (!node.isNull() && (!node.isIntegralNumber() || node.asInt() < 1900 || node.asInt() > 2100))
                throw new IllegalArgumentException("생년은 1900~2100 사이 숫자로 입력해 주세요.");
            editScalar(profile, "birthYear", node.isNull() ? null : mapper.getNodeFactory().numberNode(node.asInt()),
                    changes, fallback);
        }
        if (patch.has("grade")) {
            JsonNode node = patch.get("grade");
            if (!node.isNull() && !GRADES.contains(node.asText()))
                throw new IllegalArgumentException("등급은 JUNIOR·INTERMEDIATE·ADVANCED·EXPERT 중 하나입니다.");
            editScalar(profile, "grade", node.isNull() ? null : node, changes, fallback);
        }
        if (patch.has("skills")) editList(profile, "skills", patch.get("skills"), changes, fallback, mapper);
        if (patch.has("industries")) editList(profile, "industries", patch.get("industries"), changes, fallback, mapper);

        profile.set("missingFields", missingFields(profile, mapper));
        return changes;
    }

    private static void editScalar(ObjectNode profile, String field, JsonNode value, List<Change> changes, String fallback) {
        JsonNode before = profile.path(field);
        JsonNode beforeValue = before.isObject() ? before.get("value") : NullNode.getInstance();
        JsonNode afterValue = value == null ? NullNode.getInstance() : value;
        if (beforeValue.equals(afterValue)) return;
        if (value == null) {
            profile.putNull(field);
        } else {
            ObjectNode node = profile.putObject(field);
            node.set("value", value);
            node.put("evidence", HUMAN_EVIDENCE);
            node.put("source", "HUMAN");
        }
        changes.add(new Change(field, beforeValue, afterValue, proposedBy(before, fallback)));
    }

    /** skills: 이름 문자열 또는 {name, category}. industries: 이름 문자열. 남긴 항목은 원래 근거를 그대로 둔다. */
    private static void editList(ObjectNode profile, String field, JsonNode request, List<Change> changes,
                                 String fallback, ObjectMapper mapper) {
        boolean skills = "skills".equals(field);
        String label = skills ? "기술" : "산업";
        if (!request.isArray() || request.size() > MAX_ITEMS) throw new IllegalArgumentException(label + " 목록이 올바르지 않습니다.");
        Map<String, JsonNode> current = new LinkedHashMap<>();
        profile.path(field).forEach(item -> current.put(item.path("name").asText(), item));
        ArrayNode next = mapper.createArrayNode();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : request) {
            String name = cleanText(skills && item.isObject() ? item.get("name") : item, label + " 이름");
            if (name == null || !seen.add(name)) continue;
            JsonNode kept = current.get(name);
            if (kept != null) {
                next.add(kept);
                continue;
            }
            ObjectNode added = next.addObject();
            added.put("name", name);
            if (skills) {
                String category = item.isObject() && item.hasNonNull("category") ? item.get("category").asText() : "OTHER";
                if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("기술 분류가 올바르지 않습니다: " + category);
                added.put("category", category);
                added.put("known", false);
                added.putArray("raw").add(name);
                added.put("projectCount", 0);
            }
            added.put("evidence", HUMAN_EVIDENCE);
            added.put("source", "HUMAN");
        }
        List<String> before = new ArrayList<>(current.keySet());
        List<String> after = new ArrayList<>(seen);
        if (before.equals(after)) return;
        String proposer = listProposer(current.values(), fallback);
        profile.set(field, next);
        changes.add(new Change(field, mapper.valueToTree(before), mapper.valueToTree(after), proposer));
    }

    /** AI 모듈의 missing_fields와 같은 규칙. */
    static ArrayNode missingFields(JsonNode profile, ObjectMapper mapper) {
        List<String> missing = new ArrayList<>();
        for (String name : List.of("name", "birthYear", "grade", "totalCareer", "availableFrom"))
            if (profile.path(name).isNull() || profile.path(name).isMissingNode()) missing.add(name);
        for (String name : List.of("projects", "skills", "industries"))
            if (profile.path(name).isEmpty()) missing.add(name);
        if (profile.path("availableFrom").isObject() && profile.path("availableFrom").path("date").isNull())
            missing.add("availableFrom.date");
        missing.sort((a, b) -> MISSING_ORDER.indexOf(a) - MISSING_ORDER.indexOf(b));
        ArrayNode array = mapper.createArrayNode();
        missing.forEach(array::add);
        return array;
    }

    private static String cleanText(JsonNode node, String label) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw new IllegalArgumentException(label + "은(는) 글자로 입력해 주세요.");
        String value = node.asText().strip();
        if (value.isEmpty()) return null;
        if (value.length() > MAX_TEXT) throw new IllegalArgumentException(label + "이(가) 너무 깁니다.");
        return value;
    }

    /** 원래 값을 낸 쪽. 사람이 넣은 값이거나 값이 없었으면 프로필을 만든 방식으로 둔다 (revision.proposedBy는 RULE·MODEL·LLM). */
    private static String proposedBy(JsonNode before, String fallback) {
        String source = before.path("source").asText("");
        return "RULE".equals(source) || "LLM".equals(source) ? source : fallback;
    }

    private static String listProposer(Iterable<JsonNode> items, String fallback) {
        for (JsonNode item : items) if ("LLM".equals(item.path("source").asText())) return "LLM";
        for (JsonNode item : items) if ("RULE".equals(item.path("source").asText())) return "RULE";
        return fallback;
    }
}
