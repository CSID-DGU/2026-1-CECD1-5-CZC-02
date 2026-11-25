package com.salesmap.backend.talent;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 후보 풀 필터. 비어 있는 조건은 보지 않는다.
 * availableBy: 그날까지 투입 가능한 사람 = 진행 중 프로젝트가 그 전에 끝나거나, 진행 중 프로젝트가 없는 사람.
 * 종료일을 모르는 진행 중 프로젝트(ONGOING_WITHOUT_END)는 추측하지 않고 뺀다.
 */
record TalentFilter(String status, String q, List<String> skills, Integer minCareerMonths, String industry,
                    LocalDate availableBy) {

    static TalentFilter of(String status, String q, String skills, Integer minCareerMonths, String industry,
                           LocalDate availableBy) {
        if (status != null && !status.isBlank() && !Set.of("PROCESSING", "DRAFT", "CONFIRMED", "FAILED").contains(status))
            throw new IllegalArgumentException("상태 값이 올바르지 않습니다.");
        List<String> skillList = skills == null ? List.of() : Arrays.stream(skills.split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).map(TalentFilter::lower).toList();
        return new TalentFilter(blank(status), blank(q) == null ? null : lower(q.strip()), skillList, minCareerMonths,
                blank(industry) == null ? null : lower(industry.strip()), availableBy);
    }

    boolean matches(TalentProfile profile) {
        if (status != null && !profile.getStatus().name().equals(status)) return false;
        String words = profile.getSearchText() == null ? "" : profile.getSearchText();
        List<String> lines = List.of(words.split("\n"));
        if (q != null && !words.contains(q)
                && (profile.getFileName() == null || !lower(profile.getFileName()).contains(q))) return false;
        for (String skill : skills) if (!lines.contains(skill)) return false;   // 기술은 이름이 정확히 같아야 한다
        if (industry != null && !lines.contains(industry)) return false;
        if (minCareerMonths != null && (profile.getCareerMonths() == null || profile.getCareerMonths() < minCareerMonths))
            return false;
        if (availableBy != null) {
            boolean free = "NO_ONGOING_PROJECT".equals(profile.getAvailableBasis());
            boolean endsBefore = profile.getAvailableFrom() != null && !profile.getAvailableFrom().isAfter(availableBy);
            if (!free && !endsBefore) return false;
        }
        return true;
    }

    static String gradeLabel(String grade) {
        if (grade == null) return null;
        return switch (grade) {
            case "JUNIOR" -> "초급";
            case "INTERMEDIATE" -> "중급";
            case "ADVANCED" -> "고급";
            case "EXPERT" -> "특급";
            default -> grade;
        };
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
