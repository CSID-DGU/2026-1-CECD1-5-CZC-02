package com.salesmap.backend.global.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDayCalculatorTest {

    @Test
    void 주말은_영업일에서_제외한다() {
        // 2026-09-11(금) → 2026-09-14(월): 토·일 제외 1영업일
        assertThat(BusinessDayCalculator.businessDaysBetween(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 14)))
                .isEqualTo(1);
    }

    @Test
    void 추석_연휴와_대체공휴일을_제외한다() {
        // 2026-09-23(수) → 2026-09-30(수): 24·25 추석, 26·27 주말, 28 대체공휴일 → 29, 30만 영업일
        assertThat(BusinessDayCalculator.businessDaysBetween(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 30)))
                .isEqualTo(2);
    }

    @Test
    void 같은_날이거나_과거면_0이다() {
        LocalDate day = LocalDate.of(2026, 9, 15);
        assertThat(BusinessDayCalculator.businessDaysBetween(day, day)).isZero();
        assertThat(BusinessDayCalculator.businessDaysBetween(day, day.minusDays(3))).isZero();
    }

    @Test
    void 공휴일은_영업일이_아니다() {
        assertThat(BusinessDayCalculator.isBusinessDay(LocalDate.of(2026, 10, 9))).isFalse();  // 한글날(금)
        assertThat(BusinessDayCalculator.isBusinessDay(LocalDate.of(2026, 10, 8))).isTrue();   // 목
    }
}
