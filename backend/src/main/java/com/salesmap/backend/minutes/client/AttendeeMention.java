package com.salesmap.backend.minutes.client;

/**
 * A 식별에 넘기는 상대 단서 하나 (contracts/identity_resolution의 mention 중 B가 아는 값).
 * 메모는 이름(+사용자가 적은 소속), 메일은 이름·이메일을 함께 넘긴다 — 메일 주소가 맞으면 A가 CONFIRMED를 낼 수 있다 (D-022).
 */
public record AttendeeMention(
        // 모르면 null (메일 수신자 중 이름 없이 주소만 있는 경우)
        String name,
        String email,
        // MEETING / EMAIL
        String channel,
        // 사용자가 미팅 기록 화면에 적은 소속. 메모 본문·장소에서 추측하지 않는다. 모르면 null
        String company
) {

    public AttendeeMention(String name, String email, String channel) {
        this(name, email, channel, null);
    }

    /** 회의록 참석자 이름. 이름을 모르면 메일 주소를 그대로 쓴다. */
    public String displayName() {
        return name != null && !name.isBlank() ? name : email;
    }
}
