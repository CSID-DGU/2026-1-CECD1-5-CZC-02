package com.salesmap.backend.source.service;

import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.repository.IntegrationRepository;
import com.salesmap.backend.source.dto.SourceCreateRequest;
import com.salesmap.backend.source.dto.SourceResponse;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;
    private final UserRepository userRepository;
    private final IntegrationRepository integrationRepository;

    public SourceService(
            SourceRepository sourceRepository,
            UserRepository userRepository,
            IntegrationRepository integrationRepository
    ) {
        this.sourceRepository = sourceRepository;
        this.userRepository = userRepository;
        this.integrationRepository = integrationRepository;
    }

    @Transactional
    public SourceResponse createSource(SourceCreateRequest request, Long authenticatedUserId) {
        validateRequestedUser(request.userId(), authenticatedUserId);

        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        Integration integration = findIntegration(request.integrationId(), authenticatedUserId);
        SourceType sourceType = parseSourceType(request.sourceType());
        String externalSourceId = normalizeExternalSourceId(request.externalSourceId());
        validateExternalSource(externalSourceId, integration, sourceType);

        SourceStatus status = isCollectedSource(request, externalSourceId) ? SourceStatus.COLLECTED : SourceStatus.CREATED;

        Source source = new Source(
                user,
                integration,
                sourceType,
                externalSourceId,
                request.title(),
                request.content(),
                status,
                request.collectedAt()
        );

        return SourceResponse.from(sourceRepository.save(source));
    }

    @Transactional(readOnly = true)
    public SourceResponse getSource(Long sourceId, Long authenticatedUserId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("원본 데이터를 찾을 수 없습니다."));

        if (!source.getUser().getId().equals(authenticatedUserId)) {
            throw new AccessDeniedException("해당 원본 데이터에 접근할 권한이 없습니다.");
        }

        return SourceResponse.from(source);
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> getSourcesByUser(Long requestedUserId, Long authenticatedUserId, int page, int size) {
        validateRequestedUser(requestedUserId, authenticatedUserId);

        if (!userRepository.existsById(authenticatedUserId)) {
            throw new NoSuchElementException("사용자를 찾을 수 없습니다.");
        }

        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "sentAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return sourceRepository.findByUserId(authenticatedUserId, pageRequest).stream()
                .map(SourceResponse::from)
                .toList();
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 10;
        }

        return Math.min(size, 100);
    }

    private Integration findIntegration(Long integrationId, Long userId) {
        if (integrationId == null) {
            return null;
        }

        Integration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new NoSuchElementException("연동 정보를 찾을 수 없습니다."));

        if (!integration.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("해당 사용자의 연동 정보가 아닙니다.");
        }

        return integration;
    }

    private void validateRequestedUser(Long requestedUserId, Long authenticatedUserId) {
        if (requestedUserId != null && !requestedUserId.equals(authenticatedUserId)) {
            throw new AccessDeniedException("다른 사용자의 데이터에 접근할 수 없습니다.");
        }
    }

    private String normalizeExternalSourceId(String externalSourceId) {
        if (externalSourceId == null || externalSourceId.isBlank()) {
            return null;
        }

        return externalSourceId.trim();
    }

    private void validateExternalSource(String externalSourceId, Integration integration, SourceType sourceType) {
        if (externalSourceId == null) {
            return;
        }

        if (integration == null) {
            throw new IllegalArgumentException("외부 원본 ID가 있는 데이터는 연동 정보가 필요합니다.");
        }

        boolean duplicated = sourceRepository.existsByIntegrationIdAndSourceTypeAndExternalSourceId(
                integration.getId(),
                sourceType,
                externalSourceId
        );

        if (duplicated) {
            throw new IllegalArgumentException("이미 저장된 외부 원본 데이터입니다.");
        }
    }

    private boolean isCollectedSource(SourceCreateRequest request, String externalSourceId) {
        return request.integrationId() != null
                || externalSourceId != null
                || request.collectedAt() != null;
    }

    private SourceType parseSourceType(String sourceType) {
        try {
            return SourceType.valueOf(sourceType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 원본 데이터 타입입니다.");
        }
    }
}
