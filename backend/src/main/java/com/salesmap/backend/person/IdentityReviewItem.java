package com.salesmap.backend.person;

import com.fasterxml.jackson.databind.JsonNode;

/** etag는 HTTP 편집 메타데이터이고 resolution 내부는 기존 계약 그대로다. */
public record IdentityReviewItem(JsonNode resolution,String etag) {}
