package com.salesmap.backend.source.repository;

import com.salesmap.backend.source.entity.SourceGroup;
import com.salesmap.backend.source.entity.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceGroupRepository extends JpaRepository<SourceGroup, Long> {

    Optional<SourceGroup> findByIntegrationIdAndSourceTypeAndExternalGroupId(
            Long integrationId,
            SourceType sourceType,
            String externalGroupId
    );
}
