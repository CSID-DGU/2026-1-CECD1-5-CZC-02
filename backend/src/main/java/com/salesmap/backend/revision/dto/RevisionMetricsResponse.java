package com.salesmap.backend.revision.dto;

import java.util.List;

public record RevisionMetricsResponse(
        long decisionTargetCount,
        long acceptedWithoutEditTargetCount,
        double acceptedWithoutEditRate,
        List<FieldMetric> fields
) {
    public record FieldMetric(
            String fieldName,
            long decisionCount,
            long editedCount,
            double editRate
    ) {
    }
}
