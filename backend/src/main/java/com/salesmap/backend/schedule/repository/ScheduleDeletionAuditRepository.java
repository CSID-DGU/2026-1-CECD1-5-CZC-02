package com.salesmap.backend.schedule.repository;

import com.salesmap.backend.schedule.entity.ScheduleDeletionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleDeletionAuditRepository extends JpaRepository<ScheduleDeletionAudit, Long> {
}
