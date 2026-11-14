package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.minutes.client.RevisionClient;
import com.salesmap.backend.minutes.client.RevisionRecord;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.entity.MeetingNoteStatus;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PersonIdentityServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    final IdentityResolutionRepository rows=mock(IdentityResolutionRepository.class);
    final PersonService people=mock(PersonService.class);
    final SourceRepository sources=mock(SourceRepository.class);
    final MeetingNoteRepository notes=mock(MeetingNoteRepository.class);
    final RevisionClient revisions=mock(RevisionClient.class);
    final User user=mock(User.class);
    final Source source=mock(Source.class);
    final MeetingNote note=mock(MeetingNote.class);
    PersonIdentityService service;
    IdentityResolutionEntry row;
    ObjectNode proposal;
    PersonDocument person;

    @BeforeEach void setup() throws Exception {
        when(user.getId()).thenReturn(7L);when(source.getId()).thenReturn(101L);when(source.getUser()).thenReturn(user);
        when(source.getStatus()).thenReturn(SourceStatus.CREATED);
        when(note.getUser()).thenReturn(user);when(note.getSource()).thenReturn(source);when(note.getStatus()).thenReturn(MeetingNoteStatus.DRAFT);
        when(notes.findById(21L)).thenReturn(Optional.of(note));when(sources.findById(101L)).thenReturn(Optional.of(source));
        proposal=(ObjectNode)mapper.readTree("""
                {"resolutionId":"ir_owned","mention":{"name":"가상인물_0001","channel":"MEETING","sourceRef":"src_101"},
                "decision":"CANDIDATES","personId":null,"candidates":[{"personId":"p_17","score":null,"reasons":["이름 일치"]}],
                "decidedBy":"RULE","modelVersion":"identity-rule-0.2-d022"}
                """);
        row=new IdentityResolutionEntry(user,"src_101",0,"ir_owned",proposal.toString(),LocalDateTime.now());
        ReflectionTestUtils.setField(row,"version",0L);
        when(rows.lockOwned("ir_owned",7L)).thenReturn(Optional.of(row));
        when(rows.findByUserIdAndSourceRefOrderByMentionIndexAsc(7L,"src_101")).thenReturn(List.of(row));
        when(rows.saveAndFlush(any())).thenAnswer(call -> {IdentityResolutionEntry saved=call.getArgument(0);
            ReflectionTestUtils.setField(saved,"version",saved.getVersion()+1);return saved;});
        person=PersonRequest.parse(mapper.readTree(PersonServiceTest.body())).withId(17L);
        when(people.get("p_17",7L)).thenReturn(person);
        when(people.get("p_18",7L)).thenReturn(person.withId(18L));
        when(people.create(any(),eq(7L),eq(70L))).thenReturn(person.withId(19L));
        service=new PersonIdentityService(rows,people,sources,notes,revisions,mapper);
    }
    JsonNode choose(String id){return mapper.createObjectNode().put("personId",id);}
    IdentityReviewItem select(JsonNode body){return service.select("mn_21","ir_owned",body,"\"irv-0\"",7L,70L);}
    RevisionRecord revision(){ArgumentCaptor<RevisionRecord> captured=ArgumentCaptor.forClass(RevisionRecord.class);verify(revisions).record(captured.capture(),eq(7L));return captured.getValue();}

    @Test void candidateSelectionPersistsHumanAndRevisionWithoutChangingBNote() throws Exception {
        IdentityReviewItem saved=select(choose("p_17"));
        assertThat(saved.resolution().path("decidedBy").asText()).isEqualTo("HUMAN");
        assertThat(saved.resolution().path("personId").asText()).isEqualTo("p_17");
        assertThat(saved.etag()).isEqualTo("\"irv-1\"");
        assertThat(row.getSelectedBy()).isEqualTo(70L);
        assertThat(row.getProposal()).isEqualTo(proposal.toString());
        assertThat(ContractSchemaValidator.load("identity_resolution.schema.json").validate(saved.resolution())).isEmpty();
        RevisionRecord revision=revision();assertThat(revision.action()).isEqualTo("SELECTED_CANDIDATE");
        assertThat(revision.screen()).isEqualTo("IDENTITY_CANDIDATE");assertThat(revision.targetType()).isEqualTo("IDENTITY_RESOLUTION");
        assertThat(revision.targetId()).isEqualTo("ir_owned");assertThat(revision.proposedBy()).isEqualTo("RULE");
        ObjectNode record=mapper.valueToTree(revision);record.put("revisionId","rv_1");
        assertThat(ContractSchemaValidator.load("revision.schema.json").validate(record)).isEmpty();
        verify(notes,never()).save(any());verify(people,never()).create(any(),anyLong(),anyLong());
    }
    @Test void modelProposalIsStoredAndHumanSelectionKeepsModelAttribution() {
        proposal.put("decidedBy","MODEL").put("modelVersion","identity-logistic-v1");
        ((ObjectNode)proposal.path("candidates").get(0)).put("score",0.72);
        when(rows.findByUserIdAndSourceRefOrderByMentionIndexAsc(7L,"src_101")).thenReturn(List.of());
        service.storeProposals("src_101",List.of(proposal),7L);verify(rows).save(any());
        row=new IdentityResolutionEntry(user,"src_101",0,"ir_owned",proposal.toString(),LocalDateTime.now());
        ReflectionTestUtils.setField(row,"version",0L);when(rows.lockOwned("ir_owned",7L)).thenReturn(Optional.of(row));
        var saved=select(choose("p_17"));
        assertThat(saved.resolution().path("decidedBy").asText()).isEqualTo("HUMAN");
        assertThat(saved.resolution().path("candidates").get(0).path("score").isNull()).isTrue();
        assertThat(revision().proposedBy()).isEqualTo("MODEL");
        assertThat(row.getProposal()).isEqualTo(proposal.toString());
    }

    @Test void directSearchSelectionRecordsEditAndNewRegistrationRecordsCreatedNew(){
        assertThat(select(choose("p_18")).resolution().path("personId").asText()).isEqualTo("p_18");
        assertThat(revision().action()).isEqualTo("EDITED");
    }
    @Test void newPersonAndRevisionUseAuthenticatedOwnerAndActor() throws Exception {
        ObjectNode body=mapper.createObjectNode();body.set("newPerson",mapper.readTree(PersonServiceTest.body()));
        assertThat(select(body).resolution().path("personId").asText()).isEqualTo("p_19");
        verify(people).create(body.get("newPerson"),7L,70L);assertThat(revision().action()).isEqualTo("CREATED_NEW");
    }
    @Test void acceptingOriginalConfirmedPersonRecordsAcceptance(){
        proposal.put("decision","CONFIRMED").put("personId","p_17");
        row=new IdentityResolutionEntry(user,"src_101",0,"ir_owned",proposal.toString(),LocalDateTime.now());
        ReflectionTestUtils.setField(row,"version",0L);when(rows.lockOwned("ir_owned",7L)).thenReturn(Optional.of(row));
        select(choose("p_17"));assertThat(revision().action()).isEqualTo("ACCEPTED");
    }
    @Test void staleVersionBlocksRegistrationAndRevisionBeforeAnyWrites(){
        assertThatThrownBy(() -> service.select("mn_21","ir_owned",mapper.createObjectNode().putObject("newPerson"),"old",7L,70L))
                .isInstanceOf(IdentitySelectionConflictException.class);
        verifyNoInteractions(people,revisions);verify(rows,never()).saveAndFlush(any());
    }
    @Test void staleRetryCannotCreateDuplicatePeopleOrRevisions(){
        select(choose("p_17"));
        assertThatThrownBy(() -> service.select("mn_21","ir_owned",choose("p_18"),"\"irv-0\"",7L,70L))
                .isInstanceOf(IdentitySelectionConflictException.class);
        verify(revisions,times(1)).record(any(),anyLong());verify(people,never()).get("p_18",7L);
    }
    @Test void explicitVersionedChangeIsAllowedAndRecordsEdit(){
        select(choose("p_17"));
        var changed=service.select("mn_21","ir_owned",choose("p_18"),"\"irv-1\"",7L,70L);
        assertThat(changed.resolution().path("personId").asText()).isEqualTo("p_18");
        ArgumentCaptor<RevisionRecord> captured=ArgumentCaptor.forClass(RevisionRecord.class);
        verify(revisions,times(2)).record(captured.capture(),eq(7L));
        assertThat(captured.getAllValues().get(1).action()).isEqualTo("EDITED");
        assertThat(row.getProposal()).isEqualTo(proposal.toString());
    }

    @Test void foreignNoteOrResolutionCannotReadOrSelect(){
        assertThatThrownBy(() -> service.list("mn_21",8L)).isInstanceOf(NoSuchElementException.class);
        when(rows.lockOwned("ir_owned",7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> select(choose("p_17"))).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(people,revisions);
    }
    @Test void anotherNotesResolutionIsRejected(){
        ReflectionTestUtils.setField(row,"sourceRef","src_999");
        assertThatThrownBy(() -> select(choose("p_17"))).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(people,revisions);
    }
    @Test void foreignPersonCannotBeSelected(){
        when(people.get("p_999",7L)).thenThrow(new NoSuchElementException("인물 없음"));
        assertThatThrownBy(() -> select(choose("p_999"))).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(revisions);verify(rows,never()).saveAndFlush(any());
    }
    @Test void archivedAndConfirmedNotesAreReadOnly(){
        when(note.isArchived()).thenReturn(true);
        assertThatThrownBy(() -> select(choose("p_17"))).isInstanceOf(IdentitySelectionConflictException.class);
        when(note.isArchived()).thenReturn(false);when(note.getStatus()).thenReturn(MeetingNoteStatus.CONFIRMED);
        assertThatThrownBy(() -> select(choose("p_17"))).isInstanceOf(IdentitySelectionConflictException.class);
        verifyNoInteractions(people,revisions);
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","{\"personId\":null}","{\"personId\":\"p_17\",\"ownerId\":8}","{\"personId\":\"p_17\",\"newPerson\":{}}"})
    void invalidOrSpoofedSelectionDoesNotWrite(String input) throws Exception {
        assertThatThrownBy(() -> select(mapper.readTree(input))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(people,revisions);verify(rows,never()).saveAndFlush(any());
    }
    @Test void storedSelectionsSurviveReloadAndAreNotReplacedByNewAiResponse(){
        select(choose("p_17"));
        assertThat(service.list("mn_21",7L).get(0).resolution().path("decidedBy").asText()).isEqualTo("HUMAN");
        ObjectNode rerun=proposal.deepCopy().put("resolutionId","ir_rerun");
        assertThat(service.storeProposals("src_101",List.of(rerun),7L).get(0).path("resolutionId").asText()).isEqualTo("ir_owned");
        verify(rows,never()).save(any());
    }
    @Test void newProposalsAreStoredOnlyForOwnedSource(){
        when(rows.findByUserIdAndSourceRefOrderByMentionIndexAsc(7L,"src_101")).thenReturn(List.of());
        service.storeProposals("src_101",List.of(proposal),7L);
        verify(rows).save(any());
        assertThatThrownBy(() -> service.storeProposals("src_101",List.of(proposal),8L)).isInstanceOf(NoSuchElementException.class);
    }
    @Test void changedMentionCannotOverwriteOriginalProposal(){
        ObjectNode changed=proposal.deepCopy();((ObjectNode)changed.get("mention")).put("name","가상다른인물");
        assertThatThrownBy(() -> service.storeProposals("src_101",List.of(changed),7L)).isInstanceOf(IdentitySelectionConflictException.class);
        verify(rows,never()).save(any());
    }
    @Test void revisionFailureRollsBackTransactionalSelectionBoundary(){
        doThrow(new IllegalStateException("기록 저장 실패")).when(revisions).record(any(),anyLong());
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);TransactionStatus status=mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(status);
        TransactionInterceptor interceptor=new TransactionInterceptor();interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());interceptor.afterPropertiesSet();
        ProxyFactory factory=new ProxyFactory(service);factory.setProxyTargetClass(true);factory.addAdvice(interceptor);
        PersonIdentityService proxied=(PersonIdentityService)factory.getProxy();
        assertThatThrownBy(() -> proxied.select("mn_21","ir_owned",choose("p_17"),"\"irv-0\"",7L,70L)).isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(status);verify(manager,never()).commit(any());
        assertThat(row.getDocument()).isEqualTo(proposal.toString());verify(rows,never()).saveAndFlush(any());
    }
}
