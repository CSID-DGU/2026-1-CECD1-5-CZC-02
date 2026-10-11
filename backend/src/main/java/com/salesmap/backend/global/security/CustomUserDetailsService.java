package com.salesmap.backend.global.security;

import com.salesmap.backend.demo.entity.DemoWorkspace;
import com.salesmap.backend.demo.repository.DemoWorkspaceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final DemoWorkspaceRepository demoWorkspaceRepository;

    public CustomUserDetailsService(UserRepository userRepository, DemoWorkspaceRepository demoWorkspaceRepository) {
        this.userRepository = userRepository;
        this.demoWorkspaceRepository = demoWorkspaceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return new CustomUserPrincipal(user.getId(), user.getEmail(), user.getPassword(), user.getRole(), user.getStatus());
    }

    // 데모 모드가 켜져 있으면 이후 모든 요청을 데모 사용자 데이터로 처리한다(실제 데이터는 그대로 보존).
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        Long effectiveUserId = demoWorkspaceRepository.findByOwnerUserIdAndEnabledTrue(user.getId())
                .map(DemoWorkspace::getDemoUserId)
                .orElse(user.getId());

        return new CustomUserPrincipal(effectiveUserId, user.getId(), user.getEmail(), user.getPassword(), user.getRole(), user.getStatus());
    }
}
