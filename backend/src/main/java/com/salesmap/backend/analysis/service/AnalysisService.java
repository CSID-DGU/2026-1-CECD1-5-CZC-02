package com.salesmap.backend.analysis.service;

import com.salesmap.backend.ai.client.AiClient;
import com.salesmap.backend.ai.dto.AiAnalysisRequest;
import com.salesmap.backend.ai.dto.AiAnalysisResponse;
import com.salesmap.backend.ai.dto.AiGroupAnalysisRequest;
import com.salesmap.backend.analysis.dto.AnalysisCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisGroupCreateRequest;
import com.salesmap.backend.analysis.dto.AnalysisResponse;
import com.salesmap.backend.analysis.dto.AnalysisUpdateRequest;
import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.entity.AnalysisStatus;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceGroup;
import com.salesmap.backend.source.repository.SourceGroupRepository;
import com.salesmap.backend.source.repository.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final Pattern ISO_LOCAL_DATE_TIME_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?");

    private final AnalysisRepository analysisRepository;
    private final SourceRepository sourceRepository;
    private final SourceGroupRepository sourceGroupRepository;
    private final ScheduleRepository scheduleRepository;
    private final AiClient aiClient;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            SourceRepository sourceRepository,
            SourceGroupRepository sourceGroupRepository,
            ScheduleRepository scheduleRepository,
            AiClient aiClient
    ) {
        this.analysisRepository = analysisRepository;
        this.sourceRepository = sourceRepository;
        this.sourceGroupRepository = sourceGroupRepository;
        this.scheduleRepository = scheduleRepository;
        this.aiClient = aiClient;
    }

    @Transactional
    public AnalysisResponse createAnalysis(AnalysisCreateRequest request, Long authenticatedUserId) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new NoSuchElementException("Source not found."));
        validateSourceOwnership(source, authenticatedUserId);

        AiAnalysisResponse aiResult = aiClient.analyze(toAiAnalysisRequest(source));
        Analysis analysis = createAnalysisEntity(source, aiResult);
        source.markAnalyzed();
        Analysis savedAnalysis = analysisRepository.save(analysis);

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

        AiAnalysisResponse aiResult = aiClient.analyzeGroup(toAiGroupAnalysisRequest(sourceGroup, sources));
        Analysis analysis = createAnalysisEntity(sources.get(0), aiResult);
        sources.forEach(Source::markAnalyzed);
        Analysis savedAnalysis = analysisRepository.save(analysis);

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
    public AnalysisResponse updateAnalysis(Long analysisId, AnalysisUpdateRequest request, Long authenticatedUserId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("Analysis not found."));
        validateAnalysisOwnership(analysis, authenticatedUserId);

        analysis.updateEditableFields(
                truncate(request.customerName(), 255),
                truncate(request.contactName(), 100),
                truncate(request.productName(), 255),
                request.amount(),
                truncate(request.attendees(), 500),
                truncate(request.actionType(), 50),
                truncate(request.targetScheduleTitle(), 255),
                truncate(request.actionReason(), 255),
                truncate(request.scheduleText(), 500),
                truncate(request.followUpAction(), 500),
                request.summary()
        );

        return AnalysisResponse.from(analysis);
    }

    @Transactional
    public void applyApprovedScheduleAction(Analysis analysis, Long authenticatedUserId) {
        validateAnalysisOwnership(analysis, authenticatedUserId);
        applyScheduleAction(analysis, toAiAnalysisResponse(analysis), authenticatedUserId);
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
        return new Analysis(
                source,
                truncate(aiResult.customerName(), 255),
                truncate(aiResult.contactName(), 100),
                truncate(aiResult.productName(), 255),
                aiResult.amount(),
                truncate(aiResult.attendees(), 500),
                truncate(aiResult.actionType(), 50),
                aiResult.targetScheduleId(),
                truncate(aiResult.targetScheduleTitle(), 255),
                truncate(aiResult.actionReason(), 255),
                truncate(buildScheduleText(aiResult), 255),
                truncate(aiResult.todoContent(), 255),
                aiResult.summary(),
                AnalysisStatus.ANALYZED,
                LocalDateTime.now(),
                null
        );
    }

    private AiAnalysisRequest toAiAnalysisRequest(Source source) {
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
                getExistingSchedules(source.getUser().getId())
        );
    }

    private AiGroupAnalysisRequest toAiGroupAnalysisRequest(SourceGroup sourceGroup, List<Source> sources) {
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
                getExistingSchedules(sourceGroup.getUser().getId())
        );
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
                scheduleTitle,
                scheduleDateTime,
                analysis.getFollowUpAction(),
                null,
                analysis.getSummary(),
                null
        );
    }

    private void applyScheduleAction(Analysis analysis, AiAnalysisResponse aiResult, Long authenticatedUserId) {
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
            return;
        }

        if ("CREATE".equals(aiResult.actionType())) {
            if (analysis.getTargetScheduleId() != null
                    && scheduleRepository.findById(analysis.getTargetScheduleId()).isPresent()) {
                log.info(
                        "[SCHEDULE_CREATE] skip duplicate creation. analysisId={}, scheduleId={}",
                        analysis.getId(),
                        analysis.getTargetScheduleId()
                );
                return;
            }
            createScheduleFromAnalysis(analysis, aiResult);
            return;
        }

        Schedule schedule = findTargetSchedule(aiResult, authenticatedUserId);
        if (schedule == null) {
            log.info(
                    "[SCHEDULE_ACTION] target schedule not found. actionType={}, analysisId={}, targetScheduleId={}, targetScheduleTitle={}, scheduleTitle={}",
                    aiResult.actionType(),
                    analysis.getId(),
                    aiResult.targetScheduleId(),
                    aiResult.targetScheduleTitle(),
                    aiResult.scheduleTitle()
            );
            return;
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
            return;
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
        }
    }

    private Schedule findTargetSchedule(AiAnalysisResponse aiResult, Long authenticatedUserId) {
        if (aiResult.targetScheduleId() != null) {
            Schedule schedule = scheduleRepository.findById(aiResult.targetScheduleId())
                    .orElse(null);
            if (schedule != null) {
                validateScheduleOwnership(schedule, authenticatedUserId);
            }
            return schedule;
        }

        String titleHint = firstNotBlankOrNull(aiResult.targetScheduleTitle(), aiResult.scheduleTitle());
        if (titleHint == null || titleHint.isBlank()) {
            return null;
        }

        String normalizedHint = titleHint.toLowerCase();
        return scheduleRepository.findByUserId(authenticatedUserId).stream()
                .filter(schedule -> schedule.getStatus() != ScheduleStatus.CANCELED)
                .filter(schedule -> {
                    String title = schedule.getTitle() == null ? "" : schedule.getTitle().toLowerCase();
                    return title.contains(normalizedHint) || normalizedHint.contains(title);
                })
                .findFirst()
                .orElse(null);
    }

    private void createScheduleFromAnalysis(Analysis analysis, AiAnalysisResponse aiResult) {
        if (aiResult.scheduleDateTime() == null) {
            log.info(
                    "Skip CREATE schedule because scheduleDateTime is null. analysisId={}, scheduleTitle={}, summary={}",
                    analysis.getId(),
                    aiResult.scheduleTitle(),
                    aiResult.summary()
            );
            return;
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
        return truncate(String.join("\n", List.of(
                        "summary: " + nullToDash(aiResult.summary()),
                        "nextAction: " + nullToDash(aiResult.todoContent()),
                        "actionReason: " + nullToDash(aiResult.actionReason())
                )), 200);
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
