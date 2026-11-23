package com.salesmap.backend.minutes.repository;

import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 확인 시간을 더한다. 두 탭이 동시에 보내도 값이 사라지지 않게 DB에서 더하고, 확인 대기 중인 본인 회의록만 바꾼다 */
    @Modifying
    @Query("""
            update MeetingNote n
            set n.reviewActiveSeconds = coalesce(n.reviewActiveSeconds, 0) + :seconds
            where n.id = :noteId
              and n.user.id = :userId
              and n.status = com.salesmap.backend.minutes.entity.MeetingNoteStatus.DRAFT
              and n.archivedAt is null
            """)
    int addReviewSeconds(@Param("noteId") Long noteId, @Param("userId") Long userId, @Param("seconds") int seconds);
}
