package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;

/** AI 모듈 POST /resumes/extract 호출. 테스트에서는 가짜로 바꾼다. */
public interface ResumeExtractionClient {

    Extraction extract(String fileName, byte[] content, String profileId, String sourceId);

    /** resumeProfile은 resume_profile 형식, sourceText는 근거(evidence)가 가리키는 원문. */
    record Extraction(JsonNode resumeProfile, String sourceText, String llmProvider, String llmModel,
                      String llmFallbackReason) {}

    /** 이력서를 읽지 못함. 메시지는 사용자에게 그대로 보여 줄 수 있는 말이어야 한다 (원문을 담지 않는다). */
    class ExtractionException extends RuntimeException {
        public ExtractionException(String message) { super(message); }
    }
}
