package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salesmap.backend.minutes.entity.MeetingNote;
import com.salesmap.backend.minutes.repository.MeetingNoteRepository;
import com.salesmap.backend.talent.TalentMatchViews.Candidate;
import com.salesmap.backend.talent.TalentMatchViews.MatchResult;
import com.salesmap.backend.talent.TalentMatchViews.SubmissionBrief;
import com.salesmap.backend.task.TaskEntry;
import com.salesmap.backend.task.TaskRepository;
import com.salesmap.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 시연 시나리오(가상물산 자바 백엔드 3명)를 가짜 후보·가짜 할 일로 검사한다. DB·네트워크·LLM 없음. */
class TalentMatchServiceTest {
    static final Long OWNER = 7L;
    final ObjectMapper mapper = new ObjectMapper();
    final TaskRepository tasks = mock(TaskRepository.class);
    final MeetingNoteRepository notes = mock(MeetingNoteRepository.class);
    final TalentProfileRepository profiles = mock(TalentProfileRepository.class);
    final TalentSubmissionRepository submissions = mock(TalentSubmissionRepository.class);
    final Map<Long, TalentProfile> pool = new LinkedHashMap<>();
    final Map<Long, TaskEntry> taskRows = new LinkedHashMap<>();
    final List<TalentSubmission> savedRows = new ArrayList<>();
    TalentMatchService service;
    User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-12-03T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new TalentMatchService(tasks, notes, profiles, submissions, SkillDictionary.fromClasspath(mapper), mapper, clock);
        user = mock(User.class);
        when(user.getId()).thenReturn(OWNER);
        when(profiles.findByUserIdOrderByIdDesc(OWNER)).thenAnswer(call -> new ArrayList<>(pool.values()));
        when(profiles.findByIdAndUserId(anyLong(), eq(OWNER))).thenAnswer(call -> Optional.ofNullable(pool.get(call.<Long>getArgument(0))));
        when(tasks.findById(anyLong())).thenAnswer(call -> Optional.ofNullable(taskRows.get(call.<Long>getArgument(0))));
        when(tasks.findByOwnerIdOrderByIdDesc(OWNER)).thenAnswer(call -> new ArrayList<>(taskRows.values()));
        when(submissions.findByOwnerIdOrderByTaskIdAscPositionAsc(OWNER)).thenAnswer(call -> List.copyOf(savedRows));
        when(submissions.findByOwnerIdAndTaskIdOrderByPositionAsc(eq(OWNER), anyString())).thenAnswer(call ->
                savedRows.stream().filter(row -> row.getTaskId().equals(call.getArgument(1))).toList());
        doAnswer(call -> savedRows.removeIf(row -> row.getTaskId().equals(call.getArgument(1))))
                .when(submissions).deleteByOwnerIdAndTaskId(eq(OWNER), anyString());
        when(submissions.saveAll(anyList())).thenAnswer(call -> { savedRows.addAll(call.getArgument(0)); return call.getArgument(0); });

        // 이름은 가짜. (id, 이름, 등급, 경력 개월, 투입 가능일, 근거 종류, 기술, 산업)
        addProfile(1, "모의준호", "ADVANCED", 106, null, "NO_ONGOING_PROJECT", List.of("Java", "Spring Boot", "Oracle"), List.of("금융"), TalentStatus.CONFIRMED);
        addProfile(2, "샘플현우", "INTERMEDIATE", 82, "2027-02-01", "ONGOING_PROJECT_END", List.of("Java", "Spring Boot", "MySQL"), List.of("공공", "유통"), TalentStatus.CONFIRMED);
        addProfile(3, "모의태오", "ADVANCED", 96, null, "ONGOING_WITHOUT_END", List.of("Java", "전자정부프레임워크", "Spring", "Oracle"), List.of("공공", "교육"), TalentStatus.CONFIRMED);
        addProfile(4, "예시지민", "INTERMEDIATE", 91, "2027-04-01", "ONGOING_PROJECT_END", List.of("Java", "Spring Boot", "Oracle"), List.of("금융"), TalentStatus.CONFIRMED);
        addProfile(5, "가상민수", "JUNIOR", 45, null, "NO_ONGOING_PROJECT", List.of("Java", "Spring Boot", "MySQL"), List.of("유통", "물류"), TalentStatus.CONFIRMED);
        addProfile(6, "테스트서연", "EXPERT", 128, null, "NO_ONGOING_PROJECT", List.of("C#", ".NET", "MS-SQL"), List.of("제조", "의료"), TalentStatus.CONFIRMED);
        addProfile(7, "샘플대기", "ADVANCED", 120, null, "NO_ONGOING_PROJECT", List.of("Java", "Spring"), List.of("금융"), TalentStatus.DRAFT);

