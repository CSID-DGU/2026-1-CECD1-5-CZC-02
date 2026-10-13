# Domain Enums

백엔드, DB, 프론트가 동일하게 이해해야 하는 주요 상태값입니다 (2026-10 코드 기준 갱신). 2학기 새 데이터의 값은 `contracts/*.schema.json`이 기준입니다.

## UserStatus

```java
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}
```

| 값 | 화면 의미 |
| --- | --- |
| `ACTIVE` | 활성 사용자 |
| `INACTIVE` | 비활성 사용자 |
| `DELETED` | 삭제된 사용자 |

## IntegrationProvider

```java
public enum IntegrationProvider {
    GMAIL,
    JANDI,
    SALESMAP
}
```

| 값 | 의미 |
| --- | --- |
| `GMAIL` | Gmail OAuth/API 연동 |
| `JANDI` | JANDI 연동 준비 |
| `SALESMAP` | Salesmap 연동 정보 |

## IntegrationStatus

```java
public enum IntegrationStatus {
    CONNECTED,
    DISCONNECTED,
    EXPIRED,
    ERROR
}
```

| 값 | 화면 의미 |
| --- | --- |
| `CONNECTED` | 연결됨 |
| `DISCONNECTED` | 연결되지 않음 |
| `EXPIRED` | 토큰 만료 |
| `ERROR` | 연동 오류 |

## SourceType

```java
public enum SourceType {
    EMAIL,
    JANDI_MESSAGE,
    MEETING_NOTE,
    MANUAL_INPUT
}
```

| 값 | 화면 의미 |
| --- | --- |
| `EMAIL` | Gmail |
| `JANDI_MESSAGE` | JANDI |
| `MEETING_NOTE` | 회의록 |
| `MANUAL_INPUT` | 직접 입력 |

## SourceStatus

```java
public enum SourceStatus {
    CREATED,
    COLLECTED,
    ANALYSIS_REQUESTED,
    ANALYZED,
    FAILED,
    DELETED
}
```

흐름:

```text
CREATED -> ANALYSIS_REQUESTED -> ANALYZED
COLLECTED -> ANALYSIS_REQUESTED -> ANALYZED
ANALYSIS_REQUESTED -> FAILED
(어느 상태든) -> DELETED   사용자가 원본을 삭제
```

| 값 | 화면 의미 |
| --- | --- |
| `CREATED` | 생성됨 |
| `COLLECTED` | 수집 완료 |
| `ANALYSIS_REQUESTED` | 분석 요청 |
| `ANALYZED` | 분석 완료 |
| `FAILED` | 실패 |
| `DELETED` | 사용자가 삭제 (분석 작업·목록에서 제외) |

## SourceDirection

```java
public enum SourceDirection {
    SENT,
    RECEIVED,
    UNKNOWN
}
```

| 값 | 의미 |
| --- | --- |
| `SENT` | 내가 보낸 메일 |
| `RECEIVED` | 받은 메일 |
| `UNKNOWN` | 판단 불가 |

## AnalysisStatus

```java
public enum AnalysisStatus {
    REQUESTED,
    ANALYZING,
    ANALYZED,
    APPROVED,
    DELETED,
    REJECTED,
    FAILED
}
```

흐름:

```text
REQUESTED -> ANALYZING -> ANALYZED -> APPROVED
REQUESTED -> ANALYZING -> ANALYZED -> REJECTED
ANALYZING -> FAILED
APPROVED -> DELETED   연결된 일정을 삭제
```

| 값 | 화면 의미 |
| --- | --- |
| `REQUESTED` | 분석 요청 |
| `ANALYZING` | 분석 중 |
| `ANALYZED` | 승인 대기 |
| `APPROVED` | 등록됨 |
| `REJECTED` | 반려됨 |
| `DELETED` | 일정 삭제로 함께 삭제됨 (타임라인에서 제외) |
| `FAILED` | 분석 실패 |

## AnalysisActionType

```java
public enum AnalysisActionType {
    CREATE,
    UPDATE,
    CANCEL,
    CONFIRM,
    UNKNOWN
}
```

| 값 | 화면 의미 | 승인 후 처리 |
| --- | --- | --- |
| `CREATE` | 일정 생성 | Schedule/Google Calendar 생성 |
| `UPDATE` | 일정 변경 | Schedule/Google Calendar 수정 |
| `CANCEL` | 일정 취소 | Schedule/Google Calendar 삭제 |
| `CONFIRM` | 일정 확인 | 이력 저장 중심 |
| `UNKNOWN` | 확인 필요 | 일정 자동 반영 없음 |

## ScheduleStatus

```java
public enum ScheduleStatus {
    SCHEDULED,
    COMPLETED,
    CANCELED,
    REMINDED
}
```

| 값 | 화면 의미 |
| --- | --- |
| `SCHEDULED` | 진행 예정 |
| `COMPLETED` | 완료 |
| `CANCELED` | 취소 |
| `REMINDED` | 알림 완료 |

## ScheduleOutcome

미팅이 끝난 뒤 담당자가 남기는 결과입니다. 나중에 딜 성사 예측을 하게 되면 정답 라벨로 씁니다.

```java
public enum ScheduleOutcome {
    SUCCESS,
    ON_HOLD,
    LOST
}
```

| 값 | 화면 의미 |
| --- | --- |
| `SUCCESS` | 잘됨 |
| `ON_HOLD` | 보류 |
| `LOST` | 무산 |

최종 시연에서는 취소 승인 시 일정 상태만 바꾸는 것이 아니라 캘린더에서 제거되는 삭제 흐름을 사용합니다.

## SalesmapRecordStatus

```java
public enum SalesmapRecordStatus {
    REQUESTED,
    REGISTERED,
    FAILED,
    CANCELED
}
```

| 값 | 화면 의미 |
| --- | --- |
| `REQUESTED` | 등록 요청 |
| `REGISTERED` | 처리 완료 |
| `FAILED` | 처리 실패 |
| `CANCELED` | 취소 |

## CustomerActivityType

```java
public enum CustomerActivityType {
    AI_ANALYZED,
    SCHEDULE_CREATED,
    SCHEDULE_UPDATED,
    SCHEDULE_CANCELED,
    SALESMAP_REGISTERED,
    REPLY_SENT
}
```

| 값 | 타임라인 표시 |
| --- | --- |
| `AI_ANALYZED` | AI 분석 |
| `SCHEDULE_CREATED` | 일정 등록 |
| `SCHEDULE_UPDATED` | 일정 변경 |
| `SCHEDULE_CANCELED` | 일정 취소 |
| `SALESMAP_REGISTERED` | Salesmap 반영 |
| `REPLY_SENT` | 답장 발송 |

## businessType (AI 응답 문자열)

Java enum이 아니라 AI 모듈이 돌려주는 문자열로 `analyses.business_type`에 저장됩니다.

| 값 | 의미 |
| --- | --- |
| `SALES_ACTIVITY` | 영업 활동 메일 |
| `NON_BUSINESS` | 업무 외 메일 (고객 타임라인에 넣지 않음) |
| `UNKNOWN` | 판단 불가 |
