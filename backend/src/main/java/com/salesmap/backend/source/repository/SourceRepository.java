package com.salesmap.backend.source.repository;

import com.salesmap.backend.source.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findByUserId(Long userId);

    List<Source> findByIntegrationId(Long integrationId);
}
