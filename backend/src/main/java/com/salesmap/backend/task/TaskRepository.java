package com.salesmap.backend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntry, Long> {
    List<TaskEntry> findByOwnerIdOrderByIdDesc(Long ownerId);
    Optional<TaskEntry> findByOwnerIdAndDraftKey(Long ownerId, String draftKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskEntry t where t.id = :id and t.ownerId = :ownerId")
    Optional<TaskEntry> lockOwned(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
