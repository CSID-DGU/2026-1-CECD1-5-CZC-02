package com.salesmap.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
    // Spring MVC의 DEBUG/TRACE 객체 출력에도 인증 입력은 남기지 않는다.
    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
