package com.salesmap.backend.customer.dto;

import java.util.List;

public record CustomerTimelineResponse(
        CustomerSummaryResponse customer,
        List<CustomerActivityResponse> activities
) {
}
