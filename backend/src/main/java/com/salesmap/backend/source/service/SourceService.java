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
    public SourceResponse createSource(SourceCreateRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        Integration integration = findIntegration(request.integrationId(), request.userId());
        SourceType sourceType = parseSourceType(request.sourceType());

        Source source = new Source(
                user,
                integration,
                sourceType,
                null,
                request.title(),
                request.content(),
                SourceStatus.CREATED,
                null
        );

        return SourceResponse.from(sourceRepository.save(source));
    }

    @Transactional(readOnly = true)
    public SourceResponse getSource(Long sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NoSuchElementException("원본 데이터를 찾을 수 없습니다."));

        return SourceResponse.from(source);
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> getSourcesByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("사용자를 찾을 수 없습니다.");
        }

        return sourceRepository.findByUserId(userId).stream()
                .map(SourceResponse::from)
                .toList();
    }

    private Integration findIntegration(Long integrationId, Long userId) {
        if (integrationId == null) {
            return null;
        }

        Integration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new NoSuchElementException("연동 정보를 찾을 수 없습니다."));

        if (!integration.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 사용자의 연동 정보가 아닙니다.");
        }

        return integration;
    }

    private SourceType parseSourceType(String sourceType) {
        try {
            return SourceType.valueOf(sourceType);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 원본 데이터 타입입니다.");
        }
    }
}
