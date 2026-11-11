package com.salesmap.backend.person;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<PersonEntry, Long> {
    String SEARCH = "select p from PersonEntry p where p.user.id = :owner "
            + "and (:pattern is null or p.searchText like :pattern escape '!') order by p.id desc";

    @Query(SEARCH)
    List<PersonEntry> searchOwned(@Param("owner") Long owner, @Param("pattern") String pattern, Pageable pageable);

    /** 식별은 공유 연락처·동명이인 누락을 막기 위해 본인 대장 전체를 사용한다. */
    List<PersonEntry> findByUserIdOrderByIdAsc(Long userId);

    Optional<PersonEntry> findByIdAndUserId(Long id, Long userId);
}
