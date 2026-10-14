package com.salesmap.backend.minutes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사람이 확인 화면에서 고칠 수 있는 회의록 값. AI 원본 스냅샷과 수정 기록 비교에 같은 기준을 쓴다.
 * <p>
 * 키는 수정 기록의 fieldName이다. FULL은 "fields.commitments"처럼 문항 단위,
 * LIGHT는 missingFields 표기(D-018)와 같은 "perPerson[0].status" 칸 단위.
 */
final class MeetingNoteEditableFields {

    private MeetingNoteEditableFields() {
    }

    static Map<String, JsonNode> valuesOf(MeetingNoteDocument document, ObjectMapper objectMapper) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        if ("LIGHT".equals(document.mode())) {
            List<MeetingNoteDocument.PerPerson> perPerson = document.perPerson() == null ? List.of() : document.perPerson();
            for (int index = 0; index < perPerson.size(); index++) {
                MeetingNoteDocument.PerPerson person = perPerson.get(index);
                String prefix = "perPerson[" + index + "].";
                values.put(prefix + "status", toNode(person.status(), objectMapper));
                values.put(prefix + "request", toNode(person.request(), objectMapper));
                values.put(prefix + "followUp", toNode(person.followUp(), objectMapper));
            }
            return values;
        }

        MeetingNoteDocument.Fields fields = document.fields();
        values.put("fields.discussion", toNode(fields.discussion(), objectMapper));
        values.put("fields.customerRequests", toNode(fields.customerRequests(), objectMapper));
        values.put("fields.concerns", toNode(fields.concerns(), objectMapper));
        values.put("fields.commitments", toNode(fields.commitments(), objectMapper));
        values.put("fields.nextContact", toNode(fields.nextContact(), objectMapper));
        return values;
    }

    /** null은 JSON null로 통일해 "값 없음"끼리는 같은 값으로 본다. */
    static JsonNode toNode(Object value, ObjectMapper objectMapper) {
        if (value == null) {
            return NullNode.getInstance();
        }
        JsonNode node = objectMapper.valueToTree(value);
        return node == null ? NullNode.getInstance() : node;
    }
}
