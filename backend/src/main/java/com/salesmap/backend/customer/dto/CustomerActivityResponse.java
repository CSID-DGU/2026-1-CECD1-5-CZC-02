package com.salesmap.backend.customer.dto;

import com.salesmap.backend.customer.entity.CustomerActivity;
import com.salesmap.backend.customer.entity.CustomerActivityType;

import java.time.LocalDateTime;

public record CustomerActivityResponse(
        Long activityId,
        CustomerActivityType activityType,
        String title,
        String description,
        Long sourceId,
        Long analysisId,
        Long scheduleId,
        Long salesmapRecordId,
        LocalDateTime occurredAt,
        LocalDateTime createdAt
) {

    public static CustomerActivityResponse from(CustomerActivity activity) {
        return new CustomerActivityResponse(
                activity.getId(),
                activity.getActivityType(),
                activity.getTitle(),
                activity.getDescription(),
                activity.getSource() == null ? null : activity.getSource().getId(),
                activity.getAnalysis() == null ? null : activity.getAnalysis().getId(),
                activity.getSchedule() == null ? null : activity.getSchedule().getId(),
                activity.getSalesmapRecord() == null ? null : activity.getSalesmapRecord().getId(),
                activity.getOccurredAt(),
                activity.getCreatedAt()
        );
    }
}
