package com.salesmap.backend.global.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * 영업일(주말·공휴일 제외) 계산.
 * <p>
 * 공휴일은 대체공휴일 포함 2026~2027년분을 코드에 둔다. 임시공휴일이 지정되거나 연도가 바뀌면 이 목록을 갱신한다.
 * (근로자의 날처럼 회사마다 쉬는지 다른 날은 넣지 않았다.)
 */
public final class BusinessDayCalculator {

    private static final Set<LocalDate> HOLIDAYS = Set.of(
            // 2026
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 18), // 설날
            LocalDate.of(2026, 3, 2),   // 삼일절 대체공휴일
            LocalDate.of(2026, 5, 5),
            LocalDate.of(2026, 5, 25),  // 부처님오신날 대체공휴일
            LocalDate.of(2026, 6, 3),   // 전국동시지방선거
            LocalDate.of(2026, 8, 17),  // 광복절 대체공휴일
            LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 28), // 추석·대체공휴일
            LocalDate.of(2026, 10, 5),  // 개천절 대체공휴일
            LocalDate.of(2026, 10, 9),
            LocalDate.of(2026, 12, 25),
            // 2027
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 9), // 설날·대체공휴일
            LocalDate.of(2027, 3, 1),
            LocalDate.of(2027, 5, 5),
            LocalDate.of(2027, 5, 13),  // 부처님오신날
            LocalDate.of(2027, 8, 16),  // 광복절 대체공휴일
            LocalDate.of(2027, 9, 14), LocalDate.of(2027, 9, 15), LocalDate.of(2027, 9, 16), // 추석
            LocalDate.of(2027, 10, 4),  // 개천절 대체공휴일
            LocalDate.of(2027, 10, 11), // 한글날 대체공휴일
            LocalDate.of(2027, 12, 27)  // 성탄절 대체공휴일
    );

    private BusinessDayCalculator() {
    }

    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY
                && dayOfWeek != DayOfWeek.SUNDAY
                && !HOLIDAYS.contains(date);
    }

    /**
     * from 다음 날부터 to 당일까지의 영업일 수.
     * 예: 금요일 → 다음 주 월요일 = 1영업일 (주말은 세지 않음)
     */
    public static int businessDaysBetween(LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) {
            return 0;
        }
        int count = 0;
        for (LocalDate date = from.plusDays(1); !date.isAfter(to); date = date.plusDays(1)) {
            if (isBusinessDay(date)) {
                count++;
            }
        }
        return count;
    }
}
