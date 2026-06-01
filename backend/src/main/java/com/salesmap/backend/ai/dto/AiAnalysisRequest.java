package com.salesmap.backend.ai.dto;

import com.salesmap.backend.source.entity.SourceType;

import java.time.LocalDateTime;
import java.util.List;

public record AiAnalysisRequest(
        Long sourceId,
        SourceType sourceType,
        String externalSourceId,
        String title,
        String content,
        LocalDateTime collectedAt,
        RequesterInfo requester,
        SourceGroupInfo sourceGroup,
        List<MessageItem> messages,
        List<ExistingScheduleInfo> existingSchedules
) {

    public record RequesterInfo(
            Long userId,
            String name,
            String email
    ) {
    }

    public record SourceGroupInfo(
            String groupId,
            SourceType sourceType,
            String title,
            Boolean deduplicated
    ) {
    }

    public record MessageItem(
            Long sourceId,
            String externalSourceId,
            String direction,
            String senderName,
            String senderEmail,
            List<String> receiverNames,
            List<String> receiverEmails,
            LocalDateTime sentAt,
            String content
    ) {
    }

    public record ExistingScheduleInfo(
            Long scheduleId,
            String title,
            LocalDateTime scheduleDateTime,
            List<String> participants
    ) {
    }
}
