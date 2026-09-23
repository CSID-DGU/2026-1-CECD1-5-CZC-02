package com.salesmap.backend.analysis.service;

import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.ai.dto.AiGroupAnalysisRequest;
import com.salesmap.backend.ai.dto.AiReplyDraftRequest;
import com.salesmap.backend.ai.dto.AiReplyDraftResponse;
import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisGroupCreateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import com.salesmap.backend.analysis.dto.AnalysisRevisionResponse;
import com.salesmap.backend.analysis.dto.AnalysisUpdateRequest;
import com.salesmap.backend.analysis.dto.ReplySendRequest;
import com.salesmap.backend.analysis.dto.ReplySendResponse;
import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisRevision;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.entity.ReplyDraft;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.analysis.repository.AnalysisRevisionRepository;
import com.salesmap.backend.analysis.repository.ReplyDraftRepository;
import com.salesmap.backend.customer.service.CustomerTimelineService;
import com.salesmap.backend.gmail.service.GmailIntegrationService;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceGroup;
import com.salesmap.backend.source.repository.SourceGroupRepository;
import com.salesmap.backend.source.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final Pattern ISO_LOCAL_DATE_TIME_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?");
    private static final Set<String> BUSINESS_TYPES = Set.of("SALES_ACTIVITY", "NON_BUSINESS", "UNKNOWN");

    private final AnalysisRepository analysisRepository;
    private final AnalysisRevisionRepository analysisRevisionRepository;
    private final ReplyDraftRepository replyDraftRepository;
    private final SourceRepository sourceRepository;
    private final SourceGroupRepository sourceGroupRepository;
    private final ScheduleRepository scheduleRepository;
    private final AiClient aiClient;
    private final CustomerTimelineService customerTimelineService;
    private final GmailIntegrationService gmailIntegrationService;
    private final ObjectMapper objectMapper;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            AnalysisRevisionRepository analysisRevisionRepository,
            ReplyDraftRepository replyDraftRepository,
            SourceRepository sourceRepository,
            SourceGroupRepository sourceGroupRepository,
            ScheduleRepository scheduleRepository,
            AiClient aiClient,
            CustomerTimelineService customerTimelineService,
            GmailIntegrationService gmailIntegrationService,
            ObjectMapper objectMapper
    ) {
        this.analysisRepository = analysisRepository;
        this.analysisRevisionRepository = analysisRevisionRepository;
        this.replyDraftRepository = replyDraftRepository;
        this.sourceRepository = sourceRepository;
        this.sourceGroupRepository = sourceGroupRepository;
        this.scheduleRepository = scheduleRepository;
        this.aiClient = aiClient;
        this.customerTimelineService = customerTimelineService;
        this.gmailIntegrationService = gmailIntegrationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnalysisResponse createAnalysis(AnalysisCreateRequest request, Long authenticatedUserId) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new NoSuchElementException("Source not found."));
        validateSourceOwnership(source, authenticatedUserId);
        // 분석(정보 추출)은 받은 메일만. 내가 보낸 답장은 분석하지 않는다.
        if (source.getDirection() == SourceDirection.SENT) {
            throw new IllegalArgumentException("보낸 메일은 AI 분석 대상이 아닙니다.");
        }

        AiAnalysisResponse aiResult = aiClient.analyze(toAiAnalysisRequest(source, request.analysisMode()));
        Analysis analysis = createAnalysisEntity(source, aiResult);
        source.markAnalyzed();
        Analysis savedAnalysis = analysisRepository.save(analysis);
        customerTimelineService.recordAnalysis(savedAnalysis);

        return AnalysisResponse.from(savedAnalysis);
    }

    @Transactional
    public AnalysisResponse createGroupAnalysis(AnalysisGroupCreateRequest request, Long authenticatedUserId) {
        SourceGroup sourceGroup = sourceGroupRepository.findById(request.sourceGroupId())
                .orElseThrow(() -> new NoSuchElementException("Source group not found."));
        validateSourceGroupOwnership(sourceGroup, authenticatedUserId);

        List<Source> sources = sourceRepository.findBySourceGroupIdOrderBySentAtAscIdAsc(sourceGroup.getId());
        if (sources.isEmpty()) {
            throw new NoSuchElementException("Source group has no messages.");
        }

        AiAnalysisResponse aiResult = aiClient.analyzeGroup(toAiGroupAnalysisRequest(sourceGroup, sources, request.analysisMode()));
        Analysis analysis = createAnalysisEntity(sources.get(0), aiResult);
        sources.forEach(Source::markAnalyzed);
        Analysis savedAnalysis = analysisRepository.save(analysis);
        customerTimelineService.recordAnalysis(savedAnalysis);

        return AnalysisResponse.from(savedAnalysis);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long analysisId, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        validateAnalysisOwnership(analysis, authenticatedUserId);

        return AnalysisResponse.from(analysis);
    }

    @Transactional
    public AiReplyDraftResponse generateReplyDraft(Long analysisId, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        validateAnalysisOwnership(analysis, authenticatedUserId);

        Source source = analysis.getSource();
        AiReplyDraftResponse draft = aiClient.generateReplyDraft(new AiReplyDraftRequest(
                source.getTitle(),
                source.getContent(),
                source.getSenderEmail(),
                analysis.getCustomerName(),
                analysis.getContactName(),
                analysis.getProductName(),
                analysis.getAttendees(),
                normalizeActionType(toAiAnalysisResponse(analysis)),
                analysis.getScheduleText(),
                analysis.getSummary(),
                analysis.getFollowUpAction()
        ));

        // AI 초안을 보관해 두고, 실제 전송 시 보낸 본문과 짝지어 비교할 수 있게 한다.
        replyDraftRepository.save(new ReplyDraft(analysis, draft.subject(), draft.body(), draft.generatedBy()));
        return draft;
    }

    @Transactional
    public ReplySendResponse sendReply(Long analysisId, ReplySendRequest request, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        validateAnalysisOwnership(analysis, authenticatedUserId);

        Source source = analysis.getSource();
        String recipient = source.getSenderEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("원본 메일에 발신자 주소가 없어 답장을 보낼 수 없습니다.");
        }

        String subject = normalizeReplySubject(request.subject(), source.getTitle());
        String body = request.body() == null ? "" : request.body();
        String threadId = source.getSourceGroup() == null ? null : source.getSourceGroup().getExternalGroupId();

        String messageId = gmailIntegrationService.sendReplyEmail(authenticatedUserId, recipient, subject, body, threadId);

        // 가장 최근의 미전송 AI 초안에 실제 보낸 내용을 기록한다. 초안 없이 직접 쓴 답장이면 발송본만 남긴다.
        ReplyDraft replyDraft = replyDraftRepository.findFirstByAnalysisIdAndSentAtIsNullOrderByIdDesc(analysisId)
                .orElseGet(() -> replyDraftRepository.save(new ReplyDraft(analysis, null, null, "manual")));
        replyDraft.markSent(subject, body);

        // 보낸 답장을 고객 타임라인에 소통 이벤트로 남긴다(분석 대상은 아니지만 대화 흐름엔 포함).
        customerTimelineService.recordReplySent(analysis, subject, body);

        return new ReplySendResponse(recipient, subject, messageId);
    }

    private String normalizeReplySubject(String subject, String originalTitle) {
        String base = subject != null && !subject.isBlank() ? subject.trim()
                : (originalTitle != null && !originalTitle.isBlank() ? originalTitle.trim() : "회신 드립니다");
        if (base.regionMatches(true, 0, "re:", 0, 3)) {
            return base;
        }
        return "Re: " + base;
    }

    @Transactional
    public AnalysisResponse updateAnalysis(Long analysisId, AnalysisUpdateRequest request, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        validateAnalysisOwnership(analysis, authenticatedUserId);
        String businessType = request.businessType();
        if (businessType != null && !businessType.isBlank() && !BUSINESS_TYPES.contains(businessType)) {
            throw new IllegalArgumentException("businessType은 SALES_ACTIVITY, NON_BUSINESS, UNKNOWN 중 하나여야 합니다.");
        }

        Map<String, String> before = AnalysisEditableFields.valuesOf(analysis);
        analysis.updateEditableFields(
                truncate(request.customerName(), 255),
                truncate(request.contactName(), 100),
                truncate(request.productName(), 255),
                request.amount(),
                truncate(request.attendees(), 2000),
                truncate(request.actionType(), 50),
                truncate(request.targetScheduleTitle(), 2000),
                truncate(request.actionReason(), 2000),
                truncate(request.scheduleText(), 2000),
                truncate(request.followUpAction(), 2000),
                truncate(request.summary(), 4000),
                businessType
        );
        recordRevisions(analysis, before, AnalysisEditableFields.valuesOf(analysis));
        customerTimelineService.recordAnalysis(analysis);

        return AnalysisResponse.from(analysis);
    }

    /** 승인 처리. 한 번도 수정하지 않았으면 AI 결과가 그대로 정답이었다는 기록을 남긴다. */
    @Transactional
    public void approve(Analysis analysis) {
        analysis.markApproved(!analysisRevisionRepository.existsByAnalysisId(analysis.getId()));
    }

    @Transactional
    public AnalysisResponse rejectAnalysis(Long analysisId, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        validateAnalysisOwnership(analysis, authenticatedUserId);
        if (analysis.getStatus() == AnalysisStatus.APPROVED) {
            throw new IllegalArgumentException("이미 등록(승인)된 분석은 거절할 수 없습니다.");
        }

        analysis.markRejected();
        return AnalysisResponse.from(analysis);
    }

    @Transactional(readOnly = true)
    public List<AnalysisRevisionResponse> getRevisions(Long analysisId, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        validateAnalysisOwnership(analysis, authenticatedUserId);

        return analysisRevisionRepository.findByAnalysisIdOrderByIdAsc(analysisId).stream()
                .map(AnalysisRevisionResponse::from)
                .toList();
    }

    private void recordRevisions(Analysis analysis, Map<String, String> before, Map<String, String> after) {
        Map<String, String> aiValues = readAiSnapshot(analysis);
        after.forEach((field, userValue) -> {
            String previousValue = before.get(field);
            if (Objects.equals(previousValue, userValue)) {
                return;
            }
            analysisRevisionRepository.save(new AnalysisRevision(
                    analysis,
                    field,
                    aiValues.get(field),
                    previousValue,
                    userValue
            ));
        });
    }

    private Map<String, String> readAiSnapshot(Analysis analysis) {
        if (analysis.getAiSnapshot() == null || analysis.getAiSnapshot().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(analysis.getAiSnapshot(), new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException exception) {
            log.warn("Failed to read AI snapshot. analysisId={}", analysis.getId());
            return Map.of();
        }
    }

    private String writeAiSnapshot(Analysis analysis) {
        try {
            return objectMapper.writeValueAsString(AnalysisEditableFields.valuesOf(analysis));
        } catch (JsonProcessingException exception) {
            log.warn("Failed to write AI snapshot. sourceId={}", analysis.getSource().getId());
            return null;
        }
    }

    @Transactional
    public Schedule applyApprovedScheduleAction(Analysis analysis, Long authenticatedUserId) {
        validateAnalysisOwnership(analysis, authenticatedUserId);
        return applyScheduleAction(analysis, toAiAnalysisResponse(analysis), authenticatedUserId);
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> getAnalysesBySource(Long sourceId, Long authenticatedUserId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("Source not found."));
        validateSourceOwnership(source, authenticatedUserId);

        if (source.getSourceGroup() != null) {
            List<Long> groupSourceIds = sourceRepository.findBySourceGroupIdOrderBySentAtAscIdAsc(source.getSourceGroup().getId())
                    .stream()
                    .map(Source::getId)
                    .toList();

            return analysisRepository.findBySourceIdIn(groupSourceIds).stream()
                    .map(AnalysisResponse::from)
                    .toList();
        }

        return analysisRepository.findBySourceId(sourceId).stream()
                .map(AnalysisResponse::from)
                .toList();
    }

    private Analysis createAnalysisEntity(Source source, AiAnalysisResponse aiResult) {
        Analysis analysis = new Analysis(
                source,
                truncate(aiResult.customerName(), 255),
                truncate(aiResult.contactName(), 100),
                truncate(aiResult.productName(), 255),
                aiResult.amount(),
                truncate(aiResult.attendees(), 2000),
                truncate(normalizeActionType(aiResult), 50),
                aiResult.targetScheduleId(),
                truncate(aiResult.targetScheduleTitle(), 2000),
                truncate(aiResult.actionReason(), 2000),
                truncate(aiResult.businessType(), 50),
                aiResult.businessRelevanceScore(),
                truncate(aiResult.businessReason(), 2000),
                truncate(buildScheduleText(aiResult), 2000),
                truncate(aiResult.todoContent(), 2000),
                truncate(aiResult.summary(), 4000),
                AnalysisStatus.ANALYZED,
                LocalDateTime.now(),
                null
        );
        analysis.recordAiOrigin(
                writeAiSnapshot(analysis),
                truncate(aiResult.llmProvider(), 50),
                truncate(aiResult.llmModel(), 100),
                truncate(aiResult.llmFallbackReason(), 1000)
        );
        return analysis;
    }

    private String normalizeActionType(AiAnalysisResponse aiResult) {
        if (aiResult == null) {
            return "UNKNOWN";
        }
        if ("NON_BUSINESS".equals(aiResult.businessType())) {
            return "UNKNOWN";
        }
        return aiResult.actionType() == null || aiResult.actionType().isBlank()
                ? "UNKNOWN"
                : aiResult.actionType();
    }

    private AiAnalysisRequest toAiAnalysisRequest(Source source, String analysisMode) {
        AiAnalysisRequest.RequesterInfo requester = toRequesterInfo(source);
        AiAnalysisRequest.SourceGroupInfo sourceGroup = new AiAnalysisRequest.SourceGroupInfo(
                "source-" + source.getId(),
                source.getSourceType(),
                source.getTitle(),
                false
        );
        AiAnalysisRequest.MessageItem message = toMessageItem(source);

        return new AiAnalysisRequest(
                source.getId(),
                source.getSourceType(),
                source.getExternalSourceId(),
                source.getTitle(),
                source.getContent(),
                source.getCollectedAt(),
                requester,
                sourceGroup,
                List.of(message),
                getExistingSchedules(source.getUser().getId()),
                getRecentSenderAnalyses(source),
                normalizeAnalysisMode(analysisMode)
        );
    }

    private AiGroupAnalysisRequest toAiGroupAnalysisRequest(SourceGroup sourceGroup, List<Source> sources, String analysisMode) {
        Source firstSource = sources.get(0);

        return new AiGroupAnalysisRequest(
                toRequesterInfo(firstSource),
                new AiAnalysisRequest.SourceGroupInfo(
                        sourceGroup.getExternalGroupId(),
                        sourceGroup.getSourceType(),
                        sourceGroup.getTitle(),
                        sourceGroup.isDeduplicated()
                ),
                sources.stream()
                        .map(this::toMessageItem)
                        .toList(),
                getExistingSchedules(sourceGroup.getUser().getId()),
                getRecentSenderAnalyses(firstSource),
                normalizeAnalysisMode(analysisMode)
        );
    }

    private List<AiAnalysisRequest.HistoricalAnalysisInfo> getRecentSenderAnalyses(Source source) {
        if (source.getSenderEmail() == null || source.getSenderEmail().isBlank()) {
            return List.of();
        }

        return analysisRepository.findRecentSenderAnalyses(
                        source.getUser().getId(),
                        source.getSenderEmail(),
                        source.getId(),
                        PageRequest.of(0, 5)
                )
                .stream()
                .map(this::toHistoricalAnalysisInfo)
                .toList();
    }

    private AiAnalysisRequest.HistoricalAnalysisInfo toHistoricalAnalysisInfo(Analysis analysis) {
        return new AiAnalysisRequest.HistoricalAnalysisInfo(
                analysis.getId(),
                analysis.getSource().getId(),
                analysis.getSource().getTitle(),
                analysis.getCustomerName(),
                analysis.getProductName(),
                analysis.getAttendees(),
                analysis.getAmount(),
                analysis.getActionType(),
                analysis.getScheduleText(),
                analysis.getSummary()
        );
    }

    private String normalizeAnalysisMode(String analysisMode) {
        if (analysisMode == null || analysisMode.isBlank()) {
            return null;
        }

        String normalized = analysisMode.trim().toLowerCase();
        if ("rule".equals(normalized) || "ollama".equals(normalized)) {
            return normalized;
        }

        return null;
    }

    private AiAnalysisRequest.RequesterInfo toRequesterInfo(Source source) {
        return new AiAnalysisRequest.RequesterInfo(
                source.getUser().getId(),
                source.getUser().getName(),
                source.getUser().getEmail()
        );
    }

    private AiAnalysisRequest.MessageItem toMessageItem(Source source) {
        return new AiAnalysisRequest.MessageItem(
                source.getId(),
                source.getExternalSourceId(),
                source.getDirection() == null ? "UNKNOWN" : source.getDirection().name(),
                source.getSenderName(),
                source.getSenderEmail(),
                splitList(source.getReceiverNames()),
                splitList(source.getReceiverEmails()),
                source.getSentAt() == null ? source.getCollectedAt() : source.getSentAt(),
                source.getContent()
        );
    }

    private List<AiAnalysisRequest.ExistingScheduleInfo> getExistingSchedules(Long userId) {
        return scheduleRepository.findByUserId(userId).stream()
                .map(this::toExistingScheduleInfo)
                .toList();
    }

    private AiAnalysisRequest.ExistingScheduleInfo toExistingScheduleInfo(Schedule schedule) {
        return new AiAnalysisRequest.ExistingScheduleInfo(
                schedule.getId(),
                schedule.getTitle(),
                schedule.getScheduleDateTime(),
                Collections.emptyList()
        );
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String buildScheduleText(AiAnalysisResponse aiResult) {
        if (aiResult.scheduleTitle() == null) {
            return null;
        }

        if (aiResult.scheduleDateTime() == null) {
            return aiResult.scheduleTitle();
        }

        return aiResult.scheduleTitle() + " (" + aiResult.scheduleDateTime() + ")";
    }

    private AiAnalysisResponse toAiAnalysisResponse(Analysis analysis) {
        LocalDateTime scheduleDateTime = extractScheduleDateTime(analysis.getScheduleText(), null);
        String scheduleTitle = firstNotBlankOrNull(
                analysis.getTargetScheduleTitle(),
                extractTitleFromScheduleText(analysis.getScheduleText())
        );

        return new AiAnalysisResponse(
                analysis.getCustomerName(),
                analysis.getContactName(),
                analysis.getProductName(),
                analysis.getAmount(),
                analysis.getAttendees(),
                analysis.getActionType(),
                analysis.getTargetScheduleId(),
                analysis.getTargetScheduleTitle(),
                analysis.getActionReason(),
                analysis.getBusinessType(),
                analysis.getBusinessRelevanceScore(),
                analysis.getBusinessReason(),
                scheduleTitle,
                scheduleDateTime,
                analysis.getFollowUpAction(),
                null,
                analysis.getSummary(),
                null,
                null,
                null,
                null
        );
    }

    private Schedule applyScheduleAction(Analysis analysis, AiAnalysisResponse aiResult, Long authenticatedUserId) {
        log.info(
                "[AI_ANALYSIS] actionType={}, analysisId={}, sourceId={}, targetScheduleId={}, targetScheduleTitle={}, scheduleTitle={}, scheduleDateTime={}",
                aiResult.actionType(),
                analysis.getId(),
                analysis.getSource().getId(),
                aiResult.targetScheduleId(),
                aiResult.targetScheduleTitle(),
                aiResult.scheduleTitle(),
                aiResult.scheduleDateTime()
        );

        if (aiResult.actionType() == null) {
            return null;
        }

        if ("CREATE".equals(aiResult.actionType())) {
            Schedule existingSchedule = analysis.getTargetScheduleId() == null
                    ? null
                    : scheduleRepository.findById(analysis.getTargetScheduleId()).orElse(null);
            if (existingSchedule != null) {
                log.info(
                        "[SCHEDULE_CREATE] skip duplicate creation. analysisId={}, scheduleId={}",
                        analysis.getId(),
                        analysis.getTargetScheduleId()
                );
                return existingSchedule;
            }
            return createScheduleFromAnalysis(analysis, aiResult);
        }

        Schedule schedule = findTargetSchedule(analysis, aiResult, authenticatedUserId);
        if (schedule == null) {
            log.info(
                    "[SCHEDULE_ACTION] target schedule not found. actionType={}, analysisId={}, targetScheduleId={}, targetScheduleTitle={}, scheduleTitle={}",
                    aiResult.actionType(),
                    analysis.getId(),
                    aiResult.targetScheduleId(),
                    aiResult.targetScheduleTitle(),
                    aiResult.scheduleTitle()
            );
            return null;
        }

        if ("CANCEL".equals(aiResult.actionType())) {
            schedule.cancel();
            analysis.linkTargetSchedule(schedule.getId(), schedule.getTitle(), "Canceled");
            log.info(
                    "[SCHEDULE_CANCEL] analysisId={}, scheduleId={}, status={}",
                    analysis.getId(),
                    schedule.getId(),
                    schedule.getStatus()
            );
            return schedule;
        }

        if ("UPDATE".equals(aiResult.actionType())) {
            String title = aiResult.scheduleTitle() == null ? schedule.getTitle() : aiResult.scheduleTitle();
            LocalDateTime scheduleDateTime = aiResult.scheduleDateTime() == null
                    ? schedule.getScheduleDateTime()
                    : aiResult.scheduleDateTime();
            String memo = aiResult.summary() == null ? schedule.getMemo() : truncate(aiResult.summary(), 200);
            schedule.update(title, scheduleDateTime, memo);
            analysis.linkTargetSchedule(schedule.getId(), schedule.getTitle(), "Updated");
            log.info(
                    "[SCHEDULE_UPDATE] analysisId={}, scheduleId={}, title={}, scheduleDateTime={}",
                    analysis.getId(),
                    schedule.getId(),
                    schedule.getTitle(),
                    schedule.getScheduleDateTime()
            );
            return schedule;
        }

        return null;
    }

    private Schedule findTargetSchedule(Analysis analysis, AiAnalysisResponse aiResult, Long authenticatedUserId) {
        if (aiResult.targetScheduleId() != null) {
            Schedule schedule = scheduleRepository.findById(aiResult.targetScheduleId())
                    .orElse(null);
            if (schedule != null) {
                validateScheduleOwnership(schedule, authenticatedUserId);
            }
            return schedule;
        }

        List<Schedule> activeSchedules = scheduleRepository.findByUserId(authenticatedUserId).stream()
                .filter(schedule -> schedule.getStatus() != ScheduleStatus.CANCELED)
                .sorted(Comparator.comparing(Schedule::getId).reversed())
                .toList();

        // 1) 같은 고객(발신 이메일 또는 고객사명)이 만든 일정을 우선 매칭한다.
        //    제목 문구는 CREATE와 UPDATE/CANCEL 사이에 미묘하게 달라질 수 있어 발신자 기준이 훨씬 안정적이다.
        String senderEmail = analysis.getSource() == null ? null : analysis.getSource().getSenderEmail();
        String customerName = analysis.getCustomerName();
        for (Schedule schedule : activeSchedules) {
            Analysis origin = schedule.getAnalysis();
            if (origin == null) {
                continue;
            }
            String originSender = origin.getSource() == null ? null : origin.getSource().getSenderEmail();
            boolean sameSender = senderEmail != null && senderEmail.equalsIgnoreCase(originSender);
            boolean sameCustomer = customerName != null && !customerName.isBlank()
                    && customerName.equalsIgnoreCase(origin.getCustomerName());
            if (sameSender || sameCustomer) {
                return schedule;
            }
        }

        // 2) 발신자 매칭 실패 시 제목 부분 일치로 폴백한다.
        String titleHint = firstNotBlankOrNull(aiResult.targetScheduleTitle(), aiResult.scheduleTitle());
        if (titleHint == null || titleHint.isBlank()) {
            return null;
        }

        String normalizedHint = titleHint.toLowerCase();
        return activeSchedules.stream()
                .filter(schedule -> {
                    String title = schedule.getTitle() == null ? "" : schedule.getTitle().toLowerCase();
                    return title.contains(normalizedHint) || normalizedHint.contains(title);
                })
                .findFirst()
                .orElse(null);
    }

    private Schedule createScheduleFromAnalysis(Analysis analysis, AiAnalysisResponse aiResult) {
        if (aiResult.scheduleDateTime() == null) {
            log.info(
                    "Skip CREATE schedule because scheduleDateTime is null. analysisId={}, scheduleTitle={}, summary={}",
                    analysis.getId(),
                    aiResult.scheduleTitle(),
                    aiResult.summary()
            );
            return null;
        }

        Schedule schedule = new Schedule(
                analysis.getSource().getUser(),
                analysis,
                buildScheduleTitle(aiResult),
                aiResult.scheduleDateTime(),
                truncate(buildScheduleMemo(aiResult), 200),
                null,
                ScheduleStatus.SCHEDULED
        );

        log.info(
                "[SCHEDULE_CREATE] sourceId={}, analysisId={}, title={}, scheduleDateTime={}",
                analysis.getSource().getId(),
                analysis.getId(),
                schedule.getTitle(),
                schedule.getScheduleDateTime()
        );
        Schedule savedSchedule = scheduleRepository.save(schedule);
        analysis.linkCreatedSchedule(savedSchedule.getId(), savedSchedule.getTitle());

        log.info(
                "[SCHEDULE_CREATE] saved scheduleId={}, analysisId={}, scheduleDateTime={}",
                savedSchedule.getId(),
                analysis.getId(),
                savedSchedule.getScheduleDateTime()
        );
        log.info("[ANALYSIS_UPDATE] analysisId={}, targetScheduleId={}", analysis.getId(), analysis.getTargetScheduleId());

        return savedSchedule;
    }

    private void syncLinkedScheduleFromAnalysis(Analysis analysis, Long authenticatedUserId) {
        if (analysis.getTargetScheduleId() == null) {
            return;
        }

        Schedule schedule = scheduleRepository.findById(analysis.getTargetScheduleId())
                .orElse(null);
        if (schedule == null) {
            return;
        }

        validateScheduleOwnership(schedule, authenticatedUserId);

        String title = firstNotBlank(
                extractTitleFromScheduleText(analysis.getScheduleText()),
                analysis.getTargetScheduleTitle(),
                schedule.getTitle()
        );
        LocalDateTime scheduleDateTime = extractScheduleDateTime(analysis.getScheduleText(), schedule.getScheduleDateTime());
        String memo = truncate(firstNotBlank(
                analysis.getSummary(),
                analysis.getFollowUpAction(),
                schedule.getMemo()
        ), 200);

        schedule.update(title, scheduleDateTime, memo);
    }

    private LocalDateTime extractScheduleDateTime(String scheduleText, LocalDateTime fallback) {
        if (scheduleText == null || scheduleText.isBlank()) {
            return fallback;
        }

        Matcher matcher = ISO_LOCAL_DATE_TIME_PATTERN.matcher(scheduleText);
        if (!matcher.find()) {
            return fallback;
        }

        try {
            return LocalDateTime.parse(matcher.group());
        } catch (RuntimeException exception) {
            log.warn("Failed to parse scheduleDateTime from scheduleText. scheduleText={}", scheduleText);
            return fallback;
        }
    }

    private String extractTitleFromScheduleText(String scheduleText) {
        if (scheduleText == null || scheduleText.isBlank()) {
            return null;
        }

        String title = ISO_LOCAL_DATE_TIME_PATTERN.matcher(scheduleText).replaceAll("");
        title = title.replace("(", "").replace(")", "");
        title = title.trim();

        if (title.isBlank()) {
            return null;
        }

        return truncate(title, 255);
    }

    private String buildScheduleTitle(AiAnalysisResponse aiResult) {
        String title = firstNotBlank(
                aiResult.scheduleTitle(),
                aiResult.targetScheduleTitle(),
                aiResult.summary(),
                "AI 분석 일정"
        );

        return truncate(title, 255);
    }

    private String buildScheduleMemo(AiAnalysisResponse aiResult) {
        // 캘린더 설명·대시보드 모달에 그대로 노출되므로 사람이 읽기 좋은 라벨 형식으로 구성한다.
        List<String> lines = new java.util.ArrayList<>();

        String customer = firstNotBlankOrNull(aiResult.customerName(), aiResult.contactName());
        if (customer != null) {
            String contact = aiResult.contactName();
            boolean hasSeparateContact = contact != null && !contact.isBlank() && !contact.equals(customer);
            lines.add("보낸 사람: " + (hasSeparateContact ? customer + " (" + contact + ")" : customer));
        }
        if (aiResult.attendees() != null && !aiResult.attendees().isBlank()) {
            lines.add("참석자: " + aiResult.attendees());
        }
        if (aiResult.summary() != null && !aiResult.summary().isBlank()) {
            lines.add(aiResult.summary());
        }
        if (aiResult.todoContent() != null && !aiResult.todoContent().isBlank()) {
            lines.add("다음 행동: " + aiResult.todoContent());
        }
        if (lines.isEmpty()) {
            lines.add("AI 분석 일정");
        }

        return truncate(String.join("\n", lines), 200);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return "AI 분석 일정";
    }

    private String firstNotBlankOrNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }

    private void validateAnalysisOwnership(Analysis analysis, Long authenticatedUserId) {
        validateSourceOwnership(analysis.getSource(), authenticatedUserId);
    }

    private void validateSourceOwnership(Source source, Long authenticatedUserId) {
        if (!source.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("You do not have permission to access this analysis.");
        }
    }

    private void validateSourceGroupOwnership(SourceGroup sourceGroup, Long authenticatedUserId) {
        if (!sourceGroup.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("You do not have permission to access this source group.");
        }
    }

    private void validateScheduleOwnership(Schedule schedule, Long authenticatedUserId) {
        if (!schedule.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("You do not have permission to access this schedule.");
        }
    }
}
