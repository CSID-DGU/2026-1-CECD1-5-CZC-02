package com.salesmap.backend.taskstatus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusRulesTest {

    @Test
    void 날짜_수량_흔한_동작말은_비교_낱말에서_뺀다() {
        assertThat(TaskStatusRules.keywords("계약서 초안 이번 주 금요일까지 송부")).containsExactly("계약서", "초안");
        assertThat(TaskStatusRules.keywords("후보 이력서 3건 11월 25일까지 송부")).containsExactly("후보", "이력서");
        assertThat(TaskStatusRules.keywords("투입 일정 내부 검토 후 회신 (고객 측) — 이행 여부 확인"))
                .containsExactly("투입", "일정", "검토");
        assertThat(TaskStatusRules.keywords(null)).isEmpty();
    }

    @Test
    void 낱말이_두_개_이상_맞아야_같은_일로_본다() {
        List<String> words = List.of("계약서", "초안");
        assertThat(TaskStatusRules.matched(words, "계약서 초안 드립니다", "")).containsExactly("계약서", "초안");
        assertThat(TaskStatusRules.matched(words, "계약서 일정 문의", "")).isEmpty();
        assertThat(TaskStatusRules.matched(List.of("견적서"), "견적서 보내드립니다", "")).containsExactly("견적서");
    }

    @Test
    void 답장에_딸린_이전_메일은_근거로_쓰지_않는다() {
        List<String> words = List.of("계약서", "초안");
        assertThat(TaskStatusRules.matched(words, "회신", "확인했습니다.\n> 계약서 초안 부탁드립니다")).isEmpty();
        assertThat(TaskStatusRules.matched(words, "회신", "확인했습니다.\n-----Original Message-----\n계약서 초안")).isEmpty();
        assertThat(TaskStatusRules.matched(words, "회신", "확인했습니다.\n보낸 사람: 고객\n계약서 초안")).isEmpty();
    }

    @Test
    void 받는_사람_목록에서_메일_주소를_찾는다() {
        Set<String> emails = Set.of("lee@yesi.example");
        assertThat(TaskStatusRules.containsEmail("a@example.test, <LEE@yesi.example>", emails)).isTrue();
        assertThat(TaskStatusRules.containsEmail("notlee@yesi.example", emails)).isFalse();
        assertThat(TaskStatusRules.containsEmail(null, emails)).isFalse();
    }
}
