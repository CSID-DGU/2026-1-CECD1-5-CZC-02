package com.salesmap.backend.taskstatus;

import java.time.LocalDateTime;
import java.util.List;

/** 규칙이 만든 상태 변경 제안. 저장하지 않고 조회할 때 계산하며, 사람이 눌러야 상태가 바뀐다 (D-025). */
public record TaskStatusSuggestion(
        String taskId,
        String content,
        String currentStatus,
        String suggestedStatus,
        String sourceId,
        String sourceTitle,
        LocalDateTime sourceSentAt,
        List<String> matchedKeywords,
        String reason
) {
}
