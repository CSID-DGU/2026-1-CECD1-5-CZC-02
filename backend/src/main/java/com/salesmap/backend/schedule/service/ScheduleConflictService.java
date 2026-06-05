package com.salesmap.backend.schedule.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.schedule.dto.ScheduleConflictResponse;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.exception.ScheduleConflictException;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScheduleConflictService {

    private static final Pattern ISO_LOCAL_DATE_TIME_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?");
    private static final long NEARBY_HOURS = 3L;

    private static final Set<String> GENERIC_TITLE_TOKENS = Set.of(
            "미팅", "회의", "상담", "일정", "요청", "문의", "관련", "제품", "소개", "도입",
            "온라인", "오프라인", "데모", "서비스", "솔루션", "platform", "solution", "service",
            "meeting", "demo", "sales"
    );

    private final ScheduleRepository scheduleRepository;

    public ScheduleConflictService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    public void validateBeforeRegister(Analysis analysis, Long authenticatedUserId, boolean force) {
        if (!"CREATE".equals(analysis.getActionType()) && !"UPDATE".equals(analysis.getActionType())) {
            return;
        }

        LocalDateTime candidateDateTime = extractScheduleDateTime(analysis.getScheduleText());
        if (candidateDateTime == null) {
            return;
        }

        String candidateTitle = firstNotBlank(
                analysis.getTargetScheduleTitle(),
                extractScheduleTitle(analysis.getScheduleText()),
                analysis.getSummary(),
                "AI 분석 일정"
        );

        Long excludedScheduleId = "UPDATE".equals(analysis.getActionType()) ? analysis.getTargetScheduleId() : null;
        List<Schedule> schedules = scheduleRepository.findByUserId(authenticatedUserId).stream()
                .filter(schedule -> schedule.getStatus() != ScheduleStatus.CANCELED)
                .filter(schedule -> excludedScheduleId == null || !excludedScheduleId.equals(schedule.getId()))
                .sorted(Comparator.comparing(Schedule::getScheduleDateTime))
                .toList();

        List<Schedule> duplicateSchedules = schedules.stream()
                .filter(schedule -> schedule.getScheduleDateTime().equals(candidateDateTime))
                .filter(schedule -> hasSimilarTitle(schedule.getTitle(), candidateTitle))
                .toList();
        if (!duplicateSchedules.isEmpty()) {
            throw conflict(
                    "DUPLICATE_SCHEDULE",
                    "이미 같은 일정이 등록되어 있습니다.",
                    candidateTitle,
                    candidateDateTime,
                    duplicateSchedules
            );
        }

        List<Schedule> sameTimeSchedules = schedules.stream()
                .filter(schedule -> schedule.getScheduleDateTime().equals(candidateDateTime))
                .toList();
        if (!sameTimeSchedules.isEmpty() && !force) {
            throw conflict(
                    "SAME_TIME_SCHEDULE",
                    "같은 시간대에 다른 일정이 있습니다.",
                    candidateTitle,
                    candidateDateTime,
                    sameTimeSchedules
            );
        }

        List<Schedule> nearbySchedules = schedules.stream()
                .filter(schedule -> isNearby(candidateDateTime, schedule.getScheduleDateTime()))
                .toList();
        if (!nearbySchedules.isEmpty() && !force) {
            throw conflict(
                    "NEARBY_SCHEDULE",
                    "등록하려는 일정 전후 3시간 이내에 다른 일정이 있습니다.",
                    candidateTitle,
                    candidateDateTime,
                    nearbySchedules
            );
        }
    }

    private ScheduleConflictException conflict(
            String type,
            String message,
            String candidateTitle,
            LocalDateTime candidateDateTime,
            List<Schedule> schedules
    ) {
        return new ScheduleConflictException(
                message,
                new ScheduleConflictResponse(
                        type,
                        new ScheduleConflictResponse.CandidateSchedule(candidateTitle, candidateDateTime),
                        schedules.stream()
                                .map(ScheduleConflictResponse.ExistingSchedule::from)
                                .toList()
                )
        );
    }

    private boolean isNearby(LocalDateTime candidateDateTime, LocalDateTime existingDateTime) {
        long minutes = Math.abs(Duration.between(candidateDateTime, existingDateTime).toMinutes());
        return minutes <= NEARBY_HOURS * 60;
    }

    private LocalDateTime extractScheduleDateTime(String scheduleText) {
        if (scheduleText == null || scheduleText.isBlank()) {
            return null;
        }

        Matcher matcher = ISO_LOCAL_DATE_TIME_PATTERN.matcher(scheduleText);
        if (!matcher.find()) {
            return null;
        }

        return LocalDateTime.parse(matcher.group());
    }

    private String extractScheduleTitle(String scheduleText) {
        if (scheduleText == null || scheduleText.isBlank()) {
            return null;
        }

        String title = ISO_LOCAL_DATE_TIME_PATTERN.matcher(scheduleText).replaceAll("");
        title = title.replace("(", "").replace(")", "").trim();
        return title.isBlank() ? null : title;
    }

    private boolean hasSimilarTitle(String left, String right) {
        List<String> leftTokens = significantTokens(left);
        List<String> rightTokens = significantTokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return false;
        }

        String normalizedLeft = String.join(" ", leftTokens);
        String normalizedRight = String.join(" ", rightTokens);
        int sharedCount = sharedTokenCount(leftTokens, rightTokens);

        return normalizedLeft.equals(normalizedRight)
                || normalizedLeft.contains(normalizedRight)
                || normalizedRight.contains(normalizedLeft)
                || sharedCount >= 2;
    }

    private int sharedTokenCount(List<String> leftTokens, List<String> rightTokens) {
        int count = 0;
        for (String token : leftTokens) {
            if (rightTokens.contains(token)) {
                count += 1;
            }
        }
        return count;
    }

    private List<String> significantTokens(String value) {
        if (value == null) {
            return List.of();
        }

        return normalizeTitle(value).stream()
                .filter(token -> token.length() >= 2)
                .filter(token -> !GENERIC_TITLE_TOKENS.contains(token))
                .toList();
    }

    private List<String> normalizeTitle(String value) {
        if (value == null) {
            return List.of();
        }

        String normalized = value.toLowerCase()
                .replaceAll("[^a-z0-9가-힣]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isBlank()) {
            return List.of();
        }

        return List.of(normalized.split(" "));
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
