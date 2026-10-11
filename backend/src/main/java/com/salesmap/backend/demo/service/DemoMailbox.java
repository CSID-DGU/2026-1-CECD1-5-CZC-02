package com.salesmap.backend.demo.service;

import com.salesmap.backend.demo.entity.DemoWorkspace;
import com.salesmap.backend.demo.repository.DemoWorkspaceRepository;
import com.salesmap.backend.gmail.dto.GmailCollectResponse;
import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import com.salesmap.backend.integration.repository.IntegrationRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceGroup;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceGroupRepository;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 데모 사용자의 가짜 Gmail. "Gmail 새로고침"을 누를 때마다 시나리오의 다음 단계 메일을 받은편지함에 넣고,
 * 답장 전송은 실제로 보내지 않고 보낸 메일로만 기록한다.
 */
@Service
public class DemoMailbox {

    private static final Logger log = LoggerFactory.getLogger(DemoMailbox.class);
    private static final String THREAD_PREFIX = "demo-thread-";

    private final DemoWorkspaceRepository demoWorkspaceRepository;
    private final IntegrationRepository integrationRepository;
    private final SourceRepository sourceRepository;
    private final SourceGroupRepository sourceGroupRepository;

    public DemoMailbox(
            DemoWorkspaceRepository demoWorkspaceRepository,
            IntegrationRepository integrationRepository,
            SourceRepository sourceRepository,
            SourceGroupRepository sourceGroupRepository
    ) {
        this.demoWorkspaceRepository = demoWorkspaceRepository;
        this.integrationRepository = integrationRepository;
        this.sourceRepository = sourceRepository;
        this.sourceGroupRepository = sourceGroupRepository;
    }

    public boolean isDemoUser(Long userId) {
        return demoWorkspaceRepository.existsByDemoUserId(userId);
    }

    @Transactional
    public GmailCollectResponse deliverNextStage(Long demoUserId, String syncMode) {
        DemoWorkspace workspace = demoWorkspaceRepository.findByDemoUserId(demoUserId)
                .orElseThrow(() -> new NoSuchElementException("데모 작업공간을 찾을 수 없습니다."));
        Integration integration = demoIntegration(demoUserId);
        User user = integration.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime previousSyncedAt = integration.getLastSyncedAt();

        int nextStage = workspace.getDeliveredStage() + 1;
        List<DemoScenario.DemoMail> arriving = !workspace.isEnabled() || nextStage > DemoScenario.LAST_STAGE
                ? List.of()
                : DemoScenario.mails(workspace.getAnchorDate(), user.getName(), user.getEmail(), now).stream()
                        .filter(mail -> mail.stage() == nextStage)
                        .toList();

        List<Long> savedSourceIds = new ArrayList<>();
        List<Long> savedGroupIds = new ArrayList<>();
        LocalDateTime latest = null;
        int index = 0;
        for (DemoScenario.DemoMail mail : arriving) {
            SourceGroup group = findOrCreateThread(user, integration, mail.threadKey(), mail.subject());
            Source source = sourceRepository.save(new Source(
                    user,
                    integration,
                    group,
                    SourceType.EMAIL,
                    "demo-" + nextStage + "-" + (index++) + "-" + mail.threadKey(),
                    mail.subject(),
                    mail.body(),
                    SourceStatus.COLLECTED,
                    now,
                    mail.direction(),
                    mail.fromName(),
                    mail.fromEmail(),
                    mail.toName(),
                    mail.toEmail(),
                    mail.sentAt()
            ));
            savedSourceIds.add(source.getId());
            if (!savedGroupIds.contains(group.getId())) {
                savedGroupIds.add(group.getId());
            }
            if (latest == null || mail.sentAt().isAfter(latest)) {
                latest = mail.sentAt();
            }
        }

        if (!arriving.isEmpty()) {
            workspace.markStageDelivered(nextStage);
            integration.updateLastSyncedAt(now);
        }
        log.info("[DEMO] delivered stage={} mails={} demoUserId={}", nextStage, arriving.size(), demoUserId);

        return new GmailCollectResponse(
                integration.getId(),
                previousSyncedAt,
                arriving.isEmpty() ? previousSyncedAt : now,
                now,
                latest,
                user.getEmail(),
                integration.getExternalAccountId(),
                "demo",
                "demo",
                "demo-stage-" + nextStage,
                List.of("demo-stage-" + nextStage),
                List.of(),
                List.of(),
                arriving.size(),
                arriving.size(),
                !arriving.isEmpty(),
                syncMode,
                arriving.size(),
                arriving.size(),
                arriving.size(),
                0,
                0,
                savedGroupIds,
                savedSourceIds,
                List.of(),
                List.of()
        );
    }

    /** 데모에서 보낸 답장은 외부로 나가지 않고, 같은 대화에 "보낸 메일"로 쌓여 미답장·타임라인 흐름을 이어준다. */
    @Transactional
    public String recordSentReply(Long demoUserId, String toEmail, String subject, String body, String threadId) {
        Integration integration = demoIntegration(demoUserId);
        User user = integration.getUser();
        SourceGroup group = threadId == null || threadId.isBlank()
                ? null
                : sourceGroupRepository.findByIntegrationIdAndSourceTypeAndExternalGroupId(
                        integration.getId(), SourceType.EMAIL, threadId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        String messageId = "demo-sent-" + UUID.randomUUID();

        sourceRepository.save(new Source(
                user,
                integration,
                group,
                SourceType.EMAIL,
                messageId,
                subject,
                body == null || body.isBlank() ? "(빈 답장)" : body,
                SourceStatus.COLLECTED,
                now,
                SourceDirection.SENT,
                user.getName(),
                user.getEmail(),
                null,
                toEmail,
                now
        ));
        log.info("[DEMO] recorded reply instead of sending. demoUserId={}, to={}, threadId={}", demoUserId, toEmail, threadId);
        return messageId;
    }

    private Integration demoIntegration(Long demoUserId) {
        return integrationRepository.findByUserIdAndProvider(demoUserId, IntegrationProvider.GMAIL)
                .orElseThrow(() -> new NoSuchElementException("데모 메일 계정을 찾을 수 없습니다."));
    }

    private SourceGroup findOrCreateThread(User user, Integration integration, String threadKey, String subject) {
        String externalGroupId = THREAD_PREFIX + threadKey;
        return sourceGroupRepository.findByIntegrationIdAndSourceTypeAndExternalGroupId(
                        integration.getId(), SourceType.EMAIL, externalGroupId)
                .orElseGet(() -> sourceGroupRepository.save(new SourceGroup(
                        user, integration, SourceType.EMAIL, externalGroupId, subject, true)));
    }
}
