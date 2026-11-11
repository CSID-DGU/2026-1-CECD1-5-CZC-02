package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 등록 요청만 검사한다. personId와 소유자는 서버가 정하며 클라이언트 입력으로 받지 않는다. */
final class PersonRequest {
    private static final Set<String> FIELDS = Set.of("name", "nameVariants", "company", "department", "title",
            "emails", "phones", "channels", "companyHistory", "photoAvailable", "isSynthetic");
    private static final Set<String> CHANNELS = Set.of("EMAIL", "JANDI", "KAKAO", "PHONE", "OPEN_MARKET", "MEETING");
    private static final Set<String> HISTORY_FIELDS = Set.of("company", "title", "until");

    private PersonRequest() {}

    static PersonDocument parse(JsonNode body) {
        object(body, FIELDS);
        if (body.toString().length() > 64000) throw invalid("등록 요청은 64,000자 이하여야 합니다.");
        String name = requiredString(body, "name");
        if (name.isBlank()) throw invalid("대표 이름은 비워 둘 수 없습니다.");
        JsonNode synthetic = body.get("isSynthetic");
        if (synthetic == null || !synthetic.isBoolean()) throw invalid("isSynthetic은 필수 boolean입니다.");
        Boolean photo = null;
        if (body.has("photoAvailable")) {
            if (!body.get("photoAvailable").isBoolean()) throw invalid("photoAvailable은 boolean이어야 합니다.");
            photo = body.get("photoAvailable").booleanValue();
        }
        List<String> channels = strings(body, "channels");
        if (!CHANNELS.containsAll(channels)) throw invalid("허용되지 않은 접촉 채널입니다.");
        List<PersonDocument.CompanyHistory> history = new ArrayList<>();
        if (body.has("companyHistory")) {
            JsonNode items = body.get("companyHistory");
            if (!items.isArray()) throw invalid("companyHistory는 배열이어야 합니다.");
            for (JsonNode item : items) {
                object(item, HISTORY_FIELDS);
                String company = requiredString(item, "company");
                String until = optionalString(item, "until");
                if (until != null) {
                    if (!until.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid("이력 날짜는 YYYY-MM-DD 형식이어야 합니다.");
                    try { LocalDate.parse(until); }
                    catch (DateTimeParseException error) { throw invalid("유효하지 않은 이력 날짜입니다."); }
                }
                history.add(new PersonDocument.CompanyHistory(company, optionalString(item, "title"), until));
            }
        }
        return new PersonDocument(null, name, strings(body, "nameVariants"), optionalString(body, "company"),
                optionalString(body, "department"), optionalString(body, "title"), strings(body, "emails"),
                strings(body, "phones"), channels, List.copyOf(history), photo, synthetic.booleanValue());
    }

    private static void object(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject()) throw invalid("JSON 객체가 필요합니다.");
        node.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw invalid("등록할 수 없는 필드가 있습니다.");
        });
    }

    private static String requiredString(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isTextual()) throw invalid(field + "는 필수 문자열입니다.");
        return node.get(field).textValue();
    }

    private static String optionalString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid(field + "는 문자열 또는 null이어야 합니다.");
        return value.textValue();
    }

    private static List<String> strings(JsonNode node, String field) {
        if (!node.has(field)) return List.of();
        if (!node.get(field).isArray()) throw invalid(field + "는 배열이어야 합니다.");
        List<String> result = new ArrayList<>();
        for (JsonNode value : node.get(field)) {
            if (!value.isTextual()) throw invalid(field + "의 항목은 문자열이어야 합니다.");
            result.add(value.textValue());
        }
        return List.copyOf(result);
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
