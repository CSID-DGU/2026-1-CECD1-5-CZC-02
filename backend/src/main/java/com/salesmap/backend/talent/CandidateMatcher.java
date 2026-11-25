package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import com.salesmap.backend.talent.TalentMatchViews.Candidate;
import com.salesmap.backend.talent.TalentMatchViews.Excluded;
import com.salesmap.backend.talent.TalentMatchViews.IndustryNeed;
import com.salesmap.backend.talent.TalentMatchViews.Reason;
import com.salesmap.backend.talent.TalentMatchViews.Requirement;
import com.salesmap.backend.talent.TalentMatchViews.SkillNeed;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 후보 풀 × 조건 → 추천 순서 (규칙만, LLM 없음).
 * 필수(기술·최소 경력·최소 등급)를 못 채운 게 확실하면 뺀다. 값을 모르면 빼지 않고 "확인 필요".
 * 가점: 우대 기술 · 산업 경험 · 투입 가능 시기 · 경력 여유. 이유마다 이력서 근거를 붙인다.
 * 순서: 요청 시기보다 늦게 투입 가능한 게 확실한 후보는 맨 뒤, 그 밖에는 점수 → 경력 순.
 */
final class CandidateMatcher {
    static final int BASE = 50;
    static final int PREFERRED_SKILL = 10;
    static final int INDUSTRY = 15;
    static final int INDUSTRY_CAP = 30;
    static final int AVAILABLE_IN_TIME = 20;
    static final int NO_ONGOING = 10;
    static final int CAREER_EXTRA_CAP = 10;
    /** 요청 기술 → 이것이 있으면 채운 것으로 본다 (Spring 위에서 도는 프레임워크). */
    static final Map<String, List<String>> IMPLIED_BY = Map.of("Spring", List.of("Spring Boot", "전자정부프레임워크"));
    static final List<String> ALWAYS_CHECK = List.of("희망 단가", "현재 투입 의사·연락 가능 여부");

    record Result(List<Candidate> candidates, List<Excluded> excluded) {}

    private CandidateMatcher() {}

    static Result match(Requirement requirement, List<TalentProfile> pool, Map<Long, JsonNode> documents,
                        Set<String> selected) {
        List<Candidate> candidates = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        for (TalentProfile profile : pool) {
            JsonNode document = documents.get(profile.getId());
            if (document == null) continue;
            evaluate(requirement, profile, document, selected, candidates, excluded);
        }
        // 요청 시기에 못 맞추는 게 확실한 후보는 점수와 관계없이 뒤로 (모르는 후보는 "확인 필요"로 앞에 둔다)
        candidates.sort(Comparator.comparing(Candidate::late)
                .thenComparing(Comparator.comparingInt(Candidate::score).reversed())
                .thenComparing(candidate -> candidate.careerMonths() == null ? -1 : candidate.careerMonths(), Comparator.reverseOrder())
                .thenComparing(Candidate::profileId));
        return new Result(candidates, excluded);
    }

