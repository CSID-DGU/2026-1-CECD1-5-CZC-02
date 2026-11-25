package com.salesmap.backend.talent;

import java.time.LocalDate;
import java.util.List;

/** 후보 추천·제출 리스트 응답. 모든 이유에 출처(원문·이력서 근거)를 붙이고, 모르는 값은 "확인 필요"로 둔다. */
public final class TalentMatchViews {
    private TalentMatchViews() {}

    /** 조건을 꺼낸 글 한 줄. origin: TASK(할 일) / CUSTOMER_REQUEST(회의록 고객 요청) / TYPED(직접 입력). */
    public record SourceLine(String origin, String label, String text) {}

    public record Found<T>(T value, String evidence) {}
    public record SkillNeed(String name, boolean required, String evidence) {}
    public record IndustryNeed(String name, String evidence) {}

    /** 고객 요청에서 규칙으로 꺼낸 조건. 못 찾은 칸은 null, unknowns에 "확인 필요"로 적는다. */
    public record Requirement(List<SkillNeed> skills, Found<Integer> minCareerMonths, Found<String> minGrade,
                              Found<LocalDate> startFrom, Found<Integer> headcount, List<IndustryNeed> industries,
                              List<String> unknowns) {}

    /** kind: SKILL / CAREER / GRADE / INDUSTRY / AVAILABILITY. evidence는 이력서 원문(없으면 계산 근거 설명). */
    public record Reason(String kind, String text, String evidence, int points) {}

    public record Candidate(String profileId, String name, Integer birthYear, String grade, Integer careerMonths,
                            LocalDate availableFrom, String availableBasis, List<String> skills, int score,
                            List<Reason> reasons, List<String> checkNeeded, boolean late, boolean selected) {}

    public record Excluded(String profileId, String name, List<String> reasons) {}

    public record TaskBrief(String taskId, String taskType, String content, String dueDate, String status,
                            String noteId, int submissionCount) {}

    public record MatchResult(TaskBrief task, List<SourceLine> sources, Requirement requirement,
                              List<Candidate> candidates, List<Excluded> excluded, int poolSize,
                              int draftCount, List<String> submission) {}

    public record MatchRequest(String taskId, String text) {}
    public record SubmissionRequest(List<String> profileIds) {}

    public record SubmittedCandidate(String profileId, String name, String grade, Integer careerMonths,
                                     LocalDate availableFrom, String availableBasis, List<String> topSkills) {}

    /** relation: SAME_TASK(이 할 일) / SAME_NOTE(같은 회의록) / SAME_COUNTERPART(같은 고객 담당자). */
    public record SubmissionBrief(String taskId, String taskContent, String dueDate, String relation,
                                  List<SubmittedCandidate> candidates) {}
}
