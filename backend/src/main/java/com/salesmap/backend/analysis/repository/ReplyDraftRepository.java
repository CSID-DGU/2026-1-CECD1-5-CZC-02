package com.salesmap.backend.analysis.repository;

import com.salesmap.backend.analysis.entity.ReplyDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReplyDraftRepository extends JpaRepository<ReplyDraft, Long> {

    Optional<ReplyDraft> findFirstByAnalysisIdAndSentAtIsNullOrderByIdDesc(Long analysisId);

    List<ReplyDraft> findByAnalysisIdOrderByIdAsc(Long analysisId);
}
