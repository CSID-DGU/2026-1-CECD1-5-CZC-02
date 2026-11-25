package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.revision.dto.RevisionResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 인력 화면에 주는 응답 모양. resume_profile은 document에 그대로 담는다. */
public final class TalentViews {
    private TalentViews() {}

    public record UploadRequest(String fileName, String contentBase64) {}

    /** 목록 한 줄. llmFailed=LLM이 실패해 규칙 결과만 남음(화면에서 표시). */
    public record Summary(String profileId, String status, String fileName, String name, Integer birthYear, String grade,
                   Integer careerMonths, LocalDate availableFrom, String availableBasis, List<String> skills,
                   List<String> industries, int projectCount, List<String> missingFields, boolean llmFailed,
                   String errorMessage, Integer queuePosition, LocalDateTime createdAt, LocalDateTime confirmedAt) {}

    /** 같은 사람일 수 있는 다른 후보 (이름과 생년이 같음). 합칠지는 사람이 정한다. */
    public record DuplicateCandidate(String profileId, String status, String name, Integer birthYear, String fileName) {}

    public record Detail(Summary summary, JsonNode document, String sourceText, String llmProvider, String llmModel,
                  String llmFallbackReason, List<DuplicateCandidate> duplicates, List<RevisionResponse> edits) {}
}
