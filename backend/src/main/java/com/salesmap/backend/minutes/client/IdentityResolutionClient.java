package com.salesmap.backend.minutes.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A(인물 식별) 호출 자리. 초안 만들기의 첫 단계.
 * <p>
 * A가 실제 구현을 빈으로 등록하면 {@link FakeIdentityResolutionClient}를 지운다.
 */
public interface IdentityResolutionClient {

    /**
     * 만난 사람(메일이면 발신·수신자)을 인물 대장과 맞춘다.
     *
     * @param sourceId 원본 ID (예: src_101). identity_resolution의 mention.sourceRef가 된다
     * @param mentions 상대 단서 (이름, 메일이면 이메일도)
     * @return contracts/identity_resolution.schema.json 형식 배열. 식별하지 못한 단서는 빠질 수 있다
     */
    List<JsonNode> resolve(String sourceId, List<AttendeeMention> mentions, Long userId);
}
