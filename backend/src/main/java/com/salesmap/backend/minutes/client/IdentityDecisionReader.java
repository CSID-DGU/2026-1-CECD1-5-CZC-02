package com.salesmap.backend.minutes.client;

import java.util.Map;

/**
 * 확인 화면에서 사람이 고른 인물(A가 저장한 식별 결과)을 B가 읽는 자리.
 * <p>
 * B 회의록은 확정할 때 이 값으로 참석자·사람별 칸의 personId를 채운다. A 저장소는 읽기만 한다.
 */
public interface IdentityDecisionReader {

    /**
     * @param sourceId 회의록 원본 ID (예: src_101)
     * @return resolutionId → personId. decision=CONFIRMED이고 personId가 있는 것만. 없으면 빈 맵
     */
    Map<String, String> decidedPersonIds(String sourceId, Long userId);
}
