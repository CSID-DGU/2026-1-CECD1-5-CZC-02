package com.salesmap.backend.analysis.repository;

import com.salesmap.backend.analysis.entity.AnalysisRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisRevisionRepository extends JpaRepository<AnalysisRevision, Long> {

    List<AnalysisRevision> findByAnalysisIdOrderByIdAsc(Long analysisId);

    boolean existsByAnalysisId(Long analysisId);
}
