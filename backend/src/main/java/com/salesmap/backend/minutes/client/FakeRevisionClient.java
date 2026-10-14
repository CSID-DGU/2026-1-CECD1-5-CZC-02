package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * D의 수정 기록 API가 생기기 전까지 쓰는 가짜. 저장하지 않고 로그로만 남긴다.
 * TODO(B): D의 API가 생기면(10/23 예정) 이 클래스를 지우고 D의 구현을 쓴다.
 */
@Component
public class FakeRevisionClient implements RevisionClient {

    private static final Logger log = LoggerFactory.getLogger(FakeRevisionClient.class);

    private final ObjectMapper objectMapper;

    public FakeRevisionClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.warn("[MINUTES] D 수정 기록 API가 아직 없어 회의록 수정 기록을 로그로만 남깁니다 (저장 안 됨).");
    }

    @Override
    public void record(RevisionRecord revision) {
        try {
            log.info("[MINUTES][FAKE_REVISION] {}", objectMapper.writeValueAsString(revision));
        } catch (JsonProcessingException exception) {
            log.info("[MINUTES][FAKE_REVISION] targetId={}, fieldName={}, action={}",
                    revision.targetId(), revision.fieldName(), revision.action());
        }
    }
}
