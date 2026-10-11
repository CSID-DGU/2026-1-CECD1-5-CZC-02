package com.salesmap.backend.analysis.repository;

import com.salesmap.backend.analysis.entity.Analysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    List<Analysis> findBySourceId(Long sourceId);

    List<Analysis> findBySourceIdIn(List<Long> sourceIds);

    @Query("""
            select a
            from Analysis a
            join fetch a.source s
            where s.user.id = :userId
            order by a.id desc
            """)
    List<Analysis> findByUserId(@Param("userId") Long userId);

    @Query("""
            select a
            from Analysis a
            join fetch a.source s
            where s.user.id = :userId
              and s.senderEmail = :senderEmail
              and s.id <> :sourceId
            order by a.id desc
            """)
    List<Analysis> findRecentSenderAnalyses(
            @Param("userId") Long userId,
            @Param("senderEmail") String senderEmail,
            @Param("sourceId") Long sourceId,
            Pageable pageable
    );
}
