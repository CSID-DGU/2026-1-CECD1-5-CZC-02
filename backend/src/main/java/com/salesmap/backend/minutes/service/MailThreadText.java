package com.salesmap.backend.minutes.service;

import com.salesmap.backend.source.entity.Source;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 메일(스레드)을 회의록 엔진 입력 텍스트로 묶는다. 메일 한 통이면 본문 그대로, 여러 통이면 시간순으로 메일마다 구분 줄을 넣는다:
 * <pre>------ 메일 2/3 · 2026-10-14 15:00 · 홍길동 ------</pre>
 * AI 모듈(app/minutes/preprocess.py THREAD_SEPARATOR)이 이 줄로 메일을 나눠 메일마다 인용·서명을 뺀다.
 * 형식을 바꾸면 두 곳을 같이 바꾼다. 본문은 고치지 않는다 (근거는 원문 글자 그대로여야 함).
 */
final class MailThreadText {

    private static final DateTimeFormatter SEPARATOR_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private MailThreadText() {
    }

    static String build(List<Source> messages) {
        if (messages.size() == 1) {
            return messages.get(0).getContent();
        }
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            Source message = messages.get(index);
            parts.add(separator(index + 1, messages.size(), sentAt(message), sender(message)));
            parts.add(message.getContent());
        }
        return String.join("\n", parts);
    }

    static String separator(int number, int total, LocalDateTime sentAt, String sender) {
        String time = sentAt == null ? "시각 모름" : sentAt.format(SEPARATOR_TIME);
        return "------ 메일 " + number + "/" + total + " · " + time + " · " + sender + " ------";
    }

    static LocalDateTime sentAt(Source message) {
        return message.getSentAt() != null ? message.getSentAt() : message.getCollectedAt();
    }

    private static String sender(Source message) {
        if (message.getSenderName() != null && !message.getSenderName().isBlank()) {
            return message.getSenderName().trim();
        }
        if (message.getSenderEmail() != null && !message.getSenderEmail().isBlank()) {
            return message.getSenderEmail().trim();
        }
        return "보낸 사람 모름";
    }
}
