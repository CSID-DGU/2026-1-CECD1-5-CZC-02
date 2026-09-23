package com.salesmap.backend.analysis.service;

import com.salesmap.backend.analysis.entity.Analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 사용자가 수정할 수 있는 분석 필드 목록. AI 원본 스냅샷과 수정 이력 비교에 같은 기준을 쓴다.
 */
final class AnalysisEditableFields {

    private static final Map<String, Function<Analysis, Object>> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("businessType", Analysis::getBusinessType);
        FIELDS.put("actionType", Analysis::getActionType);
        FIELDS.put("customerName", Analysis::getCustomerName);
        FIELDS.put("contactName", Analysis::getContactName);
        FIELDS.put("productName", Analysis::getProductName);
        FIELDS.put("amount", Analysis::getAmount);
        FIELDS.put("attendees", Analysis::getAttendees);
        FIELDS.put("targetScheduleTitle", Analysis::getTargetScheduleTitle);
        FIELDS.put("actionReason", Analysis::getActionReason);
        FIELDS.put("scheduleText", Analysis::getScheduleText);
        FIELDS.put("followUpAction", Analysis::getFollowUpAction);
        FIELDS.put("summary", Analysis::getSummary);
    }

    private AnalysisEditableFields() {
    }

    /** 필드명 → 문자열 값. 빈 문자열은 null로 통일해 "값 없음"끼리는 같은 값으로 본다. */
    static Map<String, String> valuesOf(Analysis analysis) {
        Map<String, String> values = new LinkedHashMap<>();
        FIELDS.forEach((name, getter) -> values.put(name, normalize(getter.apply(analysis))));
        return values;
    }

    private static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
