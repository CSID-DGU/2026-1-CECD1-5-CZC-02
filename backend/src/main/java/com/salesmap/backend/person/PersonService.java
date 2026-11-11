package com.salesmap.backend.person;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class PersonService {
    private final PersonRepository people;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public PersonService(PersonRepository people, UserRepository users, ObjectMapper mapper) {
        this.people = people; this.users = users; this.mapper = mapper;
    }

    /** 직접 등록 요청만 처리한다. AI 호출·자동 샘플 등록·기존 인물 갱신·자동 병합은 없다. */
    @Transactional
    public PersonDocument create(JsonNode body, Long owner, Long actor) {
        requireOwner(owner);
        requireOwner(actor);
        PersonDocument person = PersonRequest.parse(body);
        User user = users.findById(owner).orElseThrow(() -> new NoSuchElementException("사용자가 없습니다."));
        PersonEntry entry = people.save(new PersonEntry(user, write(person), searchText(person), actor,
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))));
        return document(entry);
    }

    @Transactional(readOnly = true)
    public PersonDocument get(String id, Long owner) {
        requireOwner(owner);
        long pk = parseId(id);
        return document(people.findByIdAndUserId(pk, owner)
                .orElseThrow(() -> new NoSuchElementException("인물이 없거나 접근할 수 없습니다.")));
    }

    /** 한 페이지에 최대 100명. 검색과 정렬은 DB에서 수행하며 소유자 조건을 항상 포함한다. */
    @Transactional(readOnly = true)
    public List<PersonDocument> list(String query, int page, int size, Long owner) {
        requireOwner(owner);
        if (page < 0 || page > 1000000 || size < 1 || size > 100)
            throw new IllegalArgumentException("page는 0~1000000, size는 1~100이어야 합니다.");
        if (query != null && query.length() > 200) throw new IllegalArgumentException("검색어는 200자 이하여야 합니다.");
        String normalized = query == null ? "" : normalize(query);
        String pattern = normalized.isBlank() ? null : "%" + normalized.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        return people.searchOwned(owner, pattern, PageRequest.of(page, size)).stream().map(this::document).toList();
    }

    /** 읽기 트랜잭션에서 DTO 스냅샷을 만든다. 이후 식별 HTTP 호출 동안 DB 트랜잭션을 잡지 않는다. */
    @Transactional(readOnly = true)
    public List<PersonDocument> forResolution(Long owner) {
        requireOwner(owner);
        return people.findByUserIdOrderByIdAsc(owner).stream().map(this::document).toList();
    }

    private static void requireOwner(Long owner) {
        if (owner == null || owner < 1) throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
    }

    private static long parseId(String id) {
        if (id == null || !id.matches("p_[1-9][0-9]*")) throw new IllegalArgumentException("p_<DB PK> ID가 필요합니다.");
        try { return Long.parseLong(id.substring(2)); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("유효하지 않은 인물 ID입니다."); }
    }

    private String write(PersonDocument person) {
        try { return mapper.writeValueAsString(person); }
        catch (JsonProcessingException error) { throw new IllegalStateException("인물 저장 형식 오류", error); }
    }

    private PersonDocument document(PersonEntry entry) {
        try { return mapper.readValue(entry.getDocument(), PersonDocument.class).withId(entry.getId()); }
        catch (JsonProcessingException error) { throw new IllegalStateException("인물 저장 형식 오류", error); }
    }

    private static String searchText(PersonDocument person) {
        List<String> fields = new ArrayList<>();
        fields.add(person.name()); fields.add(person.company());
        fields.addAll(person.nameVariants()); fields.addAll(person.emails()); fields.addAll(person.phones());
        person.companyHistory().forEach(history -> fields.add(history.company()));
        // 필드 사이 검색어가 이어 붙지 않도록 경계를 남긴다. 관측 원문은 document에 그대로 보존한다.
        return fields.stream().filter(value -> value != null && !value.isBlank())
                .map(PersonService::normalize).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
