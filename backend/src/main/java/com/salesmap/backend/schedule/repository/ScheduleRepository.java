package com.salesmap.backend.schedule.repository;

import com.salesmap.backend.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByUserId(Long userId);

    List<Schedule> findByAnalysisId(Long analysisId);
}
