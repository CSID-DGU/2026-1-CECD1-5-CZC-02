package com.salesmap.backend.schedule.repository;

import com.salesmap.backend.schedule.entity.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByUserId(Long userId);

    Page<Schedule> findByUserId(Long userId, Pageable pageable);

    List<Schedule> findByAnalysisId(Long analysisId);
}
