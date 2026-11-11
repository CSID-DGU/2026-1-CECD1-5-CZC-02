package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.web.client.RestClientException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 계약 응답의 형식·RULE 정책·요청 대응·사용자 대장 참조를 검사한다. 판정 로직 자체는 A API에 둔다. */
final class IdentityResponseValidator {
    private static final Set<String> REQUIRED = Set.of("resolutionId", "mention", "decision", "personId", "candidates", "decidedBy");
    private static final Set<String> ALLOWED = Set.of("resolutionId", "mention", "decision", "personId", "candidates", "decidedBy", "modelVersion");
    private static final Set<String> CANDIDATE = Set.of("personId", "score", "reasons");

    private IdentityResponseValidator() {}

    static List<JsonNode> validate(JsonNode response, ArrayNode mentions, List<PersonDocument> roster) {
        require(response != null && response.isArray() && response.size() == mentions.size());
        Set<String> people = roster.stream().map(PersonDocument::personId).collect(Collectors.toSet());
        Set<String> resolutionIds = new HashSet<>();
        List<JsonNode> result = new ArrayList<>();
        for (int i = 0; i < response.size(); i++) {
            JsonNode row = response.get(i);
            object(row, REQUIRED, ALLOWED);
            String id = text(row.get("resolutionId"));
            require(id.matches("ir_[0-9a-z_]+") && resolutionIds.add(id));
            require(mentions.get(i).equals(row.get("mention")));
            require("RULE".equals(text(row.get("decidedBy"))));
            if (row.has("modelVersion")) require(row.get("modelVersion").isNull() || row.get("modelVersion").isTextual());
            String decision = text(row.get("decision"));
            require(Set.of("CONFIRMED", "CANDIDATES", "NEW").contains(decision));
            JsonNode candidates = row.get("candidates");
            require(candidates.isArray() && candidates.size() <= 3);
            Set<String> candidateIds = new HashSet<>();
            for (JsonNode candidate : candidates) {
                object(candidate, CANDIDATE, CANDIDATE);
                String personId = text(candidate.get("personId"));
                require(people.contains(personId) && candidateIds.add(personId));
                require(candidate.get("score").isNull());
                JsonNode reasons = candidate.get("reasons");
                require(reasons.isArray() && !reasons.isEmpty());
                for (JsonNode reason : reasons) require(reason.isTextual() && !reason.textValue().isBlank());
            }
            JsonNode personId = row.get("personId");
            switch (decision) {
                case "CONFIRMED" -> {
                    require(personId.isTextual() && people.contains(personId.textValue()));
                    require(candidateIds.isEmpty() || (candidateIds.size() == 1 && candidateIds.contains(personId.textValue())));
                }
                case "CANDIDATES" -> require(personId.isNull() && !candidateIds.isEmpty());
                case "NEW" -> require(personId.isNull() && candidateIds.isEmpty());
                default -> throw new IllegalStateException();
            }
            result.add(row.deepCopy());
        }
        return List.copyOf(result);
    }

    private static void object(JsonNode node, Set<String> required, Set<String> allowed) {
        require(node != null && node.isObject());
        required.forEach(field -> require(node.has(field)));
        node.fieldNames().forEachRemaining(field -> require(allowed.contains(field)));
    }

    private static String text(JsonNode node) {
        require(node != null && node.isTextual());
        return node.textValue();
    }

    private static void require(boolean condition) {
        if (!condition) throw new RestClientException("인물 식별 응답 형식·요청 대응·대장 참조 검사가 실패했습니다.");
    }
}
