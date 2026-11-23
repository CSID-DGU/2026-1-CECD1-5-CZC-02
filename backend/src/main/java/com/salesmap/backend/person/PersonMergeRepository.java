package com.salesmap.backend.person;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface PersonMergeRepository extends JpaRepository<PersonMergeEntry,Long> {
    interface Coordinates { Long getSourceId(); Long getTargetId(); }
    @Query("select m.sourceId as sourceId,m.targetId as targetId from PersonMergeEntry m where m.id=:id and m.userId=:owner")
    Optional<Coordinates> coordinates(@Param("id") Long id,@Param("owner") Long owner);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PersonMergeEntry m where m.id=:id and m.userId=:owner")
    Optional<PersonMergeEntry> lockOwned(@Param("id") Long id,@Param("owner") Long owner);
    @Query("select m from PersonMergeEntry m where m.userId=:owner and (m.targetId=:target or m.sourceId=:person) order by m.id desc")
    List<PersonMergeEntry> related(@Param("owner") Long owner,@Param("target") Long target,@Param("person") Long person,Pageable page);
}
