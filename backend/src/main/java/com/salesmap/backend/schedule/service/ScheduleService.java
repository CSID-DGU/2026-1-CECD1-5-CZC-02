package com.salesmap.backend.schedule.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.calendar.service.GoogleCalendarEventService;
import com.salesmap.backend.schedule.dto.ScheduleCreateRequest;
import com.salesmap.backend.schedule.dto.ScheduleResponse;
import com.salesmap.backend.schedule.dto.ScheduleUpdateRequest;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final AnalysisRepository analysisRepository;
    private final GoogleCalendarEventService googleCalendarEventService;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            UserRepository userRepository,
            AnalysisRepository analysisRepository,
            GoogleCalendarEventService googleCalendarEventService
    ) {
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.analysisRepository = analysisRepository;
        this.googleCalendarEventService = googleCalendarEventService;
    }

    @Transactional
    public ScheduleResponse createSchedule(ScheduleCreateRequest request, Long authenticatedUserId) {
        validateRequestedUser(request.userId(), authenticatedUserId);

        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        Analysis analysis = findAnalysis(request.analysisId(), authenticatedUserId);

        Schedule schedule = new Schedule(
                user,
                analysis,
                request.title(),
                request.scheduleDateTime(),
                truncate(request.memo(), 2000),
                null,
                ScheduleStatus.SCHEDULED
        );

        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesByUser(Long requestedUserId, Long authenticatedUserId, int page, int size) {
        validateRequestedUser(requestedUserId, authenticatedUserId);

        if (!userRepository.existsById(authenticatedUserId)) {
            throw new NoSuchElementException("사용자를 찾을 수 없습니다.");
        }

        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "scheduleDateTime")
        );

        return scheduleRepository.findByUserId(authenticatedUserId, pageRequest).stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional
    public ScheduleResponse updateSchedule(Long scheduleId, ScheduleUpdateRequest request, Long authenticatedUserId) {
        Schedule schedule = findScheduleOwnedBy(scheduleId, authenticatedUserId);

        String nextTitle = isBlank(request.title()) ? schedule.getTitle() : request.title().trim();
        String nextMemo = request.memo() == null ? schedule.getMemo() : truncate(request.memo(), 2000);

        schedule.update(
                truncate(nextTitle, 255),
                request.scheduleDateTime() == null ? schedule.getScheduleDateTime() : request.scheduleDateTime(),
                nextMemo
        );
        googleCalendarEventService.updateEventForSchedule(schedule, authenticatedUserId);

        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public void deleteSchedule(Long scheduleId, Long authenticatedUserId) {
        Schedule schedule = findScheduleOwnedBy(scheduleId, authenticatedUserId);

        if (schedule.getAnalysis() != null) {
            schedule.getAnalysis().markDeleted();
        }

        googleCalendarEventService.deleteEventForSchedule(schedule, authenticatedUserId);
        scheduleRepository.delete(schedule);
    }

    private Schedule findScheduleOwnedBy(Long scheduleId, Long authenticatedUserId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다."));

        if (!schedule.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("다른 사용자의 일정에 접근할 수 없습니다.");
        }

        return schedule;
    }

    private Analysis findAnalysis(Long analysisId, Long authenticatedUserId) {
        if (analysisId == null) {
            return null;
        }

        Analysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("분석 결과를 찾을 수 없습니다."));

        if (!analysis.getSource().getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("해당 분석 결과에 접근할 권한이 없습니다.");
        }

        return analysis;
    }

    private void validateRequestedUser(Long requestedUserId, Long authenticatedUserId) {
        if (requestedUserId != null && !requestedUserId.equals(authenticatedUserId)) {
            throw new AccessDeniedException("다른 사용자의 데이터에 접근할 수 없습니다.");
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }

        return Math.min(size, 100);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
