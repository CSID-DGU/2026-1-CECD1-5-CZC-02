package com.salesmap.backend.schedule.entity;

/**
 * 미팅이 끝난 뒤 담당자가 남기는 결과. 나중에 딜 성사 예측을 하게 되면 정답 라벨로 쓴다.
 */
public enum ScheduleOutcome {
    SUCCESS,   // 잘됨
    ON_HOLD,   // 보류
    LOST       // 무산
}
