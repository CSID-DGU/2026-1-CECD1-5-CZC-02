package com.salesmap.backend.minutes.repository;

import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MeetingNoteRepository extends JpaRepository<MeetingNote, Long> {

    @Query("""
            select n
            from MeetingNote n
            join fetch n.source
            where n.user.id = :userId
              and n.archivedAt is null
            order by n.id desc
            """)
    List<MeetingNote> findByUserId(@Param("userId") Long userId);

    @Query("""
            select n
            from MeetingNote n
            join fetch n.source
            where n.user.id = :userId
              and n.status = :status
              and n.archivedAt is null
            order by n.id desc
            """)
    List<MeetingNote> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") MeetingNoteStatus status);

    /** 보관함: 보관한 회의록만, 최근에 보관한 순 */
    @Query("""
            select n
            from MeetingNote n
            join fetch n.source
            where n.user.id = :userId
              and n.archivedAt is not null
            order by n.archivedAt desc
            """)
    List<MeetingNote> findArchivedByUserId(@Param("userId") Long userId);

    /** 같은 메일(스레드)로 이미 만든 회의록 */
    Optional<MeetingNote> findFirstByUserIdAndOriginKeyOrderByIdDesc(Long userId, String originKey);
}
