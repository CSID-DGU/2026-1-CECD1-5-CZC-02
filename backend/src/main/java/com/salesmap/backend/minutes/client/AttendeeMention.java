package com.salesmap.backend.minutes.client;

/**
 * A 식별에 넘기는 상대 단서 하나 (contracts/identity_resolution의 mention 중 B가 아는 값).
 * 메모는 이름만, 메일은 이름·이메일을 함께 넘긴다 — 메일 주소가 맞으면 A가 CONFIRMED를 낼 수 있다 (D-022).
 */
public record AttendeeMention(
        // 모르면 null (메일 수신자 중 이름 없이 주소만 있는 경우)
        String name,
        String email,
        // MEETING / EMAIL
        String channel
) {

    /** 회의록 참석자 이름. 이름을 모르면 메일 주소를 그대로 쓴다. */
    public String displayName() {
        return name != null && !name.isBlank() ? name : email;
    }
}
