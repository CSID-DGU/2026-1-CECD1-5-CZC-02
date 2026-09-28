package com.salesmap.backend.global.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import com.salesmap.backend.user.entity.UserStatus;

public class CustomUserPrincipal implements UserDetails {

    private final Long userId;
    private final Long ownerUserId;
    private final String email;
    private final String password;
    private final String role;
    private final UserStatus status;

    public CustomUserPrincipal(Long userId, String email, String password, String role, UserStatus status) {
        this(userId, userId, email, password, role, status);
    }

    public CustomUserPrincipal(Long userId, Long ownerUserId, String email, String password, String role, UserStatus status) {
        this.userId = userId;
        this.ownerUserId = ownerUserId;
        this.email = email;
        this.password = password;
        this.role = role;
        this.status = status;
    }

    /** 데이터 조회·저장에 쓰는 사용자. 데모 모드면 데모 사용자다. */
    public Long getUserId() {
        return userId;
    }

    /** 실제로 로그인한 사용자. 데모 모드 on/off처럼 계정 자체에 대한 동작에 쓴다. */
    public Long getOwnerUserId() {
        return ownerUserId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