    private static void evaluate(Requirement requirement, TalentProfile profile, JsonNode document, Set<String> selected,
                                 List<Candidate> candidates, List<Excluded> excluded) {
        String profileId = TalentService.profileId(profile.getId());
        Map<String, String> skills = new LinkedHashMap<>();
        document.path("skills").forEach(skill -> skills.putIfAbsent(skill.path("name").asText(), evidence(skill)));
        Map<String, String> industries = new LinkedHashMap<>();
        document.path("industries").forEach(item -> industries.putIfAbsent(item.path("name").asText(), evidence(item)));
        String grade = TalentFields.text(document.path("grade").path("value"));

        List<Reason> reasons = new ArrayList<>();
        List<String> misses = new ArrayList<>();
        List<String> checkNeeded = new ArrayList<>();
        int score = BASE;

        for (SkillNeed need : requirement.skills()) {
            String[] hit = findSkill(need.name(), skills);
            if (need.required()) {
                if (hit == null) { misses.add(need.name() + " 없음"); continue; }
                reasons.add(new Reason("SKILL", skillText(need.name(), hit[0]) + " (필수)", hit[1], 0));
            } else if (hit != null) {
                score += PREFERRED_SKILL;
                reasons.add(new Reason("SKILL", skillText(need.name(), hit[0]) + " (우대)", hit[1], PREFERRED_SKILL));
            }
        }

        Integer career = profile.getCareerMonths();
        if (requirement.minCareerMonths() != null) {
            int min = requirement.minCareerMonths().value();
            String asked = "요청 " + careerText(min) + " 이상";
            if (career == null) checkNeeded.add("총 경력 (" + asked + ")");
            else if (career < min) misses.add("경력 " + careerText(career) + " (" + asked + ")");
            else {
                int extra = Math.min(CAREER_EXTRA_CAP, (career - min) / 12 * 2);
                score += extra;
                reasons.add(new Reason("CAREER", "경력 " + careerText(career) + " (" + asked + ")", careerBasis(document), extra));
            }
        } else if (career != null) {
            reasons.add(new Reason("CAREER", "경력 " + careerText(career), careerBasis(document), 0));
        }

        if (requirement.minGrade() != null) {
            String asked = requirement.minGrade().value();
            if (grade == null) checkNeeded.add("등급 (요청 " + gradeLabel(asked) + " 이상)");
            else if (RequirementRules.GRADES.indexOf(grade) < RequirementRules.GRADES.indexOf(asked))
                misses.add("등급 " + gradeLabel(grade) + " (요청 " + gradeLabel(asked) + " 이상)");
            else reasons.add(new Reason("GRADE", "등급 " + gradeLabel(grade), evidence(document.path("grade")), 0));
        }

        int industryPoints = 0;
        for (IndustryNeed need : requirement.industries()) {
            String found = industries.get(need.name());
            if (found == null) continue;
            int points = Math.min(INDUSTRY, INDUSTRY_CAP - industryPoints);
            industryPoints += points;
            score += points;
            reasons.add(new Reason("INDUSTRY", need.name() + " 프로젝트 경험", found, points));
        }

        boolean late = false;
        LocalDate available = profile.getAvailableFrom();
        String basis = profile.getAvailableBasis();
        String availabilityEvidence = TalentFields.text(document.path("availableFrom").path("evidence"));
        LocalDate start = requirement.startFrom() == null ? null : requirement.startFrom().value();
        if (available != null) {
            if (start != null && !available.isAfter(start)) {
                score += AVAILABLE_IN_TIME;
                reasons.add(new Reason("AVAILABILITY", "투입 가능 " + available + " (요청 " + start + ")", availabilityEvidence, AVAILABLE_IN_TIME));
            } else if (start != null) {
                late = true;
                reasons.add(new Reason("AVAILABILITY", "투입 가능 " + available + " — 요청 " + start + "보다 늦음", availabilityEvidence, 0));
            } else {
                reasons.add(new Reason("AVAILABILITY", "투입 가능 " + available + " (진행 중 프로젝트 종료 다음 날)", availabilityEvidence, 0));
            }
        } else if ("NO_ONGOING_PROJECT".equals(basis)) {
            int points = start != null ? NO_ONGOING : 0;
            score += points;
            reasons.add(new Reason("AVAILABILITY", "이력서에 진행 중 프로젝트 없음", "이력서의 모든 프로젝트 기간이 기준일 전에 끝남", points));
            checkNeeded.add("지금 다른 일에 투입돼 있는지");
        } else if ("ONGOING_WITHOUT_END".equals(basis)) {
            checkNeeded.add("진행 중 프로젝트 종료일 (이력서에 없음)");
        } else {
            checkNeeded.add("투입 가능일");
        }

        if (!misses.isEmpty()) {
            excluded.add(new Excluded(profileId, profile.getName(), List.copyOf(misses)));
            return;
        }
        checkNeeded.addAll(ALWAYS_CHECK);
        candidates.add(new Candidate(profileId, profile.getName(), profile.getBirthYear(), grade, career, available, basis,
                List.copyOf(skills.keySet()), score, List.copyOf(reasons), List.copyOf(checkNeeded), late,
                selected.contains(profileId)));
    }

    /** [채운 기술 이름, 근거] 또는 null. */
    private static String[] findSkill(String asked, Map<String, String> skills) {
        if (skills.containsKey(asked)) return new String[]{asked, skills.get(asked)};
        for (String other : IMPLIED_BY.getOrDefault(asked, List.of()))
            if (skills.containsKey(other)) return new String[]{other, skills.get(other)};
        return null;
    }

    private static String skillText(String asked, String found) {
        return asked.equals(found) ? asked : asked + " (" + found + "로 채움)";
    }

    private static String evidence(JsonNode node) {
        String text = TalentFields.text(node.path("evidence"));
        return text == null ? null : text;
    }

    private static String careerBasis(JsonNode document) {
        JsonNode total = document.path("totalCareer");
        int periods = total.path("periodCount").asInt(0);
        String basis = total.path("basis").asText("");
        String from = "CAREERS".equals(basis) ? "경력" : "프로젝트";
        return periods > 0 ? from + " " + periods + "개 기간을 합침 (겹친 기간은 한 번만)" : null;
    }

    static String careerText(int months) {
        int years = months / 12, rest = months % 12;
        if (years == 0) return rest + "개월";
        return rest == 0 ? years + "년" : years + "년 " + rest + "개월";
    }

    static String gradeLabel(String grade) {
        return switch (grade) {
            case "JUNIOR" -> "초급";
            case "INTERMEDIATE" -> "중급";
            case "ADVANCED" -> "고급";
            case "EXPERT" -> "특급";
            default -> grade;
        };
    }
}
