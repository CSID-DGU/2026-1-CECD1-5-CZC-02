package com.salesmap.backend.gmail.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GmailCollectResponse(
        Long integrationId,
        LocalDateTime previousLastSyncedAt,
        LocalDateTime currentLastSyncedAt,
        LocalDateTime collectedAt,
        LocalDateTime latestMessageDate,
        String appUserEmail,
        String gmailAccountEmail,
        String oauthScope,
        String tokenGrantedScopes,
        String gmailQuery,
        List<String> attemptedGmailQueries,
        List<String> rawFetchedMessageIds,
        List<GmailMessagePreview> rawFetchedMessagesPreview,
        Integer gmailResultSizeEstimate,
        int gmailMaxResults,
        boolean lastSyncedAtUpdated,
        String syncMode,
        int totalFetched,
        int requestedCount,
        int savedCount,
        int skippedDuplicateCount,
        int skippedCount,
        List<Long> savedSourceGroupIds,
        List<Long> savedSourceIds,
        List<String> skippedExternalSourceIds,
        List<String> failedReasons
) {
}
