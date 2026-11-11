package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.entity.UserStatus;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PersonRepository people = mock(PersonRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PersonService service = new PersonService(people, users, mapper);
    private final User user = new User("synthetic@people.example", "가상사용자_001", "unused", "USER", UserStatus.ACTIVE);

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        AtomicLong ids = new AtomicLong(16L);
        when(people.save(any(PersonEntry.class))).thenAnswer(call -> {
            PersonEntry entry = call.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", ids.incrementAndGet());
            return entry;
        });
    }

    static String body() {
        return """
                {"name":"가상인물_0001", "nameVariants":["Synthetic Person 0001","가상인물_0001 부장님"],
                 "company":"가상회사_001", "department":null, "title":"부장",
                 "emails":["synthetic@people.example"],"phones":["000-0000-0001"],
                 "channels":["EMAIL","MEETING"],
                 "companyHistory":[{"company":"가상회사_이전","title":"과장","until":"2025-12-31"}],
                 "photoAvailable":false,"isSynthetic":true}
                """;
    }

    @Test void createsOnlyOnExplicitRequestWithServerIdAndAuditAndValidContract() throws Exception {
        JsonNode input = mapper.readTree(body());
        JsonNode original = input.deepCopy();
        LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        PersonDocument result = service.create(input, 7L, 70L);
        assertThat(input).isEqualTo(original);
        assertThat(result.personId()).isEqualTo("p_17");
        assertThat(result.nameVariants()).containsExactly("Synthetic Person 0001", "가상인물_0001 부장님");
        JsonNode output = mapper.valueToTree(result);
        assertThat(ContractSchemaValidator.load("person.schema.json").validate(output)).isEmpty();
        assertThat(output.has("userId")).isFalse();
        assertThat(output.has("createdAt")).isFalse();
        assertThat(output.get("isSynthetic").isBoolean()).isTrue();
        ArgumentCaptor<PersonEntry> saved = ArgumentCaptor.forClass(PersonEntry.class);
        verify(people).save(saved.capture());
        PersonEntry row = saved.getValue();
        assertThat(ReflectionTestUtils.getField(row, "user")).isSameAs(user);
        assertThat(row.getCreatedBy()).isEqualTo(70L);
        assertThat(row.getCreatedAt()).isBetween(before, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        assertThat((String) ReflectionTestUtils.getField(row, "searchText"))
                .contains("synthetic person 0001", "synthetic@people.example", "가상회사_이전");
        verify(people, never()).findAll();
    }

    @Test void absentFieldsStayUnknownAndOptionalPhotoIsNotInvented() throws Exception {
        PersonDocument result = service.create(mapper.readTree("{\"name\":\"가상인물_누락\",\"isSynthetic\":true}"), 7L, 7L);
        assertThat(result.company()).isNull();
        assertThat(result.department()).isNull();
        assertThat(result.photoAvailable()).isNull();
        assertThat(result.emails()).isEmpty();
        assertThat(result.companyHistory()).isEmpty();
        JsonNode output = mapper.valueToTree(result);
        assertThat(output.has("photoAvailable")).isFalse();
        assertThat(ContractSchemaValidator.load("person.schema.json").validate(output)).isEmpty();
    }

    @Test void homonymsAndSharedContactsCreateSeparatePeopleWithoutMerging() throws Exception {
        PersonDocument first = service.create(mapper.readTree(body()), 7L, 7L);
        PersonDocument second = service.create(mapper.readTree(body()), 7L, 7L);
        assertThat(first.personId()).isNotEqualTo(second.personId());
        verify(people, times(2)).save(any());
        verifyNoMoreInteractions(people);
    }

    @Test void detailAlwaysUsesOwnerAndDoesNotExposeAnotherUsersPerson() throws Exception {
        PersonEntry entry = new PersonEntry(user, mapper.writeValueAsString(PersonRequest.parse(mapper.readTree(body()))), "", 7L, LocalDateTime.now());
        ReflectionTestUtils.setField(entry, "id", 17L);
        when(people.findByIdAndUserId(17L, 7L)).thenReturn(Optional.of(entry));
        assertThat(service.get("p_17", 7L).personId()).isEqualTo("p_17");
        when(people.findByIdAndUserId(17L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("p_17", 8L)).isInstanceOf(NoSuchElementException.class);
        verify(people, never()).findById(anyLong());
    }

    @Test void listAndSearchAreOwnerScopedPagedAndEscapeSqlWildcards() {
        when(people.searchOwned(anyLong(), nullable(String.class), any())).thenReturn(List.of());
        assertThat(service.list("  SYNTHETIC_100%!  ", 2, 25, 7L)).isEmpty();
        verify(people).searchOwned(7L, "%synthetic!_100!%!!%", PageRequest.of(2, 25));
        assertThat(service.list("  ", 0, 50, 8L)).isEmpty();
        verify(people).searchOwned(8L, null, PageRequest.of(0, 50));
        verify(people, never()).findAll();
        verify(people, never()).save(any());
    }

    @ParameterizedTest @NullSource @ValueSource(strings={"p_0", "p_001", "p_-1", "17", "p_999999999999999999999999"})
    void malformedIdsDoNotReachStorage(String id) {
        assertThatThrownBy(() -> service.get(id, 7L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(people);
    }

    @Test void paginationAndSearchLengthAreBoundedBeforeQuerying() {
        for (int[] args : new int[][]{{-1,50},{0,0},{0,101},{1000001,50}})
            assertThatThrownBy(() -> service.list(null,args[0],args[1],7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list("가".repeat(201),0,50,7L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(people);
    }


    @Test void resolutionSnapshotIncludesMoreThanOneUiPageAndOnlyTheRequestedOwner() throws Exception {
        String document=mapper.writeValueAsString(PersonRequest.parse(mapper.readTree(body())));
        List<PersonEntry> rows=new java.util.ArrayList<>();
        for(long id=1;id<=120;id++) {
            PersonEntry row=new PersonEntry(user,document,"",7L,LocalDateTime.now());
            ReflectionTestUtils.setField(row,"id",id); rows.add(row);
        }
        when(people.findByUserIdOrderByIdAsc(7L)).thenReturn(rows);
        when(people.findByUserIdOrderByIdAsc(8L)).thenReturn(List.of());
        assertThat(service.forResolution(7L)).hasSize(120).last().extracting(PersonDocument::personId).isEqualTo("p_120");
        assertThat(service.forResolution(8L)).isEmpty();
        assertThatThrownBy(() -> service.forResolution(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verify(people,never()).findAll();
        verify(people,never()).searchOwned(anyLong(),nullable(String.class),any());
    }

    @Test void missingAuthenticationAndMissingUserCannotSave() throws Exception {
        JsonNode input = mapper.readTree(body());
        assertThatThrownBy(() -> service.create(input, null, 7L)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> service.create(input, 7L, null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> service.get("p_17", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> service.list(null,0,50,null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> service.create(input, 99L, 99L)).isInstanceOf(NoSuchElementException.class);
        verify(people, never()).save(any());
    }
}
