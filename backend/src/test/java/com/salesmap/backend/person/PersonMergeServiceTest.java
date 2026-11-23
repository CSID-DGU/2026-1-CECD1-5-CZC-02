package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PersonMergeServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    final PersonRepository rows=mock(PersonRepository.class);
    final PersonMergeRepository audit=mock(PersonMergeRepository.class);
    final PersonService people=new PersonService(rows,mock(UserRepository.class),mapper);
    final PersonMergeService merges=new PersonMergeService(rows,audit,people,mapper);
    PersonEntry source,target;
    PersonMergeEntry record;
    @BeforeEach void setup() throws Exception {
        User user=mock(User.class);
        var original=PersonRequest.parse(mapper.readTree(PersonServiceTest.body()));
        var other=PersonRequest.parse(mapper.readTree("{\"name\":\"가상인물_별칭\",\"company\":\"가상회사_다른소속\",\"emails\":[\"second@fake.example\"],\"isSynthetic\":true}"));
        source=new PersonEntry(user,mapper.writeValueAsString(other),PersonService.searchText(other),7L,LocalDateTime.now());
        target=new PersonEntry(user,mapper.writeValueAsString(original),PersonService.searchText(original),7L,LocalDateTime.now());
        ReflectionTestUtils.setField(source,"id",18L);ReflectionTestUtils.setField(target,"id",17L);
        when(rows.lockOwned(17L,7L)).thenReturn(Optional.of(target));when(rows.lockOwned(18L,7L)).thenReturn(Optional.of(source));
        when(rows.findByIdAndUserId(17L,7L)).thenReturn(Optional.of(target));when(rows.findByIdAndUserId(18L,7L)).thenReturn(Optional.of(source));
        when(rows.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(target,source));
        when(rows.findByUserIdAndMergedIntoIdOrderByIdAsc(7L,17L)).thenAnswer(call -> source.getMergedIntoId()==null ? List.of() : List.of(source));
        when(audit.saveAndFlush(any())).thenAnswer(call -> {
            record=call.getArgument(0);ReflectionTestUtils.setField(record,"id",31L);return record;
        });
        PersonMergeRepository.Coordinates location=mock(PersonMergeRepository.Coordinates.class);
        when(location.getSourceId()).thenReturn(18L);when(location.getTargetId()).thenReturn(17L);
        when(audit.coordinates(31L,7L)).thenReturn(Optional.of(location));
        when(audit.lockOwned(31L,7L)).thenAnswer(call -> Optional.ofNullable(record));
    }
    ObjectNode body(){return mapper.createObjectNode().put("sourcePersonId","p_18").put("confirmed",true);}
    ObjectNode undoBody(){return mapper.createObjectNode().put("confirmed",true);}
    @Test void mergedCompanyIsAClueForIdentityOnlyAndDisappearsAfterUndo() {
        var result=merges.merge("p_17",body(),7L,70L);
        PersonDocument forIdentity=people.forResolution(7L).get(0);
        assertThat(forIdentity.companyHistory()).anyMatch(h -> h.company().equals("가상회사_다른소속") && h.title()==null && h.until()==null);
        assertThat(people.get("p_17",7L).companyHistory()).noneMatch(h -> h.company().equals("가상회사_다른소속"));
        merges.undo("p_17",result.mergeId(),undoBody(),7L,70L);
        assertThat(people.forResolution(7L)).hasSize(2).allSatisfy(p -> assertThat(p.companyHistory()).noneMatch(h -> h.company().equals("가상회사_다른소속")));
    }
    @Test void explicitMergeKeepsOriginalsUnionsRegisteredCluesAndAuditWithoutInventingCompanyHistory() throws Exception {
        String sourceOriginal=source.getDocument(),targetOriginal=target.getDocument();
        var result=merges.merge("p_17",body(),7L,70L);
        assertThat(result.mergeId()).isEqualTo("pm_31");assertThat(result.mergedBy()).isEqualTo(70L);
        assertThat(result.mergedAt()).isNotNull();assertThat(result.active()).isTrue();
        assertThat(source.getMergedIntoId()).isEqualTo(17L);
        assertThat(source.getDocument()).isEqualTo(sourceOriginal);assertThat(target.getDocument()).isEqualTo(targetOriginal);
        PersonDocument combined=people.get("p_17",7L);
        assertThat(combined.name()).isEqualTo("가상인물_0001");assertThat(combined.company()).isEqualTo("가상회사_001");
        assertThat(combined.nameVariants()).contains("가상인물_별칭");assertThat(combined.emails()).contains("second@fake.example");
        assertThat(combined.companyHistory()).noneMatch(h -> h.company().equals("가상회사_다른소속"));
        assertThat(ContractSchemaValidator.load("person.schema.json").validate(mapper.valueToTree(combined))).isEmpty();
        assertThat(people.get("p_18",7L)).isEqualTo(combined);
        assertThat(people.forResolution(7L)).singleElement().satisfies(p -> {assertThat(p.personId()).isEqualTo(combined.personId());assertThat(p.emails()).isEqualTo(combined.emails());assertThat(p.nameVariants()).isEqualTo(combined.nameVariants());});
        assertThat(people.historyIds("p_17",7L)).containsExactlyInAnyOrder("p_17","p_18");
        assertThat((String)ReflectionTestUtils.getField(target,"searchText")).contains("second@fake.example");
    }
    @Test void undoRestoresSeparateRosterAndSearchAndRecordsRealActor() {
        String original=target.getDocument();merges.merge("p_17",body(),7L,70L);
        var result=merges.undo("p_17","pm_31",undoBody(),7L,71L);
        assertThat(result.active()).isFalse();assertThat(result.undoneBy()).isEqualTo(71L);assertThat(result.undoneAt()).isNotNull();
        assertThat(source.getMergedIntoId()).isNull();assertThat(target.getDocument()).isEqualTo(original);
        assertThat(people.forResolution(7L)).hasSize(2);
        assertThat(people.historyIds("p_17",7L)).containsExactly("p_17");
        assertThat((String)ReflectionTestUtils.getField(target,"searchText")).doesNotContain("second@fake.example");
        assertThatThrownBy(() -> merges.undo("p_17","pm_31",undoBody(),7L,71L)).isInstanceOf(IdentitySelectionConflictException.class);
    }
    @Test void missingConfirmationMalformedInputAndSamePersonNeverMutate() {
        for(String input:List.of("{}","{\"sourcePersonId\":\"p_18\",\"confirmed\":false}","{\"sourcePersonId\":\"p_18\",\"confirmed\":true,\"actor\":99}"))
            assertThatThrownBy(() -> merges.merge("p_17",mapper.readTree(input),7L,70L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> merges.merge("p_17",body().put("sourcePersonId","p_17"),7L,70L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> merges.merge("p_17",body(),null,70L)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> merges.undo("p_17","pm_31",mapper.createObjectNode(),7L,70L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rows,audit);
    }
    @Test void foreignPeopleAndForeignAuditCannotBeAccessed() {
        when(rows.lockOwned(18L,7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> merges.merge("p_17",body(),7L,70L)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> merges.undo("p_17","pm_31",undoBody(),8L,80L)).isInstanceOf(NoSuchElementException.class);
        assertThat(source.getMergedIntoId()).isNull();verify(audit,never()).saveAndFlush(any());
    }
    @Test void duplicateReverseAndNestedMergesCannotCreateCycles() {
        merges.merge("p_17",body(),7L,70L);
        assertThatThrownBy(() -> merges.merge("p_17",body(),7L,70L)).isInstanceOf(IdentitySelectionConflictException.class);
        assertThatThrownBy(() -> merges.merge("p_18",body().put("sourcePersonId","p_17"),7L,70L)).isInstanceOf(IdentitySelectionConflictException.class);
        source.mergeInto(null);
        when(rows.findByUserIdAndMergedIntoIdOrderByIdAsc(7L,18L)).thenReturn(List.of(target));
        assertThatThrownBy(() -> merges.merge("p_17",body(),7L,70L)).isInstanceOf(IdentitySelectionConflictException.class);
        verify(audit,times(1)).saveAndFlush(any());
    }
}
