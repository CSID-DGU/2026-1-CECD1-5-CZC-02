package com.salesmap.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
