package com.salesmap.backend.taskstatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskStatusDismissalRepository extends JpaRepository<TaskStatusDismissal, Long> {

    List<TaskStatusDismissal> findByUserId(Long userId);
}
