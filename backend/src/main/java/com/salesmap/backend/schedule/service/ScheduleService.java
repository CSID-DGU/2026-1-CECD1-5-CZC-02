package com.salesmap.backend.schedule.service;

import com.salesmap.backend.analysis.entity.Analysis;
import com.salesmap.backend.analysis.repository.AnalysisRepository;
import com.salesmap.backend.schedule.dto.ScheduleCreateRequest;
import com.salesmap.backend.schedule.dto.ScheduleResponse;
import com.salesmap.backend.schedule.entity.Schedule;
import com.salesmap.backend.schedule.entity.ScheduleStatus;
import com.salesmap.backend.schedule.repository.ScheduleRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final AnalysisRepository analysisRepository;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            UserRepository userRepository,
            AnalysisRepository analysisRepository
    ) {
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.analysisRepository = analysisRepository;
    }

    @Transactional
    public ScheduleResponse createSchedule(ScheduleCreateRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        Analysis analysis = findAnalysis(request.analysisId());

        Schedule schedule = new Schedule(
                user,
                analysis,
                request.title(),
                request.scheduleDateTime(),
                request.memo(),
                null,
                ScheduleStatus.SCHEDULED
        );

        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    private Analysis findAnalysis(Long analysisId) {
        if (analysisId == null) {
            return null;
        }

        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new NoSuchElementException("분석 결과를 찾을 수 없습니다."));
    }
}
