package com.salesmap.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRevisionRepository extends JpaRepository<TaskRevision, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from TaskRevision r order by r.id")
    List<TaskRevision> lockPending(org.springframework.data.domain.Pageable pageable);
    List<TaskRevision> findByOwnerIdOrderByIdAsc(Long ownerId);
}
