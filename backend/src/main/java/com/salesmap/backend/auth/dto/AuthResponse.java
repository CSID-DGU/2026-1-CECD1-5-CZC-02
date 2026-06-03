package com.salesmap.backend.auth.dto;

import com.salesmap.backend.user.dto.UserResponse;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Long expiresIn,
        UserResponse user
) {
}
