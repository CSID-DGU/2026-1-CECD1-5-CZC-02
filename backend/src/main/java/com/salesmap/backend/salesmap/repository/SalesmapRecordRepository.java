package com.salesmap.backend.salesmap.repository;

import com.salesmap.backend.salesmap.entity.SalesmapRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesmapRecordRepository extends JpaRepository<SalesmapRecord, Long> {

    List<SalesmapRecord> findByAnalysisId(Long analysisId);

    Optional<SalesmapRecord> findByExternalRecordId(String externalRecordId);
}
