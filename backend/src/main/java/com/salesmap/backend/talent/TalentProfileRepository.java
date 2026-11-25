package com.salesmap.backend.talent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TalentProfileRepository extends JpaRepository<TalentProfile, Long> {
    List<TalentProfile> findByUserIdOrderByIdDesc(Long userId);

    Optional<TalentProfile> findByIdAndUserId(Long id, Long userId);

    List<TalentProfile> findByUserIdAndBirthYearAndIdNot(Long userId, Integer birthYear, Long id);

    /** 서버가 다시 켜지면 메모리에 있던 처리 대기열이 사라지므로, 처리 중으로 남은 건을 실패로 돌린다. */
    @Transactional
    @Modifying
    @Query("update TalentProfile t set t.status = com.salesmap.backend.talent.TalentStatus.FAILED, "
            + "t.errorMessage = :message, t.updatedAt = :now "
            + "where t.status = com.salesmap.backend.talent.TalentStatus.PROCESSING")
    int failInterrupted(@Param("message") String message, @Param("now") LocalDateTime now);
}
