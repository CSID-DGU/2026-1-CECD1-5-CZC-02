package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.minutes.service.MeetingNoteService;
import com.salesmap.backend.source.entity.SourceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;

/** B 저장본 읽기만 한다. 이름이나 A 후보를 확정된 미팅 이력으로 추측하지 않는다. */
@Service
public class PersonHistoryService {
    private final PersonService people;
    private final MeetingNoteRepository notes;
    private final MeetingNoteService meetings;
    private final ObjectMapper mapper;

    public PersonHistoryService(PersonService people, MeetingNoteRepository notes,
            MeetingNoteService meetings, ObjectMapper mapper) {
        this.people=people; this.notes=notes; this.meetings=meetings; this.mapper=mapper;
    }

    @Transactional(readOnly=true)
    public List<MeetingNoteDocument> list(String personId,int page,int size,Long owner) {
        people.get(personId,owner); // 권한/ID 검사 먼저. 타 사용자 대장의 존재 여부도 숨긴다.
        if(page<0 || page>1000000 || size<1 || size>100)
            throw new IllegalArgumentException("page는 0~1000000, size는 1~100이어야 합니다.");
        return notes.findByUserId(owner).stream()
                .filter(n -> n.getUser().getId().equals(owner) && n.getSource().getUser().getId().equals(owner))
                .filter(n -> !n.isArchived() && n.getStatus()==MeetingNoteStatus.CONFIRMED
                        && n.getSource().getStatus()!=SourceStatus.DELETED)
                .filter(n -> contains(n.getParticipantsJson(),personId) || contains(n.getPerPersonJson(),personId))
                .sorted(Comparator.comparing(MeetingNote::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(MeetingNote::getId,Comparator.reverseOrder()))
                .skip((long)page*size).limit(size)
                .map(n -> meetings.getMeetingNote("mn_"+n.getId(),owner).meetingNote()).toList();
    }

    private boolean contains(String json,String personId) {
        if(json==null) return false;
        try {
            JsonNode rows=mapper.readTree(json);
            if(!rows.isArray()) throw new IllegalArgumentException();
            for(JsonNode row:rows) if(personId.equals(row.path("personId").asText(null))) return true;
            return false;
        } catch(Exception error) { throw new IllegalStateException("저장된 회의록의 인물 연결 형식을 확인해야 합니다."); }
    }
}
