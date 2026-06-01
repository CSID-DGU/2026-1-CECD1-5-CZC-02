package com.salesmap.backend.gmail.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GmailCollectResponse(
        Long integrationId,
        LocalDateTime previousLastSyncedAt,
        LocalDateTime currentLastSyncedAt,
        int requestedCount,
        int savedCount,
        int skippedCount,
        List<Long> savedSourceGroupIds,
        List<Long> savedSourceIds,
        List<String> skippedExternalSourceIds,
        List<String> failedReasons
) {
}
