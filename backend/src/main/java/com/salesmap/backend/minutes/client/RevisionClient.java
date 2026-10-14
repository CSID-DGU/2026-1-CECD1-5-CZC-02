package com.salesmap.backend.minutes.client;

/**
 * D의 수정 기록(ML-4) 저장 API 호출 자리 (D-002, 10/23 예정).
 * <p>
 * D가 실제 구현을 빈으로 등록하면 {@link FakeRevisionClient}를 지운다.
 */
public interface RevisionClient {

    void record(RevisionRecord revision);
}
