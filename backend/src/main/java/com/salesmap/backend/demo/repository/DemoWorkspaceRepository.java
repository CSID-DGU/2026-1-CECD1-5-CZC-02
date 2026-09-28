package com.salesmap.backend.demo.repository;

import com.salesmap.backend.demo.entity.DemoWorkspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DemoWorkspaceRepository extends JpaRepository<DemoWorkspace, Long> {

    Optional<DemoWorkspace> findByOwnerUserId(Long ownerUserId);

    Optional<DemoWorkspace> findByOwnerUserIdAndEnabledTrue(Long ownerUserId);

    Optional<DemoWorkspace> findByDemoUserId(Long demoUserId);

    boolean existsByDemoUserId(Long demoUserId);
}
