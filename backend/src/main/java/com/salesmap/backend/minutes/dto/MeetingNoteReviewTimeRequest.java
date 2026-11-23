package com.salesmap.backend.minutes.dto;

/** 확인 화면을 실제로 본 시간(초). 범위 검사는 서비스에서 한다. */
public record MeetingNoteReviewTimeRequest(Integer seconds) {
}
