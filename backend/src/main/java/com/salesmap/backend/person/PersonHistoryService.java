package com.salesmap.backend.person;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.minutes.service.MeetingNoteService;
import com.salesmap.backend.source.entity.SourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;

/** B 저장본 읽기만 한다. 이름이나 A 후보를 확정된 미팅 이력으로 추측하지 않는다. */
@Service
public class PersonHistoryService {
    private static final Logger log = LoggerFactory.getLogger(PersonHistoryService.class);
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
                .filter(n -> matchesPerson(n,personId))
                .sorted(Comparator.comparing(MeetingNote::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(MeetingNote::getId,Comparator.reverseOrder()))
                .skip((long)page*size).limit(size)
                .map(n -> meetings.getMeetingNote("mn_"+n.getId(),owner).meetingNote()).toList();
    }

    private boolean matchesPerson(MeetingNote note,String personId) {
        try {
            // 첫 배열에서 ID가 맞아도 두 배열을 모두 검사한다. 깨진 노트는 전체를 건너뛴다.
            boolean participant=contains(note.getParticipantsJson(),personId);
            boolean perPerson=contains(note.getPerPersonJson(),personId);
            return participant || perPerson;
        } catch(JsonProcessingException | IllegalArgumentException error) {
            // 원문 JSON·인물 단서·예외 메시지를 로그에 복사하지 않는다.
            log.warn("사람별 이력에서 인물 연결 JSON이 잘못된 회의록을 건너뜁니다. noteId=mn_{}",note.getId());
            return false;
        }
    }

    private boolean contains(String json,String personId) throws JsonProcessingException {
        if(json==null) return false;
        JsonNode rows=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
        if(rows==null || !rows.isArray()) throw new IllegalArgumentException();
        boolean found=false;
        for(JsonNode row:rows) {
            if(!row.isObject()) throw new IllegalArgumentException();
            JsonNode id=row.get("personId");
            if(id!=null && !id.isNull() && !id.isTextual()) throw new IllegalArgumentException();
            found |= id!=null && id.isTextual() && personId.equals(id.textValue());
        }
        return found;
    }
}
