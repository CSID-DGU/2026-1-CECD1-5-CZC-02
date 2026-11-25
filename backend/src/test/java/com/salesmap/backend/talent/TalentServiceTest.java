package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.minutes.ContractSchemaValidator;
import com.salesmap.backend.revision.dto.RevisionCreateRequest;
import com.salesmap.backend.revision.service.RevisionService;
import com.salesmap.backend.talent.ResumeExtractionClient.Extraction;
import com.salesmap.backend.talent.ResumeExtractionClient.ExtractionException;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.support.ExecutorServiceAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 가짜 이력서·가짜 AI 응답만 쓴다 (네트워크·DB 없음). */
class TalentServiceTest {
    static final Long OWNER = 7L;
    final ObjectMapper mapper = new ObjectMapper();
    final TalentProfileRepository profiles = mock(TalentProfileRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final ResumeExtractionClient extractor = mock(ResumeExtractionClient.class);
    final RevisionService revisions = mock(RevisionService.class);
    final Map<Long, TalentProfile> stored = new TreeMap<>();
    TalentService service;
    User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T05:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new TalentService(profiles, users, extractor, revisions, mapper, clock,
                new ExecutorServiceAdapter(new SyncTaskExecutor()));
        user = mock(User.class);
        when(user.getId()).thenReturn(OWNER);
        when(users.findById(OWNER)).thenReturn(Optional.of(user));
        when(profiles.save(any())).thenAnswer(call -> {
            TalentProfile profile = call.getArgument(0);
            if (profile.getId() == null) ReflectionTestUtils.setField(profile, "id", (long) stored.size() + 1);
            stored.put(profile.getId(), profile);
            return profile;
        });
        when(profiles.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
        when(profiles.findByIdAndUserId(anyLong(), eq(OWNER)))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
        when(profiles.findByUserIdOrderByIdDesc(OWNER)).thenAnswer(call -> { List<TalentProfile> rows = new ArrayList<>(stored.values()); java.util.Collections.reverse(rows); return rows; });
        when(profiles.findByUserIdAndBirthYearAndIdNot(eq(OWNER), any(), anyLong())).thenAnswer(call -> stored.values().stream()
                .filter(p -> call.getArgument(1).equals(p.getBirthYear()) && !p.getId().equals(call.getArgument(2))).toList());
        when(revisions.list(OWNER)).thenReturn(List.of());
    }

    JsonNode example(int index) throws Exception {
        return mapper.readTree(ContractSchemaValidator.contractsDir().resolve("examples/resume_profile.example.json").toFile())
                .get(index).deepCopy();
    }

    TalentViews.UploadRequest upload(String fileName) {
        return new TalentViews.UploadRequest(fileName, Base64.getEncoder().encodeToString("가짜 docx".getBytes(StandardCharsets.UTF_8)));
    }

    TalentViews.Summary uploadWith(JsonNode profile, String fallbackReason) {
        when(extractor.extract(anyString(), any(), anyString(), anyString()))
                .thenReturn(new Extraction(profile, "샘플 원문", fallbackReason == null ? "rule" : "ollama", null, fallbackReason));
        return service.upload(upload("샘플_이력서.docx"), OWNER);
    }

    @Test
    void rejectsNonDocxAndOversizedFiles() {
        assertThatThrownBy(() -> service.upload(upload("모의.doc"), OWNER)).hasMessageContaining(".docx로 다시 저장");
        assertThatThrownBy(() -> service.upload(upload("모의.hwp"), OWNER)).hasMessageContaining(".docx로 다시 저장");
        assertThatThrownBy(() -> service.upload(upload("모의.pdf"), OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upload(new TalentViews.UploadRequest("모의.docx", "%%%"), OWNER))
                .hasMessageContaining("base64");
        String big = Base64.getEncoder().encodeToString(new byte[TalentService.MAX_FILE_BYTES + 1]);
        assertThatThrownBy(() -> service.upload(new TalentViews.UploadRequest("모의.docx", big), OWNER))
                .hasMessageContaining("10MB");
        verifyNoInteractions(extractor);
    }

    @Test
    void uploadExtractsIntoDraftWithStoredId() throws Exception {
        uploadWith(example(0), null);
        verify(extractor).extract(eq("샘플_이력서.docx"), any(), eq("rp_1"), eq("src_resume_1"));
        TalentViews.Detail detail = service.get("rp_1", OWNER);
        assertThat(detail.summary().status()).isEqualTo("DRAFT");
        assertThat(detail.document().path("profileId").asText()).isEqualTo("rp_1");
        assertThat(detail.document().path("status").asText()).isEqualTo("DRAFT");
        assertThat(detail.summary().name()).isEqualTo(example(0).path("name").path("value").asText());
        assertThat(detail.summary().careerMonths()).isEqualTo(example(0).path("totalCareer").path("months").asInt());
        assertThat(detail.summary().llmFailed()).isFalse();
        assertThat(detail.sourceText()).isEqualTo("샘플 원문");
        assertThat(ContractSchemaValidator.load("resume_profile.schema.json").validate(detail.document())).isEmpty();
        verify(revisions, never()).create(any(), any());   // AI 결과만으로는 아무것도 확정·기록하지 않는다
    }

    @Test
    void failedExtractionIsShownAndLlmFallbackIsFlagged() throws Exception {
        when(extractor.extract(anyString(), any(), anyString(), anyString()))
                .thenThrow(new ExtractionException("워드(.docx)만 읽을 수 있습니다."));
        service.upload(upload("모의.docx"), OWNER);
        assertThat(service.get("rp_1", OWNER).summary().status()).isEqualTo("FAILED");
        assertThat(service.get("rp_1", OWNER).summary().errorMessage()).contains(".docx");

        reset(extractor);
        uploadWith(example(1), "timed out");
        assertThat(service.get("rp_2", OWNER).summary().llmFailed()).isTrue();
    }

    @Test
    void humanEditsRecordRevisionsAndKeepSchema() throws Exception {
        uploadWith(example(0), null);
        ObjectNode patch = mapper.createObjectNode();
        patch.put("grade", "EXPERT");
        patch.putArray("skills").add("Java").addObject().put("name", "모의툴").put("category", "TOOL");
        TalentViews.Detail detail = service.update("rp_1", patch, OWNER);

        JsonNode document = detail.document();
        assertThat(document.path("grade").path("value").asText()).isEqualTo("EXPERT");
        assertThat(document.path("grade").path("source").asText()).isEqualTo("HUMAN");
        assertThat(document.path("skills")).hasSize(2);
        assertThat(document.path("skills").get(1).path("source").asText()).isEqualTo("HUMAN");
        assertThat(ContractSchemaValidator.load("resume_profile.schema.json").validate(document)).isEmpty();

        ArgumentCaptor<RevisionCreateRequest> captor = ArgumentCaptor.forClass(RevisionCreateRequest.class);
        verify(revisions, times(2)).create(captor.capture(), eq(OWNER));
        assertThat(captor.getAllValues()).extracting(RevisionCreateRequest::fieldName).containsExactly("grade", "skills");
        RevisionCreateRequest grade = captor.getAllValues().get(0);
        assertThat(grade.screen()).isEqualTo("RESUME_REVIEW");
        assertThat(grade.targetType()).isEqualTo("RESUME_PROFILE");
        assertThat(grade.targetId()).isEqualTo("rp_1");
        assertThat(grade.action()).isEqualTo("EDITED");
        assertThat(grade.finalValue().asText()).isEqualTo("EXPERT");

        // 같은 값으로 다시 보내면 기록하지 않는다
        clearInvocations(revisions);
        service.update("rp_1", mapper.createObjectNode().put("grade", "EXPERT"), OWNER);
        verify(revisions, never()).create(any(), any());
    }

    @Test
    void rejectsComputedOrInvalidFields() throws Exception {
        uploadWith(example(0), null);
        assertThatThrownBy(() -> service.update("rp_1", mapper.createObjectNode().put("totalCareer", 10), OWNER))
                .hasMessageContaining("고칠 수 없는 칸");
        assertThatThrownBy(() -> service.update("rp_1", mapper.createObjectNode().put("birthYear", 1800), OWNER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update("rp_1", mapper.createObjectNode().put("grade", "SENIOR"), OWNER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearingNameUpdatesMissingFields() throws Exception {
        uploadWith(example(0), null);
        ObjectNode patch = mapper.createObjectNode();
        patch.putNull("name");
        JsonNode document = service.update("rp_1", patch, OWNER).document();
        assertThat(document.path("name").isNull()).isTrue();
        assertThat(document.path("missingFields").toString()).contains("\"name\"");
        assertThat(ContractSchemaValidator.load("resume_profile.schema.json").validate(document)).isEmpty();
    }

    @Test
    void confirmIsHumanOnlyAndRecordsAcceptance() throws Exception {
        uploadWith(example(0), null);
        TalentViews.Detail detail = service.confirm("rp_1", OWNER, OWNER);
        assertThat(detail.summary().status()).isEqualTo("CONFIRMED");
        assertThat(detail.document().path("status").asText()).isEqualTo("CONFIRMED");
        verify(revisions).create(argThat(r -> "ACCEPTED".equals(r.action()) && "status".equals(r.fieldName())), eq(OWNER));
        assertThatThrownBy(() -> service.confirm("rp_1", OWNER, OWNER)).hasMessageContaining("초안");
    }

    @Test
    void otherOwnersProfilesAreHidden() {
        assertThatThrownBy(() -> service.get("rp_99", OWNER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.get("p_1", OWNER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameNameAndBirthYearIsOnlySuggested() throws Exception {
        uploadWith(example(0), null);
        uploadWith(example(0), null);
        ObjectNode other = (ObjectNode) example(0);
        ((ObjectNode) other.path("birthYear")).put("value", 1970);
        uploadWith(other, null);
        TalentViews.Detail detail = service.get("rp_1", OWNER);
        assertThat(detail.duplicates()).extracting(TalentViews.DuplicateCandidate::profileId).containsExactly("rp_2");
        assertThat(service.get("rp_2", OWNER).summary().status()).isEqualTo("DRAFT");   // 자동으로 합치지 않음
    }

    @Test
    void filtersBySkillCareerIndustryAndAvailability() throws Exception {
        JsonNode card = example(0);
        uploadWith(card, null);
        String skill = card.path("skills").get(0).path("name").asText();
        int months = card.path("totalCareer").path("months").asInt();

        assertThat(service.list(OWNER, TalentFilter.of(null, null, skill, null, null, null))).hasSize(1);
        assertThat(service.list(OWNER, TalentFilter.of(null, null, skill + ",없는기술", null, null, null))).isEmpty();
        assertThat(service.list(OWNER, TalentFilter.of(null, null, null, months, null, null))).hasSize(1);
        assertThat(service.list(OWNER, TalentFilter.of(null, null, null, months + 1, null, null))).isEmpty();
        assertThat(service.list(OWNER, TalentFilter.of("CONFIRMED", null, null, null, null, null))).isEmpty();

        String available = card.path("availableFrom").path("date").asText(null);
        if (available != null) {
            LocalDate date = LocalDate.parse(available);
            assertThat(service.list(OWNER, TalentFilter.of(null, null, null, null, null, date))).hasSize(1);
            assertThat(service.list(OWNER, TalentFilter.of(null, null, null, null, null, date.minusDays(1)))).isEmpty();
        }
    }

    @Test
    void csvHasOnlyConfirmedAndEscapesFormulas() throws Exception {
        ObjectNode card = (ObjectNode) example(0);
        ((ObjectNode) card.path("name")).put("value", "=모의,인물");
        uploadWith(card, null);
        uploadWith(example(1), null);
        service.confirm("rp_1", OWNER, OWNER);
        String csv = new String(service.exportCsv(OWNER), StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines[0]).startsWith("﻿이름,구분");
        assertThat(lines).hasSize(2);
        assertThat(lines[1]).startsWith("\"'=모의,인물\",인력 후보,");
        assertThat(TalentService.cell("+1")).isEqualTo("'+1");
    }
}
