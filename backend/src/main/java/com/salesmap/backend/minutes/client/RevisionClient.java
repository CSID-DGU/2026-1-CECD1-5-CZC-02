package com.salesmap.backend.minutes.client;

/**
 * 회의록 수정 기록(ML-4)을 D에 넘기는 자리. 실제 구현은 D의 RevisionService를 직접 부르는 {@link RevisionServiceClient}.
 */
public interface RevisionClient {

    /** @param userId 기록의 소유자(확인한 사용자). D가 actorId·decidedAt을 이 값과 서버 시각으로 채운다 */
    void record(RevisionRecord revision, Long userId);
}
