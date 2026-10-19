package com.salesmap.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRevisionRepository extends JpaRepository<TaskRevision, Long> {
    List<TaskRevision> findByOwnerIdOrderByIdAsc(Long ownerId);
}
