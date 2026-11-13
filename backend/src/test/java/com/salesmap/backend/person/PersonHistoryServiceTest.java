package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.minutes.dto.MeetingNoteDocument;
import com.salesmap.backend.minutes.dto.MeetingNoteResponse;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.minutes.service.MeetingNoteService;
import com.salesmap.backend.source.entity.SourceStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PersonHistoryServiceTest {
    final PersonService people=mock(PersonService.class);
    final MeetingNoteRepository notes=mock(MeetingNoteRepository.class);
    final MeetingNoteService meetings=mock(MeetingNoteService.class);
    final ObjectMapper mapper=new ObjectMapper();
    final PersonHistoryService history=new PersonHistoryService(people,notes,meetings,mapper);

    MeetingNote note(long id,String participants,String perPerson) {
        MeetingNote note=mock(MeetingNote.class,RETURNS_DEEP_STUBS);
        when(note.getId()).thenReturn(id);when(note.getUser().getId()).thenReturn(7L);
        when(note.getSource().getUser().getId()).thenReturn(7L);
        when(note.getSource().getStatus()).thenReturn(SourceStatus.CREATED);
        when(note.getStatus()).thenReturn(MeetingNoteStatus.CONFIRMED);when(note.getOccurredAt()).thenReturn(null);
        when(note.getParticipantsJson()).thenReturn(participants);when(note.getPerPersonJson()).thenReturn(perPerson);
        return note;
    }
    MeetingNoteDocument document(long id) {
        var item=new MeetingNoteDocument.Item("가상 미팅 논의","가상인물_0001과 논의함");
        return new MeetingNoteDocument("mn_"+id,List.of(new MeetingNoteDocument.SourceRef("src_101","MEETING_NOTE")),
                null,null,"FULL",List.of(new MeetingNoteDocument.Participant("가상인물_0001","ir_owned","p_17")),
                new MeetingNoteDocument.Fields(List.of(item),List.of(),List.of(),List.of(),List.of()),
                null,List.of(),null,"CONFIRMED","RULE",null);
    }
    void returned(long id) {
        when(meetings.getMeetingNote("mn_"+id,7L)).thenReturn(new MeetingNoteResponse(document(id),null,null,null,null,null,null,null,null));
    }
    @Test void onlyExactPersonIdsInOwnedVisibleConfirmedNotesAreHistory() {
        var exact=note(1,"[{\"personId\":\"p_17\"}]",null);returned(1);
        var homonym=note(2,"[{\"displayName\":\"가상인물_0001\",\"personId\":null}]",null);
        var foreign=note(3,"[{\"personId\":\"p_17\"}]",null);when(foreign.getUser().getId()).thenReturn(8L);
        var sourceForeign=note(4,"[{\"personId\":\"p_17\"}]",null);when(sourceForeign.getSource().getUser().getId()).thenReturn(8L);
        var draft=note(5,"[{\"personId\":\"p_17\"}]",null);when(draft.getStatus()).thenReturn(MeetingNoteStatus.DRAFT);
        var archived=note(6,"[{\"personId\":\"p_17\"}]",null);when(archived.isArchived()).thenReturn(true);
        var deleted=note(7,"[{\"personId\":\"p_17\"}]",null);when(deleted.getSource().getStatus()).thenReturn(SourceStatus.DELETED);
        when(notes.findByUserId(7L)).thenReturn(List.of(exact,homonym,foreign,sourceForeign,draft,archived,deleted));
        assertThat(history.list("p_17",0,20,7L)).extracting(MeetingNoteDocument::noteId).containsExactly("mn_1");
        verify(meetings).getMeetingNote("mn_1",7L);verifyNoMoreInteractions(meetings);
        verify(people).get("p_17",7L);verify(notes,never()).save(any());
    }
    @Test void lightExactIdDeduplicatesAndOrdersByMeetingDateWithUnknownDatesLast() {
        var light=note(2,"[{\"personId\":\"p_17\"}]","[{\"personId\":\"p_17\"}]");
        var older=note(9,"[]","[{\"personId\":\"p_17\"}]");
        var unknown=note(20,"[{\"personId\":\"p_17\"}]",null);
        when(light.getOccurredAt()).thenReturn(LocalDateTime.of(2026,11,12,10,0));
        when(older.getOccurredAt()).thenReturn(LocalDateTime.of(2026,11,1,10,0));
        when(notes.findByUserId(7L)).thenReturn(List.of(unknown,older,light));returned(2);returned(9);returned(20);
        assertThat(history.list("p_17",0,1,7L)).extracting(MeetingNoteDocument::noteId).containsExactly("mn_2");
        assertThat(history.list("p_17",1,1,7L)).extracting(MeetingNoteDocument::noteId).containsExactly("mn_9");
        assertThat(history.list("p_17",2,1,7L)).extracting(MeetingNoteDocument::noteId).containsExactly("mn_20");
    }
    @Test void personAuthorizationPrecedesAnyMeetingReadAndBadPagesAreRejected() {
        doThrow(new NoSuchElementException()).when(people).get("p_18",7L);
        assertThatThrownBy(() -> history.list("p_18",0,20,7L)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> history.list("p_17",-1,20,7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.list("p_17",0,101,7L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notes,meetings);
    }
    @Test void malformedStoredLinksFailRatherThanPretendThereIsNoHistory() {
        var broken=note(1,"{broken",null);
        when(notes.findByUserId(7L)).thenReturn(List.of(broken));
        assertThatThrownBy(() -> history.list("p_17",0,20,7L)).isInstanceOf(IllegalStateException.class);
    }
    @Test void outputUsesExistingMeetingNoteContractWithoutAddingIdentityFields() throws Exception {
        var exact=note(1,"[{\"personId\":\"p_17\"}]",null);
        when(notes.findByUserId(7L)).thenReturn(List.of(exact));returned(1);
        var result=history.list("p_17",0,20,7L).get(0);
        assertThat(ContractSchemaValidator.load("meeting_note.schema.json").validate(mapper.valueToTree(result))).isEmpty();
    }
}
