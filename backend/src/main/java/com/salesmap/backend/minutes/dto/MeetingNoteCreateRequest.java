package com.salesmap.backend.minutes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** ② 미팅 기록 화면의 "AI로 정리하기". 메모는 원본(sources, MEETING_NOTE)으로 저장된다. */
public record MeetingNoteCreateRequest(
        // 언제 만났는지. 모르면 null
        LocalDateTime occurredAt,

        @Size(max = 255, message = "장소는 255자 이하여야 합니다.")
        String location,

        // 만난 사람 이름 (사용자가 입력한 그대로)
        List<String> attendees,

        @NotBlank(message = "메모 내용은 필수입니다.")
        String text,

        // FULL | LIGHT | 비우면 AI 모듈이 메모 모양을 보고 규칙으로 정한다
        String mode,

        // 원본 제목. 비우면 날짜·장소로 만든다
        @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
        String title
) {
}
