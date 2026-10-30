package com.salesmap.backend.minutes.client;

import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.service.RevisionService;
import org.springframework.stereotype.Component;

/**
 * 회의록 수정 기록을 D의 RevisionService로 저장한다. 같은 백엔드라 HTTP가 아니라 서비스를 직접 부르고,
 * 호출한 쪽(회의록 수정·확정)의 트랜잭션에 함께 묶인다 — 기록 저장이 실패하면 수정·확정도 되돌린다.
 */
@Component
public class RevisionServiceClient implements RevisionClient {

    private final RevisionService revisionService;

    public RevisionServiceClient(RevisionService revisionService) {
        this.revisionService = revisionService;
    }

    @Override
    public void record(RevisionRecord revision, Long userId) {
        // actorId·decidedAt은 D가 소유자와 서버 시각(KST)으로 채운다
        revisionService.create(new RevisionCreateRequest(
                revision.screen(),
                revision.targetType(),
                revision.targetId(),
                revision.fieldName(),
                revision.proposedValue(),
                revision.finalValue(),
                revision.action(),
                revision.proposedBy(),
                revision.modelVersion()
        ), userId);
    }
}
