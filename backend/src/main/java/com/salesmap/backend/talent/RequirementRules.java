package com.salesmap.backend.talent;

import com.salesmap.backend.talent.TalentMatchViews.Found;
import com.salesmap.backend.talent.TalentMatchViews.IndustryNeed;
import com.salesmap.backend.talent.TalentMatchViews.Requirement;
import com.salesmap.backend.talent.TalentMatchViews.SkillNeed;
import com.salesmap.backend.talent.TalentMatchViews.SourceLine;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 고객 요청 글 → 인력 조건 (규칙만, LLM 없음). 기술은 기술명 사전, 경력·등급·시기·인원은 정규식.
 * 근거는 조건이 나온 원문 구절 그대로. 못 찾으면 null로 두고 "확인 필요"에 적는다 (추측하지 않는다).
 */
final class RequirementRules {
    static final List<String> GRADES = List.of("JUNIOR", "INTERMEDIATE", "ADVANCED", "EXPERT");
    private static final Map<String, String> GRADE_WORDS = Map.of("초급", "JUNIOR", "중급", "INTERMEDIATE", "고급", "ADVANCED", "특급", "EXPERT");
    private static final Map<String, Integer> KOREAN_COUNTS = Map.of("한", 1, "두", 2, "세", 3, "네", 4, "다섯", 5,
            "여섯", 6, "일곱", 7, "여덟", 8, "아홉", 9, "열", 10);

    private static final Pattern CLAUSE_SPLIT = Pattern.compile("[,;\\n]|(?<=[.!?])\\s+|\\s/\\s");
    private static final Pattern PREFERRED = Pattern.compile("우대|선호|있으면\\s*좋|있으면\\s*더|가산|플러스|가능하면");
    private static final Pattern CAREER = Pattern.compile("(?<![\\d.])(\\d{1,2})\\s*년\\s*(?:차\\s*)?(?:이상|↑|넘)");
    private static final Pattern CAREER_PLAIN = Pattern.compile("경력\\s*(\\d{1,2})\\s*년(?!\\s*\\d)");
    private static final Pattern GRADE = Pattern.compile("(초급|중급|고급|특급)");
    private static final Pattern HEADCOUNT = Pattern.compile("(?<![\\d.])(\\d{1,2})\\s*명|(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*명");
    private static final Pattern START_WORDS = Pattern.compile("부터|투입|착수|시작|합류|개시");
    private static final Pattern FULL_DATE = Pattern.compile("(?<!\\d)(20\\d{2})\\s*(?:[.\\-/]|년)\\s*(\\d{1,2})(?:\\s*(?:[.\\-/]|월)\\s*(\\d{1,2})\\s*일?)?(?!\\d)");
    private static final Pattern MONTH_DATE = Pattern.compile("(?<![\\d.])(\\d{1,2})\\s*월(?:\\s*(\\d{1,2})\\s*일|\\s*(초|중순|중|말|첫\\s*주))?");
    private static final Pattern SLASH_DATE = Pattern.compile("(?<![\\d./])(\\d{1,2})/(\\d{1,2})(?![\\d/])");

    private RequirementRules() {}

