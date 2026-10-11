package com.salesmap.backend.demo.service;

import com.salesmap.backend.autopipeline.service.AutoPipelineService;
import com.salesmap.backend.demo.dto.DemoStatusResponse;
import com.salesmap.backend.demo.entity.DemoWorkspace;
import com.salesmap.backend.demo.repository.DemoWorkspaceRepository;
import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import com.salesmap.backend.integration.entity.IntegrationStatus;
import com.salesmap.backend.integration.repository.IntegrationRepository;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 개발자용 데모 모드 on/off.
 * <p>
 * 켜면 소유자 전용 "데모 사용자"(가짜 Gmail 연결 포함)로 요청이 전환되어, 실제 계정 데이터와 섞이지 않는다.
 * 메일 데이터만 가짜이고 분석·파이프라인·일정·답장 초안은 실제 로직과 AI 모듈을 그대로 탄다.
 */
@Service
public class DemoWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(DemoWorkspaceService.class);

    // 삭제 순서가 FK 의존 순서다(자식 → 부모).
    private static final List<String> WIPE_STATEMENTS = List.of(
            "delete from CustomerActivity a where a.user.id = :userId",
            "delete from CustomerContact c where c.user.id = :userId",
            "delete from SalesmapRecord r where r.analysis.id in (select a.id from Analysis a where a.source.user.id = :userId)",
            "delete from ReplyDraft d where d.analysis.id in (select a.id from Analysis a where a.source.user.id = :userId)",
            "delete from AnalysisRevision v where v.analysis.id in (select a.id from Analysis a where a.source.user.id = :userId)",
            "delete from Schedule s where s.user.id = :userId",
            "delete from Analysis a where a.source.id in (select s.id from Source s where s.user.id = :userId)",
            "delete from Source s where s.user.id = :userId",
            "delete from SourceGroup g where g.user.id = :userId"
    );

    private final DemoWorkspaceRepository demoWorkspaceRepository;
    private final UserRepository userRepository;
    private final IntegrationRepository integrationRepository;
    private final ScheduleRepository scheduleRepository;
    private final AutoPipelineService autoPipelineService;
    private final EntityManager entityManager;

    public DemoWorkspaceService(
            DemoWorkspaceRepository demoWorkspaceRepository,
            UserRepository userRepository,
            IntegrationRepository integrationRepository,
            ScheduleRepository scheduleRepository,
            AutoPipelineService autoPipelineService,
            EntityManager entityManager
    ) {
        this.demoWorkspaceRepository = demoWorkspaceRepository;
        this.userRepository = userRepository;
        this.integrationRepository = integrationRepository;
        this.scheduleRepository = scheduleRepository;
        this.autoPipelineService = autoPipelineService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public DemoStatusResponse getStatus(Long ownerUserId) {
        return demoWorkspaceRepository.findByOwnerUserId(ownerUserId)
                .map(this::toStatus)
                .orElseGet(() -> new DemoStatusResponse(false, null, null, 0, DemoScenario.LAST_STAGE, 0, countMails(null, 0, true)));
    }

    @Transactional
    public DemoStatusResponse enable(Long ownerUserId) {
        DemoWorkspace workspace = demoWorkspaceRepository.findByOwnerUserId(ownerUserId)
                .orElseGet(() -> createWorkspace(ownerUserId));
        workspace.enable();
        log.info("[DEMO] enabled. ownerUserId={}, demoUserId={}", ownerUserId, workspace.getDemoUserId());
        return toStatus(workspace);
    }

    @Transactional
    public DemoStatusResponse disable(Long ownerUserId) {
        DemoWorkspace workspace = demoWorkspaceRepository.findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new NoSuchElementException("데모 작업공간이 없습니다."));
        workspace.disable();
        // 숨겨진 데모 계정이 백그라운드에서 로컬 LLM을 계속 돌리지 않도록 자동 모드도 끈다.
        autoPipelineService.disable(workspace.getDemoUserId());
        log.info("[DEMO] disabled. ownerUserId={}, demoUserId={}", ownerUserId, workspace.getDemoUserId());
        return toStatus(workspace);
    }

    /** 데모 데이터를 모두 지우고 오늘 기준으로 시나리오를 처음부터 다시 시작한다. */
    @Transactional
    public DemoStatusResponse reset(Long ownerUserId) {
        DemoWorkspace workspace = demoWorkspaceRepository.findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new NoSuchElementException("데모 작업공간이 없습니다."));
        Long demoUserId = workspace.getDemoUserId();

        entityManager.flush();
        WIPE_STATEMENTS.forEach(statement -> entityManager.createQuery(statement)
                .setParameter("userId", demoUserId)
                .executeUpdate());
        entityManager.clear();

        DemoWorkspace reloaded = demoWorkspaceRepository.findById(workspace.getId()).orElseThrow();
        reloaded.restart(LocalDate.now());
        User demoUser = userRepository.findById(demoUserId).orElseThrow();
        integrationRepository.findByUserIdAndProvider(demoUserId, IntegrationProvider.GMAIL)
                .ifPresent(integration -> integration.updateLastSyncedAt(null));
        seedSchedules(demoUser, reloaded.getAnchorDate());
        log.info("[DEMO] reset. ownerUserId={}, demoUserId={}, anchorDate={}", ownerUserId, demoUserId, reloaded.getAnchorDate());
        return toStatus(reloaded);
    }

    private DemoWorkspace createWorkspace(Long ownerUserId) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        String demoEmail = "demo+" + ownerUserId + "@salesbreeze.demo";

        // 비밀번호 자리에 BCrypt 형식이 아닌 값을 넣어 이 계정으로는 로그인할 수 없게 한다.
        User demoUser = userRepository.save(new User(
                demoEmail,
                owner.getName(),
                "demo-" + UUID.randomUUID(),
                owner.getRole(),
                UserStatus.ACTIVE
        ));
        integrationRepository.save(new Integration(
                demoUser,
                IntegrationProvider.GMAIL,
                demoEmail,
                "demo-access-token",
                "demo-refresh-token",
                LocalDateTime.now().plusYears(50),
                IntegrationStatus.CONNECTED
        ));

        LocalDate anchorDate = LocalDate.now();
        seedSchedules(demoUser, anchorDate);
        return demoWorkspaceRepository.save(new DemoWorkspace(ownerUserId, demoUser.getId(), anchorDate));
    }

    private void seedSchedules(User demoUser, LocalDate anchorDate) {
        DemoScenario.schedules(anchorDate).forEach(seed -> scheduleRepository.save(new Schedule(
                demoUser,
                null,
                seed.title(),
                seed.dateTime(),
                seed.memo(),
                seed.dateTime().minusMinutes(30),
                ScheduleStatus.SCHEDULED
        )));
    }

    private DemoStatusResponse toStatus(DemoWorkspace workspace) {
        String demoEmail = userRepository.findById(workspace.getDemoUserId()).map(User::getEmail).orElse(null);
        return new DemoStatusResponse(
                workspace.isEnabled(),
                demoEmail,
                workspace.getAnchorDate(),
                workspace.getDeliveredStage(),
                DemoScenario.LAST_STAGE,
                countMails(workspace.getAnchorDate(), workspace.getDeliveredStage(), false),
                countMails(workspace.getAnchorDate(), workspace.getDeliveredStage(), true)
        );
    }

    private int countMails(LocalDate anchorDate, int deliveredStage, boolean pending) {
        LocalDate anchor = anchorDate == null ? LocalDate.now() : anchorDate;
        return (int) DemoScenario.mails(anchor, "", "", LocalDateTime.now()).stream()
                .filter(mail -> pending ? mail.stage() > deliveredStage : mail.stage() <= deliveredStage)
                .count();
    }
}
