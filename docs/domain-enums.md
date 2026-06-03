# Domain Enums

백엔드, DB, 프론트가 동일하게 이해해야 하는 주요 상태값입니다.

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
    FAILED
}
```

흐름:

```text
CREATED -> ANALYSIS_REQUESTED -> ANALYZED
COLLECTED -> ANALYSIS_REQUESTED -> ANALYZED
ANALYSIS_REQUESTED -> FAILED
```

| 값 | 화면 의미 |
| --- | --- |
| `CREATED` | 생성됨 |
| `COLLECTED` | 수집 완료 |
| `ANALYSIS_REQUESTED` | 분석 요청 |
| `ANALYZED` | 분석 완료 |
| `FAILED` | 실패 |

## AnalysisStatus

```java
public enum AnalysisStatus {
    REQUESTED,
    ANALYZING,
    ANALYZED,
    APPROVED,
    REJECTED,
    FAILED
}
```

흐름:

```text
REQUESTED -> ANALYZING -> ANALYZED -> APPROVED
REQUESTED -> ANALYZING -> ANALYZED -> REJECTED
ANALYZING -> FAILED
```

| 값 | 화면 의미 |
| --- | --- |
| `REQUESTED` | 분석 요청 |
| `ANALYZING` | 분석 중 |
| `ANALYZED` | 승인 대기 |
| `APPROVED` | 등록됨 |
| `REJECTED` | 반려됨 |
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
