package com.salesmap.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.salesmap.backend.source.entity.SourceDirection;

import java.time.LocalDateTime;
import java.util.List;

public record AiSourceMessagePayload(
        Long sourceId,
        String externalSourceId,
        SourceDirection direction,
        String senderName,
        String senderEmail,
        List<String> receiverNames,
        List<String> receiverEmails,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime sentAt,
        String content
) {
}
