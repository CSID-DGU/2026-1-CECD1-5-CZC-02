package com.salesmap.backend.integration.repository;

import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    boolean existsByUserIdAndProvider(Long userId, IntegrationProvider provider);
}
