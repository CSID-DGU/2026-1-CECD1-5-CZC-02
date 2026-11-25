package com.salesmap.backend.talent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.talent.TalentMatchViews.Requirement;
import com.salesmap.backend.talent.TalentMatchViews.SkillNeed;
import com.salesmap.backend.talent.TalentMatchViews.SourceLine;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 가짜 고객 요청 글만 쓴다. LLM·네트워크 없음. */
class RequirementRulesTest {
    static final SkillDictionary DICTIONARY = SkillDictionary.fromClasspath(new ObjectMapper());
    static final LocalDate REF = LocalDate.of(2026, 12, 3);

    static Requirement extract(String... texts) {
        return RequirementRules.extract(java.util.Arrays.stream(texts).map(text -> new SourceLine("TYPED", "테스트", text)).toList(),
                REF, DICTIONARY);
    }

    @Test
    void dictionaryScanFollowsPythonRules() {
        assertThat(DICTIONARY.scan("자바·스프링부트 백엔드, 자바스크립트는 몰라도 됨").stream().map(SkillDictionary.SkillHit::name))
                .containsExactly("Java", "Spring Boot", "JavaScript");
        // 헷갈리는 짧은 이름(go, c)은 줄글에서 찾지 않는다
        assertThat(DICTIONARY.scan("go 하자, c 등급")).isEmpty();
        assertThat(DICTIONARY.industries("가상은행 차세대").stream().map(SkillDictionary.IndustryHit::name)).containsExactly("금융");
    }

    @Test
    void demoRequestGivesAllConditionsWithEvidence() {
        Requirement requirement = extract("가상물산 자바·스프링 백엔드 개발자 3명 요청, 경력 5년 이상, 2027년 2월부터 투입. 금융권 프로젝트 경험 있으면 좋겠음");
        assertThat(requirement.skills()).extracting(SkillNeed::name).containsExactly("Java", "Spring");
        assertThat(requirement.skills()).allMatch(SkillNeed::required);
        assertThat(requirement.headcount().value()).isEqualTo(3);
        assertThat(requirement.minCareerMonths().value()).isEqualTo(60);
        assertThat(requirement.minCareerMonths().evidence()).isEqualTo("경력 5년 이상");
        assertThat(requirement.startFrom().value()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(requirement.industries()).extracting(TalentMatchViews.IndustryNeed::name).containsExactly("금융");
        assertThat(requirement.unknowns()).containsExactly("희망 단가", "근무 장소·형태");
    }

    @Test
    void preferredSkillIsNotRequiredUnlessAlsoAskedPlainly() {
        Requirement requirement = extract("Java 필수, React 우대");
        assertThat(requirement.skills()).extracting(SkillNeed::name, SkillNeed::required)
                .containsExactly(tuple("Java", true), tuple("React", false));
        Requirement both = extract("React 우대", "React 개발자");
        assertThat(both.skills()).extracting(SkillNeed::required).containsExactly(true);
    }

    @Test
    void monthWithoutYearUsesNextOccurrence() {
        assertThat(extract("3월 중순 투입").startFrom().value()).isEqualTo(LocalDate.of(2027, 3, 15));
        assertThat(extract("12월 말부터").startFrom().value()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(extract("1/5부터 합류").startFrom().value()).isEqualTo(LocalDate.of(2027, 1, 5));
        // 시작을 뜻하는 말이 없으면 날짜로 보지 않는다
        assertThat(extract("3월에 다시 연락").startFrom()).isNull();
    }

    @Test
    void unknownStaysNullAndIsListed() {
        Requirement requirement = extract("좋은 분 있으면 소개 부탁");
        assertThat(requirement.skills()).isEmpty();
        assertThat(requirement.minCareerMonths()).isNull();
        assertThat(requirement.startFrom()).isNull();
        assertThat(requirement.headcount()).isNull();
        assertThat(requirement.unknowns()).contains("필요 기술", "최소 경력", "투입 시기", "인원", "희망 단가");
    }

    @Test
    void careerGradeAndKoreanCount() {
        Requirement requirement = extract("고급 이상 두 명, 7년차 이상");
        assertThat(requirement.minGrade().value()).isEqualTo("ADVANCED");
        assertThat(requirement.headcount().value()).isEqualTo(2);
        assertThat(requirement.minCareerMonths().value()).isEqualTo(84);
        // 연도(2027년)를 경력으로 읽지 않는다
        assertThat(extract("2027년 2월부터 투입").minCareerMonths()).isNull();
    }
}