    static Requirement extract(List<SourceLine> lines, LocalDate reference, SkillDictionary dictionary) {
        Map<String, SkillNeed> skills = new LinkedHashMap<>();
        Map<String, IndustryNeed> industries = new LinkedHashMap<>();
        Found<Integer> career = null;
        Found<String> grade = null;
        Found<LocalDate> start = null;
        Found<Integer> headcount = null;

        for (SourceLine line : lines) {
            for (String raw : CLAUSE_SPLIT.split(line.text() == null ? "" : line.text())) {
                String clause = raw.strip();
                if (clause.isEmpty()) continue;
                boolean preferred = PREFERRED.matcher(clause).find();
                for (SkillDictionary.SkillHit hit : dictionary.scan(clause)) {
                    SkillNeed before = skills.get(hit.name());
                    // 한 번이라도 필수로 나오면 필수
                    if (before == null || (!before.required() && !preferred))
                        skills.put(hit.name(), new SkillNeed(hit.name(), !preferred, clause));
                }
                for (SkillDictionary.IndustryHit hit : dictionary.industries(clause))
                    industries.putIfAbsent(hit.name(), new IndustryNeed(hit.name(), clause));
                if (career == null) career = career(clause);
                if (grade == null) grade = grade(clause);
                if (start == null) start = startDate(clause, reference);
                if (headcount == null) headcount = headcount(clause);
            }
        }

        List<String> unknowns = new ArrayList<>();
        if (skills.isEmpty()) unknowns.add("필요 기술");
        if (career == null) unknowns.add("최소 경력");
        if (start == null) unknowns.add("투입 시기");
        if (headcount == null) unknowns.add("인원");
        unknowns.add("희망 단가");
        unknowns.add("근무 장소·형태");
        return new Requirement(List.copyOf(skills.values()), career, grade, start, headcount,
                List.copyOf(industries.values()), List.copyOf(unknowns));
    }

    static Found<Integer> career(String clause) {
        Matcher matcher = CAREER.matcher(clause);
        if (!matcher.find()) {
            matcher = CAREER_PLAIN.matcher(clause);
            if (!matcher.find()) return null;
        }
        return new Found<>(Integer.parseInt(matcher.group(1)) * 12, clause);
    }

    static Found<String> grade(String clause) {
        Matcher matcher = GRADE.matcher(clause);
        return matcher.find() ? new Found<>(GRADE_WORDS.get(matcher.group(1)), clause) : null;
    }

    static Found<Integer> headcount(String clause) {
        Matcher matcher = HEADCOUNT.matcher(clause);
        if (!matcher.find()) return null;
        int count = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : KOREAN_COUNTS.get(matcher.group(2));
        return count > 0 ? new Found<>(count, clause) : null;
    }

    /** "2027년 2월부터", "2월 중순 투입", "3/2부터". 연도가 없으면 기준일 이후 가장 가까운 그 달 (지난 달이면 다음 해). */
    static Found<LocalDate> startDate(String clause, LocalDate reference) {
        if (!START_WORDS.matcher(clause).find()) return null;
        Matcher full = FULL_DATE.matcher(clause);
        if (full.find()) {
            LocalDate date = date(Integer.parseInt(full.group(1)), Integer.parseInt(full.group(2)),
                    full.group(3) == null ? null : Integer.parseInt(full.group(3)), null);
            return date == null ? null : new Found<>(date, clause);
        }
        Matcher month = MONTH_DATE.matcher(clause);
        if (month.find()) {
            int value = Integer.parseInt(month.group(1));
            LocalDate date = date(yearFor(value, reference), value,
                    month.group(2) == null ? null : Integer.parseInt(month.group(2)), month.group(3));
            return date == null ? null : new Found<>(date, clause);
        }
        Matcher slash = SLASH_DATE.matcher(clause);
        if (slash.find()) {
            int value = Integer.parseInt(slash.group(1));
            LocalDate date = date(yearFor(value, reference), value, Integer.parseInt(slash.group(2)), null);
            return date == null ? null : new Found<>(date, clause);
        }
        return null;
    }

    private static int yearFor(int month, LocalDate reference) {
        return month >= reference.getMonthValue() ? reference.getYear() : reference.getYear() + 1;
    }

    private static LocalDate date(int year, int month, Integer day, String part) {
        if (month < 1 || month > 12) return null;
        YearMonth yearMonth = YearMonth.of(year, month);
        if (day != null) return day >= 1 && day <= yearMonth.lengthOfMonth() ? yearMonth.atDay(day) : null;
        if (part == null) return yearMonth.atDay(1);
        return switch (part.replaceAll("\\s+", "")) {
            case "중", "중순" -> yearMonth.atDay(15);
            case "말" -> yearMonth.atEndOfMonth();
            default -> yearMonth.atDay(1);
        };
    }
}
