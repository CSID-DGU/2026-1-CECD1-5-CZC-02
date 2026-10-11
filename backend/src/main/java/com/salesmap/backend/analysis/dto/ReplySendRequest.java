package com.salesmap.backend.analysis.dto;

/**
 * 답장 초안을 Gmail로 전송할 때 프론트가 보내는 본문.
 * 받는 사람은 보안을 위해 서버가 원본 메일의 발신자에서 직접 가져온다(임의 주소로 못 보냄).
 */
public record ReplySendRequest(
        String subject,
        String body
) {
}
