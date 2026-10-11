package com.salesmap.backend.customer.dto;

import com.salesmap.backend.customer.entity.CustomerContact;

import java.time.LocalDateTime;

public record CustomerSummaryResponse(
        Long customerContactId,
        String customerName,
        String contactName,
        String email,
        String domain,
        LocalDateTime lastSeenAt,
        long activityCount
) {

    public static CustomerSummaryResponse from(CustomerContact contact, long activityCount) {
        return new CustomerSummaryResponse(
                contact.getId(),
                contact.getCustomerName(),
                contact.getContactName(),
                contact.getEmail(),
                contact.getDomain(),
                contact.getLastSeenAt(),
                activityCount
        );
    }
}
