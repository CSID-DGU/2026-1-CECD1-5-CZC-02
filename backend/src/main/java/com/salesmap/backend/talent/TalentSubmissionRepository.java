package com.salesmap.backend.talent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TalentSubmissionRepository extends JpaRepository<TalentSubmission, Long> {
    List<TalentSubmission> findByOwnerIdAndTaskIdOrderByPositionAsc(Long ownerId, String taskId);
    List<TalentSubmission> findByOwnerIdOrderByTaskIdAscPositionAsc(Long ownerId);
    void deleteByOwnerIdAndTaskId(Long ownerId, String taskId);
}
