package com.salesmap.backend.gmail.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesmap.backend.gmail.config.GmailOAuthProperties;
import com.salesmap.backend.gmail.dto.GmailAuthorizeResponse;
import com.salesmap.backend.gmail.dto.GmailCollectResponse;
import com.salesmap.backend.gmail.dto.GmailMessagePreview;
import com.salesmap.backend.gmail.dto.GmailOAuthCallbackResponse;
import com.salesmap.backend.integration.entity.Integration;
import com.salesmap.backend.integration.entity.IntegrationProvider;
import com.salesmap.backend.integration.entity.IntegrationStatus;
import com.salesmap.backend.integration.repository.IntegrationRepository;
import com.salesmap.backend.source.entity.Source;
import com.salesmap.backend.source.entity.SourceDirection;
import com.salesmap.backend.source.entity.SourceGroup;
import com.salesmap.backend.source.entity.SourceStatus;
import com.salesmap.backend.source.entity.SourceType;
import com.salesmap.backend.source.repository.SourceGroupRepository;
import com.salesmap.backend.source.repository.SourceRepository;
import com.salesmap.backend.user.entity.User;
import com.salesmap.backend.user.repository.UserRepository;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GmailIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GmailIntegrationService.class);

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private static final String GOOGLE_USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String GMAIL_MESSAGES_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_MANUAL_RECENT_DAYS = 30;
    private static final int MAX_MANUAL_RECENT_DAYS = 90;
    private static final String SYNC_MODE_AUTO_INCREMENTAL = "AUTO_INCREMENTAL";
    private static final String SYNC_MODE_MANUAL_RECENT = "MANUAL_RECENT";
    private static final DateTimeFormatter GMAIL_SEARCH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Pattern HTML_BLOCK_PATTERN = Pattern.compile("(?is)<(script|style|head|meta|title)[^>]*>.*?</\\1>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(x?[0-9a-fA-F]+);");

    private final GmailOAuthProperties properties;
    private final GmailOAuthStateService stateService;
    private final IntegrationRepository integrationRepository;
    private final SourceRepository sourceRepository;
    private final SourceGroupRepository sourceGroupRepository;
    private final UserRepository userRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GmailIntegrationService(
            GmailOAuthProperties properties,
            GmailOAuthStateService stateService,
            IntegrationRepository integrationRepository,
            SourceRepository sourceRepository,
            SourceGroupRepository sourceGroupRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.stateService = stateService;
        this.integrationRepository = integrationRepository;
        this.sourceRepository = sourceRepository;
        this.sourceGroupRepository = sourceGroupRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    public GmailAuthorizeResponse createAuthorizationUrl(Long authenticatedUserId) {
        validateOAuthProperties();
        log.info("Creating Gmail OAuth authorization URL. userId={}, clientId={}, redirectUri={}, scope={}",
                authenticatedUserId,
                maskClientId(properties.getClientId()),
                properties.getRedirectUri(),
                properties.getScope()
        );

        String url = UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.getScope())
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", stateService.createState(authenticatedUserId))
                .build()
                .encode()
                .toUriString();

        return new GmailAuthorizeResponse(url);
    }

    @Transactional
    public GmailOAuthCallbackResponse handleCallback(String code, String state) {
        validateOAuthProperties();
        log.info("Handling Gmail OAuth callback. redirectUri={}, clientId={}, codePresent={}, statePresent={}",
                properties.getRedirectUri(),
                maskClientId(properties.getClientId()),
                code != null && !code.isBlank(),
                state != null && !state.isBlank()
        );

        Long userId = stateService.parseUserId(state);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        JsonNode tokenResponse = exchangeCodeForToken(code);
        String accessToken = requiredText(tokenResponse, "access_token");
        String refreshToken = optionalText(tokenResponse, "refresh_token");
        long expiresIn = tokenResponse.path("expires_in").asLong(3600);

        String externalAccountId = fetchEmail(accessToken);
        LocalDateTime tokenExpiresAt = LocalDateTime.now(DEFAULT_ZONE).plusSeconds(expiresIn);

        Integration integration = integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.GMAIL)
                .map(existing -> {
                    existing.updateToken(
                            externalAccountId,
                            accessToken,
                            refreshToken,
                            tokenExpiresAt,
                            IntegrationStatus.CONNECTED
                    );
                    return existing;
                })
                .orElseGet(() -> {
                    if (refreshToken == null || refreshToken.isBlank()) {
                        throw new IllegalArgumentException("Gmail refresh token을 받지 못했습니다. Google 권한 동의 화면에서 다시 승인해주세요.");
                    }
                    return integrationRepository.save(new Integration(
                            user,
                            IntegrationProvider.GMAIL,
                            externalAccountId,
                            accessToken,
                            refreshToken,
                            tokenExpiresAt,
                            IntegrationStatus.CONNECTED
                    ));
                });
        Integration savedIntegration = integrationRepository.save(integration);
        log.info("Gmail integration saved. userId={}, integrationId={}, gmailAccount={}, status={}",
                userId,
                savedIntegration.getId(),
                savedIntegration.getExternalAccountId(),
                savedIntegration.getStatus()
        );

        return new GmailOAuthCallbackResponse(
                savedIntegration.getId(),
                userId,
                savedIntegration.getProvider().name(),
                savedIntegration.getExternalAccountId()
        );
    }

    @Transactional
    public void disconnect(Long authenticatedUserId) {
        Integration integration = integrationRepository.findByUserIdAndProvider(authenticatedUserId, IntegrationProvider.GMAIL)
                .orElseThrow(() -> new NoSuchElementException("Gmail 연동 정보를 찾을 수 없습니다."));

        integration.disconnect();
        integrationRepository.save(integration);
        log.info("Disconnected Gmail integration. userId={}, integrationId={}, gmailAccount={}, status={}",
                authenticatedUserId,
                integration.getId(),
                integration.getExternalAccountId(),
                integration.getStatus()
        );
    }

    public GmailCollectResponse collectMessages(
            Long authenticatedUserId,
            String requestedMode,
            Integer requestedRecentDays,
            boolean debug,
            boolean ignoreQuery,
            String queryOverride
    ) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        Integration integration = integrationRepository.findByUserIdAndProvider(authenticatedUserId, IntegrationProvider.GMAIL)
                .orElseThrow(() -> new NoSuchElementException("Gmail 연동 정보를 찾을 수 없습니다."));

        if (integration.getStatus() != IntegrationStatus.CONNECTED) {
            throw new IllegalArgumentException("Gmail 연동 상태가 CONNECTED가 아닙니다.");
        }

        String accessToken = getValidAccessToken(integration);
        String gmailAccountEmail = fetchEmail(accessToken);
        String tokenGrantedScopes = fetchGrantedScopes(accessToken);
        LocalDateTime previousLastSyncedAt = integration.getLastSyncedAt();
        LocalDateTime syncStartedAt = LocalDateTime.now(DEFAULT_ZONE);
        String syncMode = resolveSyncMode(requestedMode);
        String gmailSearchQuery = resolveSearchQuery(syncMode, previousLastSyncedAt, requestedRecentDays, ignoreQuery, queryOverride);
        int gmailMaxResults = normalizePageSize(syncMode);
        List<String> attemptedGmailQueries = new ArrayList<>();
        log.info("Starting Gmail collect. userId={}, appUserEmail={}, gmailAccountEmail={}, integrationId={}, mode={}, query={}, maxResults={}, oauthScope={}, tokenGrantedScopes={}, ignoreQuery={}",
                authenticatedUserId,
                user.getEmail(),
                gmailAccountEmail,
                integration.getId(),
                syncMode,
                gmailSearchQuery,
                gmailMaxResults,
                properties.getScope(),
                tokenGrantedScopes,
                ignoreQuery
        );
        GmailListResult gmailListResult = listAllMessages(accessToken, gmailSearchQuery, gmailMaxResults, gmailMaxResults);
        attemptedGmailQueries.add(displayQuery(gmailSearchQuery));
        if (shouldTryFallbackQueries(gmailListResult, ignoreQuery, queryOverride)) {
            for (String fallbackQuery : fallbackQueries(requestedRecentDays)) {
                GmailListResult fallbackResult = listAllMessages(accessToken, fallbackQuery, gmailMaxResults, gmailMaxResults);
                attemptedGmailQueries.add(displayQuery(fallbackQuery));
                log.info("Gmail fallback list attempt. userId={}, integrationId={}, query={}, fetchedCount={}, resultSizeEstimate={}",
                        authenticatedUserId,
                        integration.getId(),
                        fallbackQuery,
                        fallbackResult.messageSummaries().size(),
                        fallbackResult.resultSizeEstimate()
                );
                if (!fallbackResult.messageSummaries().isEmpty()) {
                    gmailSearchQuery = fallbackQuery;
                    gmailListResult = fallbackResult;
                    break;
                }
            }
        }
        List<JsonNode> messageSummaries = gmailListResult.messageSummaries();
        List<String> rawFetchedMessageIds = messageSummaries.stream()
                .map(message -> message.path("id").asText(""))
                .filter(id -> !id.isBlank())
                .limit(10)
                .toList();
        List<GmailMessagePreview> rawFetchedMessagesPreview = buildMessagePreviews(accessToken, messageSummaries, debug ? 10 : 0);

        int requestedCount = messageSummaries.size();
        int savedCount = 0;
        int skippedCount = 0;
        int skippedDuplicateCount = 0;
        LocalDateTime latestMessageDate = null;
        List<Long> savedSourceGroupIds = new ArrayList<>();
        List<Long> savedSourceIds = new ArrayList<>();
        List<String> skippedExternalSourceIds = new ArrayList<>();
        List<String> failedReasons = new ArrayList<>();

        log.info("Fetched Gmail message summaries. userId={}, integrationId={}, fetchedCount={}, resultSizeEstimate={}, firstMessageIds={}, preview={}",
                authenticatedUserId,
                integration.getId(),
                requestedCount,
                gmailListResult.resultSizeEstimate(),
                rawFetchedMessageIds,
                rawFetchedMessagesPreview
        );

        for (JsonNode messageSummary : messageSummaries) {
            String messageId = requiredText(messageSummary, "id");
            boolean duplicated = sourceRepository.existsByIntegrationIdAndSourceTypeAndExternalSourceId(
                    integration.getId(),
                    SourceType.EMAIL,
                    messageId
            );

            if (duplicated) {
                skippedCount++;
                skippedDuplicateCount++;
                skippedExternalSourceIds.add(messageId);
                String duplicateSubject = findPreviewSubject(rawFetchedMessagesPreview, messageId);
                log.debug("Skipped duplicated Gmail message. messageId={}, title={}", messageId, duplicateSubject);
                failedReasons.add(duplicateSubject == null || duplicateSubject.isBlank()
                        ? messageId + ": duplicated"
                        : messageId + ": duplicated - " + duplicateSubject);
                continue;
            }

            try {
                JsonNode message = getMessage(accessToken, messageId);
                String threadId = requiredText(message, "threadId");
                String subject = extractSubject(message);
                LocalDateTime sentAt = extractSentAt(message);
                latestMessageDate = maxDateTime(latestMessageDate, sentAt);
                SourceGroup sourceGroup = findOrCreateSourceGroup(
                        user,
                        integration,
                        threadId,
                        subject
                );
                Source source = sourceRepository.save(new Source(
                        user,
                        integration,
                        sourceGroup,
                        SourceType.EMAIL,
                        messageId,
                        subject,
                        extractContent(message),
                        SourceStatus.COLLECTED,
                        sentAt,
                        resolveDirection(message, integration.getExternalAccountId()),
                        parseName(findHeader(message, "From")),
                        parseEmail(findHeader(message, "From")),
                        parseNames(combineRecipients(message)),
                        parseEmails(combineRecipients(message)),
                        sentAt
                ));

                savedCount++;
                addIfAbsent(savedSourceGroupIds, sourceGroup.getId());
                savedSourceIds.add(source.getId());
                log.info("Saved Gmail message as Source. messageId={}, sourceId={}, threadId={}, title={}",
                        messageId,
                        source.getId(),
                        threadId,
                        subject
                );
            } catch (RuntimeException exception) {
                skippedCount++;
                skippedExternalSourceIds.add(messageId);
                failedReasons.add(messageId + ": " + exception.getClass().getSimpleName() + " - " + exception.getMessage());
                log.warn("Failed to save Gmail message. messageId={}, reason={}", messageId, exception.getMessage());
            }
        }

        boolean lastSyncedAtUpdated = requestedCount > 0;
        LocalDateTime currentLastSyncedAt = lastSyncedAtUpdated ? syncStartedAt : previousLastSyncedAt;
        if (lastSyncedAtUpdated) {
            integration.updateLastSyncedAt(syncStartedAt);
            integrationRepository.save(integration);
        } else {
            log.info("Gmail collect fetched no messages. lastSyncedAt was not updated. userId={}, integrationId={}, query={}",
                    authenticatedUserId,
                    integration.getId(),
                    gmailSearchQuery
            );
        }

        return new GmailCollectResponse(
                integration.getId(),
                previousLastSyncedAt,
                currentLastSyncedAt,
                syncStartedAt,
                latestMessageDate,
                user.getEmail(),
                gmailAccountEmail,
                properties.getScope(),
                tokenGrantedScopes,
                gmailSearchQuery,
                attemptedGmailQueries,
                rawFetchedMessageIds,
                rawFetchedMessagesPreview,
                gmailListResult.resultSizeEstimate(),
                gmailMaxResults,
                lastSyncedAtUpdated,
                syncMode,
                requestedCount,
                requestedCount,
                savedCount,
                skippedDuplicateCount,
                skippedCount,
                savedSourceGroupIds,
                savedSourceIds,
                skippedExternalSourceIds,
                failedReasons
        );
    }

    private SourceGroup findOrCreateSourceGroup(
            User user,
            Integration integration,
            String threadId,
            String title
    ) {
        return sourceGroupRepository.findByIntegrationIdAndSourceTypeAndExternalGroupId(
                        integration.getId(),
                        SourceType.EMAIL,
                        threadId
                )
                .orElseGet(() -> sourceGroupRepository.save(new SourceGroup(
                        user,
                        integration,
                        SourceType.EMAIL,
                        threadId,
                        title,
                        true
                )));
    }

    @Transactional
    public String getValidAccessTokenForUser(Long authenticatedUserId) {
        Integration integration = integrationRepository.findByUserIdAndProvider(authenticatedUserId, IntegrationProvider.GMAIL)
                .orElseThrow(() -> new NoSuchElementException("Gmail integration not found."));

        if (integration.getStatus() != IntegrationStatus.CONNECTED) {
            throw new IllegalArgumentException("Gmail integration is not connected.");
        }

        return getValidAccessToken(integration);
    }

    private String getValidAccessToken(Integration integration) {
        if (integration.getTokenExpiresAt().isAfter(LocalDateTime.now(DEFAULT_ZONE).plusMinutes(1))) {
            return integration.getAccessToken();
        }

        JsonNode tokenResponse = refreshAccessToken(integration.getRefreshToken());
        String accessToken = requiredText(tokenResponse, "access_token");
        long expiresIn = tokenResponse.path("expires_in").asLong(3600);

        integration.updateToken(
                integration.getExternalAccountId(),
                accessToken,
                null,
                LocalDateTime.now(DEFAULT_ZONE).plusSeconds(expiresIn),
                IntegrationStatus.CONNECTED
        );
        integrationRepository.save(integration);

        return accessToken;
    }

    private JsonNode exchangeCodeForToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("redirect_uri", properties.getRedirectUri());
        body.add("grant_type", "authorization_code");

        try {
            String response = restClient.post()
                    .uri(GOOGLE_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseJson(response);
        } catch (RestClientResponseException exception) {
            log.warn("Gmail OAuth token exchange failed. status={}, redirectUri={}, clientId={}, responseBody={}",
                    exception.getStatusCode(),
                    properties.getRedirectUri(),
                    maskClientId(properties.getClientId()),
                    exception.getResponseBodyAsString()
            );
            throw new IllegalArgumentException("Gmail OAuth 인증 코드가 만료되었거나 이미 사용되었습니다. 설정 화면에서 다시 Gmail 연결을 시도해주세요.");
        } catch (RestClientException exception) {
            log.warn("Gmail OAuth token exchange request failed. redirectUri={}, clientId={}, message={}",
                    properties.getRedirectUri(),
                    maskClientId(properties.getClientId()),
                    exception.getMessage()
            );
            throw new IllegalArgumentException("Gmail OAuth 토큰 요청에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private JsonNode refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("refresh_token", refreshToken);
        body.add("grant_type", "refresh_token");

        String response = restClient.post()
                .uri(GOOGLE_TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class);

        return parseJson(response);
    }

    private String fetchEmail(String accessToken) {
        String response = restClient.get()
                .uri(GOOGLE_USERINFO_URL)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class);

        return requiredText(parseJson(response), "email");
    }

    private String fetchGrantedScopes(String accessToken) {
        try {
            String response = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(GOOGLE_TOKEN_INFO_URL)
                            .queryParam("access_token", accessToken)
                            .build()
                            .encode()
                            .toUriString())
                    .retrieve()
                    .body(String.class);

            return optionalText(parseJson(response), "scope");
        } catch (RestClientException exception) {
            log.warn("Failed to fetch Google tokeninfo scope. message={}", exception.getMessage());
            return null;
        }
    }

    private GmailListResult listAllMessages(String accessToken, String searchQuery, int pageSize, int maxTotalResults) {
        List<JsonNode> messageSummaries = new ArrayList<>();
        String pageToken = null;
        int resultSizeEstimate = 0;

        do {
            JsonNode response = listMessages(accessToken, searchQuery, pageSize, pageToken);
            resultSizeEstimate = Math.max(resultSizeEstimate, response.path("resultSizeEstimate").asInt(0));
            JsonNode messages = response.path("messages");
            if (messages.isArray()) {
                for (JsonNode message : messages) {
                    if (messageSummaries.size() >= maxTotalResults) {
                        break;
                    }
                    messageSummaries.add(message);
                }
            }
            pageToken = optionalText(response, "nextPageToken");
        } while (pageToken != null && !pageToken.isBlank() && messageSummaries.size() < maxTotalResults);

        return new GmailListResult(messageSummaries, resultSizeEstimate);
    }

    private JsonNode listMessages(String accessToken, String searchQuery, int maxResults, String pageToken) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(GMAIL_MESSAGES_URL)
                .queryParam("maxResults", maxResults);

        if (searchQuery != null && !searchQuery.isBlank()) {
            builder.queryParam("q", searchQuery);
        }

        if (pageToken != null && !pageToken.isBlank()) {
            builder.queryParam("pageToken", pageToken);
        }

        String response = restClient.get()
                .uri(builder.build().encode().toUriString())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class);

        return parseJson(response);
    }

    private JsonNode getMessage(String accessToken, String messageId) {
        String response = restClient.get()
                .uri(GMAIL_MESSAGES_URL + "/" + messageId + "?format=full")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class);

        return parseJson(response);
    }

    private List<GmailMessagePreview> buildMessagePreviews(
            String accessToken,
            List<JsonNode> messageSummaries,
            int limit
    ) {
        if (limit < 1) {
            return List.of();
        }

        List<GmailMessagePreview> previews = new ArrayList<>();
        for (JsonNode messageSummary : messageSummaries.stream().limit(limit).toList()) {
            try {
                JsonNode message = getMessage(accessToken, requiredText(messageSummary, "id"));
                previews.add(new GmailMessagePreview(
                        requiredText(message, "id"),
                        optionalText(message, "threadId"),
                        extractSubject(message),
                        message.path("internalDate").asText(null)
                ));
            } catch (RuntimeException exception) {
                log.warn("Failed to build Gmail debug preview. messageSummary={}, reason={}",
                        messageSummary,
                        exception.getMessage()
                );
            }
        }

        return previews;
    }

    private String findPreviewSubject(List<GmailMessagePreview> previews, String messageId) {
        return previews.stream()
                .filter(preview -> messageId.equals(preview.id()))
                .map(GmailMessagePreview::subject)
                .findFirst()
                .orElse(null);
    }

    private String extractSubject(JsonNode message) {
        String subject = findHeader(message, "Subject");
        if (subject == null || subject.isBlank()) {
            return "(no subject)";
        }

        return subject.length() > 255 ? subject.substring(0, 255) : subject;
    }

    private LocalDateTime extractSentAt(JsonNode message) {
        String date = findHeader(message, "Date");
        if (date != null && !date.isBlank()) {
            try {
                return LocalDateTime.ofInstant(
                        DateTimeFormatter.RFC_1123_DATE_TIME.parse(date, Instant::from),
                        DEFAULT_ZONE
                );
            } catch (DateTimeParseException ignored) {
                // Fall back to Gmail internalDate below.
            }
        }

        long internalDateMillis = message.path("internalDate").asLong(0);
        if (internalDateMillis > 0) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(internalDateMillis), DEFAULT_ZONE);
        }

        return LocalDateTime.now(DEFAULT_ZONE);
    }

    private SourceDirection resolveDirection(JsonNode message, String accountEmail) {
        String senderEmail = parseEmail(findHeader(message, "From"));
        if (senderEmail == null || accountEmail == null) {
            return SourceDirection.UNKNOWN;
        }

        return senderEmail.equalsIgnoreCase(accountEmail) ? SourceDirection.SENT : SourceDirection.RECEIVED;
    }

    private String combineRecipients(JsonNode message) {
        List<String> values = new ArrayList<>();
        addIfPresent(values, findHeader(message, "To"));
        addIfPresent(values, findHeader(message, "Cc"));
        addIfPresent(values, findHeader(message, "Bcc"));

        return String.join(", ", values);
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private String parseNames(String addressHeader) {
        if (addressHeader == null || addressHeader.isBlank()) {
            return null;
        }

        List<String> names = new ArrayList<>();
        for (String part : addressHeader.split(",")) {
            String name = parseName(part);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }

        return truncate(String.join(", ", names), 1000);
    }

    private String parseEmails(String addressHeader) {
        if (addressHeader == null || addressHeader.isBlank()) {
            return null;
        }

        List<String> emails = new ArrayList<>();
        for (String part : addressHeader.split(",")) {
            String email = parseEmail(part);
            if (email != null && !email.isBlank()) {
                emails.add(email);
            }
        }

        return truncate(String.join(", ", emails), 1000);
    }

    private String parseName(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        String trimmed = address.trim();
        int angleIndex = trimmed.indexOf('<');
        if (angleIndex > 0) {
            return cleanupAddressText(trimmed.substring(0, angleIndex));
        }

        String email = parseEmail(trimmed);
        if (email != null && trimmed.equalsIgnoreCase(email)) {
            return null;
        }

        return cleanupAddressText(trimmed);
    }

    private String parseEmail(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }

        String trimmed = address.trim();
        int start = trimmed.indexOf('<');
        int end = trimmed.indexOf('>');
        if (start >= 0 && end > start) {
            return trimmed.substring(start + 1, end).trim();
        }

        for (String token : trimmed.split("\\s+")) {
            String cleaned = token.replace(",", "").replace(";", "").trim();
            if (cleaned.contains("@")) {
                return cleaned;
            }
        }

        return null;
    }

    private String cleanupAddressText(String value) {
        String cleaned = value
                .replace("\"", "")
                .replace("'", "")
                .trim();

        return cleaned.isBlank() ? null : truncate(cleaned, 255);
    }

    private String extractContent(JsonNode message) {
        String content = extractBodyText(message.path("payload"));
        if (content == null || content.isBlank()) {
            content = message.path("snippet").asText("");
        }

        String cleaned = cleanMessageContent(content);
        if (cleaned.isBlank()) {
            return "(empty Gmail message)";
        }

        return cleaned;
    }

    private String extractBodyText(JsonNode payload) {
        String plainText = extractBodyTextByMimeType(payload, "text/plain");
        if (!plainText.isBlank()) {
            return plainText;
        }

        return extractBodyTextByMimeType(payload, "text/html");
    }

    private String extractBodyTextByMimeType(JsonNode payload, String targetMimeType) {
        if (payload == null || payload.isMissingNode()) {
            return "";
        }

        String mimeType = payload.path("mimeType").asText("");
        String data = payload.path("body").path("data").asText("");
        if (!data.isBlank() && mimeType.startsWith(targetMimeType)) {
            return decodeBase64Url(data);
        }

        JsonNode parts = payload.path("parts");
        if (parts.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : parts) {
                String text = extractBodyTextByMimeType(part, targetMimeType);
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append("\n");
                    }
                    builder.append(text);
                }
            }
            return builder.toString();
        }

        return "";
    }

    private String cleanMessageContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String cleaned = content
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        cleaned = HTML_BLOCK_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</div\\s*>", "\n")
                .replaceAll("(?i)</li\\s*>", "\n");
        cleaned = HTML_TAG_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = decodeHtmlEntities(cleaned);
        cleaned = cleaned
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();

        return cleaned;
    }

    private String decodeHtmlEntities(String value) {
        String decoded = value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");

        Matcher matcher = NUMERIC_ENTITY_PATTERN.matcher(decoded);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String entity = matcher.group(1);
            try {
                int codePoint = entity.startsWith("x") || entity.startsWith("X")
                        ? Integer.parseInt(entity.substring(1), 16)
                        : Integer.parseInt(entity);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (IllegalArgumentException exception) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    private String findHeader(JsonNode message, String name) {
        JsonNode headers = message.path("payload").path("headers");
        if (!headers.isArray()) {
            return null;
        }

        for (JsonNode header : headers) {
            if (name.equalsIgnoreCase(header.path("name").asText())) {
                return header.path("value").asText();
            }
        }

        return null;
    }

    private JsonNode parseJson(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Gmail API 응답을 파싱하지 못했습니다.", exception);
        }
    }

    private String decodeBase64Url(String data) {
        return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Gmail API 응답에 " + fieldName + " 값이 없습니다.");
        }

        return value;
    }

    private String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private String resolveSyncMode(String requestedMode) {
        if ("auto".equalsIgnoreCase(requestedMode) || SYNC_MODE_AUTO_INCREMENTAL.equalsIgnoreCase(requestedMode)) {
            return SYNC_MODE_AUTO_INCREMENTAL;
        }

        return SYNC_MODE_MANUAL_RECENT;
    }

    private String resolveSearchQuery(
            String syncMode,
            LocalDateTime lastSyncedAt,
            Integer requestedRecentDays,
            boolean ignoreQuery,
            String queryOverride
    ) {
        if (ignoreQuery) {
            return null;
        }

        if (queryOverride != null && !queryOverride.isBlank()) {
            return queryOverride.trim();
        }

        return buildSearchQuery(syncMode, lastSyncedAt, requestedRecentDays);
    }

    private boolean shouldTryFallbackQueries(
            GmailListResult gmailListResult,
            boolean ignoreQuery,
            String queryOverride
    ) {
        return gmailListResult.messageSummaries().isEmpty()
                && !ignoreQuery
                && (queryOverride == null || queryOverride.isBlank());
    }

    private List<String> fallbackQueries(Integer requestedRecentDays) {
        int recentDays = normalizeRecentDays(requestedRecentDays);
        List<String> queries = new ArrayList<>();
        queries.add(null);
        queries.add("newer_than:" + recentDays + "d");
        queries.add("in:anywhere");
        return queries;
    }

    private String displayQuery(String query) {
        return query == null || query.isBlank() ? "(query 없음)" : query;
    }

    private String buildSearchQuery(String syncMode, LocalDateTime lastSyncedAt, Integer requestedRecentDays) {
        if (SYNC_MODE_AUTO_INCREMENTAL.equals(syncMode) && lastSyncedAt != null) {
            return "in:anywhere after:" + lastSyncedAt
                    .minusDays(1)
                    .toLocalDate()
                    .format(GMAIL_SEARCH_DATE_FORMATTER);
        }

        return "in:anywhere newer_than:" + normalizeRecentDays(requestedRecentDays) + "d";
    }

    private int normalizeRecentDays(Integer requestedRecentDays) {
        if (requestedRecentDays == null || requestedRecentDays < 1) {
            return DEFAULT_MANUAL_RECENT_DAYS;
        }

        return Math.min(requestedRecentDays, MAX_MANUAL_RECENT_DAYS);
    }

    private int normalizePageSize(String syncMode) {
        int pageSize = properties.getCollectPageSize();
        if (pageSize < 1) {
            pageSize = 500;
        }

        if (SYNC_MODE_MANUAL_RECENT.equals(syncMode)) {
            return 100;
        }

        return Math.min(pageSize, 500);
    }

    private void validateOAuthProperties() {
        if (isBlank(properties.getClientId())
                || isBlank(properties.getClientSecret())
                || isBlank(properties.getRedirectUri())) {
            throw new IllegalStateException("Gmail OAuth 설정이 필요합니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String maskClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "(empty)";
        }

        int visibleLength = Math.min(clientId.length(), 8);
        return clientId.substring(0, visibleLength) + "...";
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }

    private void addIfAbsent(List<Long> values, Long value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private LocalDateTime maxDateTime(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }

        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }

        return current;
    }

    private record GmailListResult(List<JsonNode> messageSummaries, int resultSizeEstimate) {
    }
}
