package com.salesmap.backend.integration.repository;

import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    boolean existsByUserIdAndProvider(Long userId, IntegrationProvider provider);

    Optional<Integration> findByUserIdAndProvider(Long userId, IntegrationProvider provider);

    List<Integration> findByUserId(Long userId);
}
