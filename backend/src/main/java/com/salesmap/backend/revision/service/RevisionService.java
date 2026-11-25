package com.salesmap.backend.revision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.analysis.entity.AnalysisRevision;
import com.salesmap.backend.analysis.repository.AnalysisRevisionRepository;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.dto.RevisionResponse;
import com.salesmap.backend.revision.dto.RevisionMetricsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

@Service
public class RevisionService {
    private static final Set<String> SCREENS = Set.of("IDENTITY_CANDIDATE", "MEETING_NOTE_REVIEW", "TASK_REVIEW", "BRIEFING", "RESUME_REVIEW");
    private static final Set<String> TARGET_TYPES = Set.of("IDENTITY_RESOLUTION", "MEETING_NOTE", "TASK", "BRIEFING_ITEM", "RESUME_PROFILE");
    private static final Set<String> ACTIONS = Set.of("ACCEPTED", "EDITED", "REJECTED", "SELECTED_CANDIDATE", "CREATED_NEW", "OPENED", "IGNORED");
    private static final Set<String> PROPOSERS = Set.of("RULE", "MODEL", "LLM");

    private final AnalysisRevisionRepository revisions;
    private final ObjectMapper mapper;

    public RevisionService(AnalysisRevisionRepository revisions, ObjectMapper mapper) {
        this.revisions = revisions;
        this.mapper = mapper;
    }

    @Transactional
    public RevisionResponse create(RevisionCreateRequest request, Long ownerUserId) {
        Objects.requireNonNull(request, "revision request is required");
        Objects.requireNonNull(ownerUserId, "owner user id is required");
        validate(request);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withNano(0);
        AnalysisRevision saved = revisions.save(new AnalysisRevision(
                ownerUserId, request.screen(), request.targetType(), request.targetId(), request.fieldName(),
                write(request.proposedValue()), write(request.finalValue()), request.action(), request.proposedBy(),
                request.modelVersion(), "user_" + ownerUserId, now));
        return RevisionResponse.from(saved, mapper);
    }

    @Transactional(readOnly = true)
    public List<RevisionResponse> list(Long ownerUserId) {
        return revisions.findByOwnerUserIdOrderByIdAsc(ownerUserId).stream()
                .map(row -> RevisionResponse.from(row, mapper)).toList();
    }

    @Transactional(readOnly = true)
    public RevisionMetricsResponse metrics(Long ownerUserId) {
        Objects.requireNonNull(ownerUserId, "owner user id is required");

        List<AnalysisRevision> decisions = revisions.findByOwnerUserIdOrderByIdAsc(ownerUserId).stream()
                .filter(this::isDecision)
                .toList();

        Map<String, Set<String>> actionsByTarget = new LinkedHashMap<>();
        Map<String, FieldCounts> countsByField = new LinkedHashMap<>();
        for (AnalysisRevision revision : decisions) {
            String targetKey = revision.getTargetType() + ":" + revision.getTargetId();
            actionsByTarget.computeIfAbsent(targetKey, ignored -> new LinkedHashSet<>())
                    .add(revision.getAction());

            FieldCounts counts = countsByField.computeIfAbsent(revision.getFieldName(), ignored -> new FieldCounts());
            counts.decisions++;
            if ("EDITED".equals(revision.getAction())) counts.edited++;
        }

        long withoutEdit = actionsByTarget.values().stream()
                .filter(actions -> actions.contains("ACCEPTED") && !actions.contains("EDITED"))
                .count();
        double withoutEditRate = rate(withoutEdit, actionsByTarget.size());
        List<RevisionMetricsResponse.FieldMetric> fields = countsByField.entrySet().stream()
                .map(entry -> new RevisionMetricsResponse.FieldMetric(
                        entry.getKey(), entry.getValue().decisions, entry.getValue().edited,
                        rate(entry.getValue().edited, entry.getValue().decisions)))
                .toList();

        return new RevisionMetricsResponse(actionsByTarget.size(), withoutEdit, withoutEditRate, fields);
    }

    private boolean isDecision(AnalysisRevision revision) {
        return "ACCEPTED".equals(revision.getAction()) || "EDITED".equals(revision.getAction());
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static final class FieldCounts {
        private long decisions;
        private long edited;
    }

    private void validate(RevisionCreateRequest request) {
        requireText(request.screen(), "screen");
        requireText(request.targetType(), "targetType");
        requireText(request.targetId(), "targetId");
        requireText(request.fieldName(), "fieldName");
        requireText(request.action(), "action");
        requireText(request.proposedBy(), "proposedBy");
        if (!SCREENS.contains(request.screen())) throw new IllegalArgumentException("지원하지 않는 revision screen입니다.");
        if (!TARGET_TYPES.contains(request.targetType())) throw new IllegalArgumentException("지원하지 않는 revision targetType입니다.");
        if (!ACTIONS.contains(request.action())) throw new IllegalArgumentException("지원하지 않는 revision action입니다.");
        if (!PROPOSERS.contains(request.proposedBy())) throw new IllegalArgumentException("지원하지 않는 revision proposedBy입니다.");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("수정 값을 JSON으로 저장할 수 없습니다.", exception); }
    }
}
