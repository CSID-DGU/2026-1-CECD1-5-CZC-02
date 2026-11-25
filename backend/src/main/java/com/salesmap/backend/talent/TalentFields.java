package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** resume_profile에서 목록·필터용 칸을 뽑는다. 계산은 AI 모듈의 규칙이 이미 했으므로 여기서는 옮기기만 한다. */
record TalentFields(String name, Integer birthYear, Integer careerMonths, LocalDate availableFrom,
                    String availableBasis, String searchText) {

    static TalentFields from(JsonNode profile) {
        String name = text(profile.path("name").path("value"));
        JsonNode birth = profile.path("birthYear").path("value");
        Integer birthYear = birth.isInt() ? birth.asInt() : null;
        JsonNode months = profile.path("totalCareer").path("months");
        Integer careerMonths = months.isInt() ? months.asInt() : null;
        String date = text(profile.path("availableFrom").path("date"));
        String basis = text(profile.path("availableFrom").path("basis"));
        return new TalentFields(name, birthYear, careerMonths, date == null ? null : LocalDate.parse(date), basis,
                searchText(profile));
    }

    /** 이름·기술·산업을 소문자로 이어 둔다 (필터는 서비스가 낱말 단위로 다시 확인한다). */
    static String searchText(JsonNode profile) {
        Set<String> words = new LinkedHashSet<>();
        String name = text(profile.path("name").path("value"));
        if (name != null) words.add(name);
        profile.path("skills").forEach(skill -> words.add(skill.path("name").asText("")));
        profile.path("industries").forEach(industry -> words.add(industry.path("name").asText("")));
        words.remove("");
        return String.join("\n", words).toLowerCase(Locale.ROOT);
    }

    static String text(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() || node.asText().isBlank() ? null : node.asText();
    }
}
