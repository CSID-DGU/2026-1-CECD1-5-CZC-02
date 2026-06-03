package com.salesmap.backend.integration.service;

import com.salesmap.backend.integration.dto.IntegrationCreateRequest;
import com.salesmap.backend.integration.dto.IntegrationResponse;
import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import com.salesmap.backend.integration.entity.IntegrationStatus;
import com.salesmap.backend.integration.repository.IntegrationRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class IntegrationService {

    private final IntegrationRepository integrationRepository;
    private final UserRepository userRepository;

    public IntegrationService(
            IntegrationRepository integrationRepository,
            UserRepository userRepository
    ) {
        this.integrationRepository = integrationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public IntegrationResponse createIntegration(IntegrationCreateRequest request, Long authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        IntegrationProvider provider = parseProvider(request.provider());

        if (integrationRepository.existsByUserIdAndProvider(authenticatedUserId, provider)) {
            throw new IllegalArgumentException("이미 연동된 외부 서비스입니다.");
        }

        Integration integration = new Integration(
                user,
                provider,
                request.externalAccountId(),
                request.accessToken(),
                request.refreshToken(),
                request.tokenExpiresAt(),
                IntegrationStatus.CONNECTED
        );

        return IntegrationResponse.from(integrationRepository.save(integration));
    }

    @Transactional(readOnly = true)
    public List<IntegrationResponse> getIntegrations(Long authenticatedUserId) {
        if (!userRepository.existsById(authenticatedUserId)) {
            throw new NoSuchElementException("사용자를 찾을 수 없습니다.");
        }

        return integrationRepository.findByUserId(authenticatedUserId).stream()
                .map(IntegrationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationResponse getIntegrationByProvider(String providerValue, Long authenticatedUserId) {
        IntegrationProvider provider = parseProvider(providerValue);

        Integration integration = integrationRepository.findByUserIdAndProvider(authenticatedUserId, provider)
                .orElseThrow(() -> new NoSuchElementException("연동 정보를 찾을 수 없습니다."));

        return IntegrationResponse.from(integration);
    }

    private IntegrationProvider parseProvider(String provider) {
        try {
            return IntegrationProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 외부 서비스입니다.");
        }
    }
}
