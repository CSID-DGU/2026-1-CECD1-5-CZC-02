package com.salesmap.backend.minutes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * contracts/meeting_note.schema.json 형식 그대로의 회의록. C(할 일)에 이 모양 그대로 넘긴다.
 * 필드를 추가·이름 변경하지 않는다 (바꾸려면 [contract] PR).
 * <p>
 * 스키마에서 null을 허용하지 않는 선택 필드(perPerson·uncertainSegments·generatedBy)는 값이 없으면 JSON에서 뺀다.
 */
public record MeetingNoteDocument(
        String noteId,
        List<SourceRef> sourceRefs,
        // "YYYY-MM-DDTHH:mm:ss" (KST, 시간대 표기 없음). 모르면 null
        String occurredAt,
        String location,
        String mode,
        List<Participant> participants,
        Fields fields,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<PerPerson> perPerson,
        List<String> missingFields,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<UncertainSegment> uncertainSegments,
        String status,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String generatedBy,
        String llmModel
) {

    public record SourceRef(
            String sourceId,
            String sourceType
    ) {
    }

    /** 상대측 참석자. resolutionId·personId는 A의 식별 결과 값 그대로, 식별 전·후보 상태면 null. */
    public record Participant(
            String displayName,
            String resolutionId,
            String personId
    ) {
    }

    /** 내용 항목 하나. evidence는 근거가 된 원문 문장 그대로. */
    public record Item(
            String text,
            String evidence
    ) {
    }

    public record Fields(
            List<Item> discussion,
            List<Item> customerRequests,
            List<Item> concerns,
            List<Item> commitments,
            List<Item> nextContact
    ) {
    }

    /** LIGHT 모드의 사람별 3칸. 모르는 칸은 null. */
    public record PerPerson(
            String displayName,
            String personId,
            Item status,
            Item request,
            Item followUp
    ) {
    }

    public record UncertainSegment(
            String text,
            String reason
    ) {
    }
}