        addTask(11, "PROPOSAL_SUBMISSION", "가상물산에 자바 백엔드 인력 리스트 제출", "mn_21", "p_31", "CONFIRMED");
        addTask(12, "MEETING_PREP", "가상물산 후보 면접 미팅", "mn_22", "p_31", "CONFIRMED");
        addTask(13, "MEETING_PREP", "모의상사 정기 미팅", "mn_23", "p_40", "CONFIRMED");
        addTask(14, "SOURCING_START", "모의상사 인력 탐색 착수", "mn_23", "p_40", "CANCELLED");

        MeetingNote note = mock(MeetingNote.class);
        when(note.getUser()).thenReturn(user);
        when(note.getOccurredAt()).thenReturn(LocalDateTime.of(2026, 12, 2, 14, 0));
        when(note.getFieldsJson()).thenReturn("""
                {"discussion": [], "concerns": [], "commitments": [], "nextContact": [],
                 "customerRequests": [{"text": "자바·스프링 백엔드 개발자 3명, 경력 5년 이상, 2월부터 투입",
                   "evidence": "자바 스프링 백엔드 3명 정도 필요하고 5년 이상이면 좋겠어요. 2월부터 들어오면 됩니다. 금융 쪽 해 본 분 있으면 더 좋고요"}]}
                """);
        when(notes.findById(21L)).thenReturn(Optional.of(note));
    }

    void addProfile(long id, String name, String grade, Integer months, String available, String basis,
                    List<String> skills, List<String> industries, TalentStatus status) {
        ObjectNode document = mapper.createObjectNode();
        document.putObject("grade").put("value", grade).put("evidence", grade.substring(0, 2)).put("source", "RULE");
        ArrayNode skillNodes = document.putArray("skills");
        skills.forEach(skill -> skillNodes.addObject().put("name", skill).put("category", "LANGUAGE").put("evidence", "샘플 프로젝트 | " + skill));
        skillNodes.addObject().put("name", "Linux").put("category", "OS").put("evidence", "Linux");   // 브리핑 요약에서는 빠진다
        ArrayNode industryNodes = document.putArray("industries");
        industries.forEach(industry -> industryNodes.addObject().put("name", industry).put("evidence", industry));
        document.putObject("totalCareer").put("months", months).put("basis", "PROJECTS").put("periodCount", 3);
        ObjectNode availableFrom = document.putObject("availableFrom").put("basis", basis);
        if (available != null) availableFrom.put("date", available).put("evidence", "예시 프로젝트 | ~ " + available);
        else availableFrom.putNull("date");

        TalentProfile profile = mock(TalentProfile.class);
        when(profile.getId()).thenReturn(id);
        when(profile.getName()).thenReturn(name);
        when(profile.getStatus()).thenReturn(status);
        when(profile.getCareerMonths()).thenReturn(months);
        when(profile.getAvailableFrom()).thenReturn(available == null ? null : LocalDate.parse(available));
        when(profile.getAvailableBasis()).thenReturn(basis);
        when(profile.getDocument()).thenReturn(document.toString());
        pool.put(id, profile);
    }

    void addTask(long id, String type, String content, String noteId, String counterpart, String status) {
        ObjectNode task = mapper.createObjectNode();
        task.put("taskId", "t_" + id).put("noteId", noteId).put("content", content).put("dueDate", "2026-12-08")
                .put("dueDateIsDefault", false).putNull("assignee").put("counterpartPersonId", counterpart)
                .put("commitmentKind", "EXPLICIT").put("taskType", type).put("taskTypeConfidence", 0.9)
                .put("taskTypeSource", "RULE").put("evidence", content).put("status", status);
        TaskEntry entry = new TaskEntry(OWNER, noteId, "key-" + id, task.toString());
        ReflectionTestUtils.setField(entry, "id", id);
        taskRows.put(id, entry);
    }

    @Test
    void demoScenarioFiltersAndRanksWithReasons() {
        MatchResult result = service.matchForTask("t_11", null, OWNER);

        assertThat(result.sources()).extracting(TalentMatchViews.SourceLine::origin)
                .containsExactly("TASK", "CUSTOMER_REQUEST", "CUSTOMER_REQUEST");
        assertThat(result.requirement().skills()).extracting(TalentMatchViews.SkillNeed::name).containsExactly("Java", "Spring");
        // 회의 날짜(2026-12-02) 기준으로 "2월" → 2027-02-01
        assertThat(result.requirement().startFrom().value()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(result.poolSize()).isEqualTo(6);
        assertThat(result.draftCount()).isEqualTo(1);   // 확정 전 이력서는 추천에 넣지 않는다

        assertThat(result.candidates()).extracting(Candidate::name)
                .containsExactly("모의준호", "샘플현우", "모의태오", "예시지민");
        assertThat(result.excluded()).extracting(TalentMatchViews.Excluded::name).containsExactlyInAnyOrder("가상민수", "테스트서연");
        assertThat(result.excluded()).filteredOn(item -> item.name().equals("가상민수")).singleElement()
                .satisfies(item -> assertThat(item.reasons()).containsExactly("경력 3년 9개월 (요청 5년 이상)"));
        assertThat(result.excluded()).filteredOn(item -> item.name().equals("테스트서연")).singleElement()
                .satisfies(item -> assertThat(item.reasons()).containsExactly("Java 없음", "Spring 없음"));

        Candidate junho = result.candidates().get(0);
        assertThat(junho.reasons()).extracting(TalentMatchViews.Reason::text)
                .contains("Spring (Spring Boot로 채움) (필수)", "금융 프로젝트 경험", "이력서에 진행 중 프로젝트 없음");
        assertThat(junho.reasons()).allSatisfy(reason -> assertThat(reason.evidence()).isNotBlank());
        assertThat(junho.checkNeeded()).contains("희망 단가", "지금 다른 일에 투입돼 있는지");

        Candidate jimin = result.candidates().get(3);
        assertThat(jimin.late()).isTrue();
        assertThat(result.candidates().get(2).checkNeeded()).contains("진행 중 프로젝트 종료일 (이력서에 없음)");
    }

    @Test
    void typedTextRecomputesWithoutTouchingTask() {
        MatchResult result = service.matchForTask("t_11", "C# 개발자 1명", OWNER);
        assertThat(result.sources()).singleElement().extracting(TalentMatchViews.SourceLine::origin).isEqualTo("TYPED");
        assertThat(result.candidates()).extracting(Candidate::name).containsExactly("테스트서연");
        assertThat(service.matchText("자바 개발자", OWNER).task()).isNull();
        assertThatThrownBy(() -> service.matchText(" ", OWNER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submissionKeepsOrderAcceptsOnlyConfirmedAndFlushesDeleteFirst() {
        var saved = service.saveSubmission("t_11", List.of("rp_2", "rp_1", "rp_2"), OWNER);
        assertThat(saved).extracting(TalentMatchViews.SubmittedCandidate::name).containsExactly("샘플현우", "모의준호");
        assertThat(saved.get(0).topSkills()).containsExactly("Java", "Spring Boot", "MySQL");
        InOrder order = inOrder(submissions);
        order.verify(submissions).deleteByOwnerIdAndTaskId(OWNER, "t_11");
        order.verify(submissions).flush();
        order.verify(submissions).saveAll(anyList());

        assertThat(service.matchForTask("t_11", null, OWNER).submission()).containsExactly("rp_2", "rp_1");
        assertThat(service.matchForTask("t_11", null, OWNER).candidates()).filteredOn(Candidate::selected)
                .extracting(Candidate::profileId).containsExactlyInAnyOrder("rp_1", "rp_2");
        assertThat(service.matchTasks(OWNER)).extracting(TalentMatchViews.TaskBrief::taskId, TalentMatchViews.TaskBrief::submissionCount)
                .containsExactly(tuple("t_11", 2));   // 취소된 소싱 할 일(t_14)은 뺀다

        assertThatThrownBy(() -> service.saveSubmission("t_11", List.of("rp_7"), OWNER)).hasMessageContaining("확정한 후보만");
        assertThatThrownBy(() -> service.saveSubmission("t_11", List.of("rp_99"), OWNER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.saveSubmission("t_99", List.of(), OWNER)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.matchForTask("x_1", null, OWNER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void briefingSlotFollowsSameTaskNoteOrCounterpart() {
        service.saveSubmission("t_11", List.of("rp_1", "rp_2", "rp_3"), OWNER);

        List<SubmissionBrief> interview = service.related("t_12", OWNER);   // 같은 고객 담당자(p_31)의 면접 미팅
        assertThat(interview).singleElement().satisfies(brief -> {
            assertThat(brief.taskId()).isEqualTo("t_11");
            assertThat(brief.relation()).isEqualTo("SAME_COUNTERPART");
            assertThat(brief.candidates()).extracting(TalentMatchViews.SubmittedCandidate::name).containsExactly("모의준호", "샘플현우", "모의태오");
        });
        assertThat(service.related("t_11", OWNER)).singleElement().extracting(SubmissionBrief::relation).isEqualTo("SAME_TASK");
        assertThat(service.related("t_13", OWNER)).isEmpty();   // 다른 고객

        pool.remove(2L);   // 지운 이력서는 브리핑에서 빠진다
        assertThat(service.related("t_12", OWNER).get(0).candidates()).extracting(TalentMatchViews.SubmittedCandidate::name)
                .containsExactly("모의준호", "모의태오");
    }

    @Test
    void otherUsersTaskIsHidden() {
        TaskEntry entry = new TaskEntry(99L, null, "key-50", taskRows.get(11L).getDocument());
        ReflectionTestUtils.setField(entry, "id", 50L);
        taskRows.put(50L, entry);
        assertThatThrownBy(() -> service.matchForTask("t_50", null, OWNER)).isInstanceOf(NoSuchElementException.class);
    }
}
