package com.salesmap.backend.analysis.repository;

import com.salesmap.backend.analysis.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    List<Analysis> findBySourceId(Long sourceId);

    List<Analysis> findBySourceIdIn(List<Long> sourceIds);
}
