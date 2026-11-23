package com.salesmap.backend.person;

import java.time.LocalDateTime;
import java.util.List;

/** 인물 계약과 분리한 A 관리 API의 감사 메타데이터. */
public record PersonMergeView(String mergeId,String sourcePersonId,String targetPersonId,
        PersonDocument sourceBefore,PersonDocument targetBefore,Long mergedBy,LocalDateTime mergedAt,
        Long undoneBy,LocalDateTime undoneAt,boolean active) {
    public record History(List<String> personIds,List<PersonMergeView> records) {}
}
