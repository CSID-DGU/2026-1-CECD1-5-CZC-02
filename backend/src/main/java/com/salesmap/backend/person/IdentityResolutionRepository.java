package com.salesmap.backend.person;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface IdentityResolutionRepository extends JpaRepository<IdentityResolutionEntry,Long> {
    List<IdentityResolutionEntry> findByUserIdAndSourceRefOrderByMentionIndexAsc(Long userId,String sourceRef);
    String LOCK_OWNED="select r from IdentityResolutionEntry r where r.resolutionId=:id and r.user.id=:owner";
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query(LOCK_OWNED)
    Optional<IdentityResolutionEntry> lockOwned(@Param("id") String id,@Param("owner") Long owner);
}
