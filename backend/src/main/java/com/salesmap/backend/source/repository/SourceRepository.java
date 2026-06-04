package com.salesmap.backend.source.repository;

import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findByUserId(Long userId);

    Page<Source> findByUserId(Long userId, Pageable pageable);

    Page<Source> findByUserIdAndStatusNot(Long userId, SourceStatus status, Pageable pageable);

    List<Source> findByIntegrationId(Long integrationId);

    List<Source> findBySourceGroupIdOrderBySentAtAscIdAsc(Long sourceGroupId);

    boolean existsByIntegrationIdAndSourceTypeAndExternalSourceId(
            Long integrationId,
            SourceType sourceType,
            String externalSourceId
    );
}
