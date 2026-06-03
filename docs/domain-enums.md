# Domain Enums

SALESMAP 활동 자동화 AI Agent에서 사용하는 상태값과 enum 정책 초안입니다.

이 문서는 backend, DB, frontend가 같은 상태값을 사용하기 위한 기준입니다. 현재는 설계 문서이며 backend 코드는 아직 수정하지 않았습니다.

## Enum Policy

- 상태값은 대문자 snake case를 사용합니다. 예: `ANALYSIS_REQUESTED`
- API 응답과 요청에서는 enum 이름을 문자열로 전달합니다.
- Java에서는 domain별 enum 클래스로 관리하는 것을 권장합니다.
- DB에는 초기에는 `VARCHAR(50)`로 저장하고, 상태값이 안정되면 DB enum 또는 check constraint 적용을 검토합니다.
- frontend는 표시 문구를 별도로 매핑하고, API 값 자체는 변경하지 않습니다.
- 같은 의미의 상태값을 도메인마다 다르게 만들지 않습니다. 예: 실패 상태는 기본적으로 `FAILED`를 사용합니다.

## users.status

사용자 계정의 사용 가능 상태를 나타냅니다.

상태 흐름:

```text
ACTIVE -> INACTIVE -> ACTIVE
ACTIVE -> DELETED
INACTIVE -> DELETED
```

상태값:

| Status | Meaning | Changed When | Next Status | Related API Flow |
| --- | --- | --- | --- | --- |
| ACTIVE | 정상 사용 가능한 사용자 | 회원가입 완료, 비활성 계정 복구 | INACTIVE, DELETED | 추후 로그인, 사용자 조회, 외부 계정 연동 |
| INACTIVE | 일시적으로 비활성화된 사용자 | 사용자 휴면 처리, 관리자의 비활성 처리 | ACTIVE, DELETED | 추후 로그인 제한, 계정 복구 |
| DELETED | 탈퇴 또는 삭제 처리된 사용자 | 회원 탈퇴, 관리자 삭제 | 없음 | 추후 사용자 데이터 보관/익명화 정책과 연결 |

Java enum 예시:

```java
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}
```

## integrations.status

외부 서비스 연동 상태를 나타냅니다.

상태 흐름:

```text
CONNECTED -> EXPIRED -> CONNECTED
CONNECTED -> DISCONNECTED
CONNECTED -> ERROR -> CONNECTED
EXPIRED -> DISCONNECTED
ERROR -> DISCONNECTED
```

상태값:

| Status | Meaning | Changed When | Next Status | Related API Flow |
| --- | --- | --- | --- | --- |
| CONNECTED | 외부 서비스 연동이 정상 동작 중 | Gmail, Jandi, Salesmap 계정 연동 성공 | EXPIRED, ERROR, DISCONNECTED | 추후 외부 계정 연동, 원본 데이터 수집 |
| DISCONNECTED | 사용자가 연동을 해제한 상태 | 연동 해제 요청 처리 | CONNECTED | 추후 연동 해제, 재연동 |
| EXPIRED | 토큰 만료로 연동 갱신이 필요한 상태 | access token 또는 refresh token 만료 감지 | CONNECTED, DISCONNECTED | 추후 토큰 갱신, 재인증 |
| ERROR | 외부 API 오류 또는 인증 오류 상태 | 외부 API 호출 실패, 권한 오류 발생 | CONNECTED, DISCONNECTED | 추후 연동 상태 확인, 재시도 |

Java enum 예시:

```java
public enum IntegrationStatus {
    CONNECTED,
    DISCONNECTED,
    EXPIRED,
    ERROR
}
```

## sources.status

이메일, 메시지, 회의록 등 원본 데이터의 처리 상태를 나타냅니다.

상태 흐름:

```text
CREATED -> ANALYSIS_REQUESTED -> ANALYZED
COLLECTED -> ANALYSIS_REQUESTED -> ANALYZED
CREATED -> FAILED
COLLECTED -> FAILED
ANALYSIS_REQUESTED -> FAILED
```

상태값:

| Status | Meaning | Changed When | Next Status | Related API Flow |
| --- | --- | --- | --- | --- |
| CREATED | 사용자가 직접 생성했거나 API로 입력된 원본 데이터 | `POST /api/sources` 요청 성공 | ANALYSIS_REQUESTED, FAILED | Source 생성 후 AI 분석 요청 |
| COLLECTED | 외부 서비스에서 수집된 원본 데이터 | Gmail, Jandi 등에서 데이터 수집 성공 | ANALYSIS_REQUESTED, FAILED | 추후 외부 연동 수집 API |
| ANALYSIS_REQUESTED | AI 분석 요청이 접수된 상태 | `POST /api/analysis` 요청 시 source 기준으로 분석 요청 | ANALYZED, FAILED | Analysis 생성 |
| ANALYZED | 원본 데이터 분석이 완료된 상태 | AI 분석 성공 후 결과 저장 | 없음 | `GET /api/analysis/{analysisId}` 결과 조회 |
| FAILED | 원본 데이터 처리 또는 분석 요청 실패 | 수집 실패, 분석 요청 실패 | ANALYSIS_REQUESTED | 추후 재시도 |

Java enum 예시:

```java
public enum SourceStatus {
    CREATED,
    COLLECTED,
    ANALYSIS_REQUESTED,
    ANALYZED,
    FAILED
}
```

## analyses.status

AI 분석 요청과 분석 결과의 상태를 나타냅니다.

상태 흐름:

```text
REQUESTED -> ANALYZING -> ANALYZED -> APPROVED
REQUESTED -> ANALYZING -> FAILED
ANALYZED -> REJECTED
ANALYZED -> APPROVED
FAILED -> REQUESTED
```

상태값:

| Status | Meaning | Changed When | Next Status | Related API Flow |
| --- | --- | --- | --- | --- |
| REQUESTED | 분석 요청이 생성된 상태 | `POST /api/analysis` 요청 접수 | ANALYZING, FAILED | AI module 호출 전 |
| ANALYZING | AI module이 분석 중인 상태 | AI module 호출 시작 | ANALYZED, FAILED | 추후 비동기 분석 처리 |
| ANALYZED | AI 분석 결과가 생성된 상태 | AI module 응답 성공 | APPROVED, REJECTED | `GET /api/analysis/{analysisId}`, 사용자 검토 |
| APPROVED | 사용자가 분석 결과를 승인한 상태 | 사용자가 검토 후 승인 | 없음 | `POST /api/salesmap/register`, `POST /api/schedules` |
| REJECTED | 사용자가 분석 결과를 거절한 상태 | 사용자가 검토 후 반려 | REQUESTED | 추후 재분석 요청 |
| FAILED | 분석 실패 상태 | AI module 오류, 타임아웃, 파싱 실패 | REQUESTED | 추후 재시도 |

Java enum 예시:

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

## schedules.status

일정과 리마인드의 처리 상태를 나타냅니다.

상태 흐름:

```text
SCHEDULED -> REMINDED -> COMPLETED
SCHEDULED -> COMPLETED
SCHEDULED -> CANCELED
REMINDED -> CANCELED
```

상태값:

| Status | Meaning | Changed When | Next Status | Related API Flow |
| --- | --- | --- | --- | --- |
| SCHEDULED | 일정이 등록된 상태 | `POST /api/schedules` 요청 성공 | REMINDED, COMPLETED, CANCELED | 일정 생성, 캘린더 표시 |
| REMINDED | 리마인드 알림을 보낸 상태 | reminder 시간이 지나 알림 발송 성공 | COMPLETED, CANCELED | 추후 리마인드 처리 |
| COMPLETED | 일정 또는 후속 조치가 완료된 상태 | 사용자가 완료 처리 | 없음 | 추후 일정 완료 API |
| CANCELED | 일정이 취소된 상태 | 사용자가 취소 처리 | 없음 | 추후 일정 취소 API |

Java enum 예시:

```java
public enum ScheduleStatus {
    SCHEDULED,
    COMPLETED,
    CANCELED,
    REMINDED
}
```

## salesmap_records.status

Salesmap 등록 요청과 등록 결과의 상태를 나타냅니다.

상태 흐름:

```text
REQUESTED -> REGISTERED
REQUESTED -> FAILED
FAILED -> REQUESTED
REQUESTED -> CANCELED
```

상태값:

| Status | Meaning | Changed When | Next Status | Related API Flow |
| --- | --- | --- | --- | --- |
| REQUESTED | Salesmap 등록 요청이 생성된 상태 | `POST /api/salesmap/register` 요청 접수 | REGISTERED, FAILED, CANCELED | Salesmap API 호출 전 |
| REGISTERED | Salesmap 등록이 완료된 상태 | Salesmap API 응답 성공, external record ID 저장 | 없음 | 등록 이력 조회 |
| FAILED | Salesmap 등록 실패 상태 | Salesmap API 오류, 네트워크 오류, validation 오류 | REQUESTED, CANCELED | 추후 재시도 |
| CANCELED | 등록 요청이 취소된 상태 | 사용자가 취소하거나 운영 정책상 중단 | 없음 | 추후 등록 취소 처리 |

Java enum 예시:

```java
public enum SalesmapRecordStatus {
    REQUESTED,
    REGISTERED,
    FAILED,
    CANCELED
}
```

## Cross-Domain Flow

현재 API 흐름 기준 전체 상태 전이 예시입니다.

```text
sources.CREATED
  -> analyses.REQUESTED
  -> analyses.ANALYZING
  -> analyses.ANALYZED
  -> analyses.APPROVED
  -> salesmap_records.REQUESTED
  -> salesmap_records.REGISTERED
```

일정 생성 흐름:

```text
analyses.ANALYZED or analyses.APPROVED
  -> schedules.SCHEDULED
  -> schedules.REMINDED
  -> schedules.COMPLETED
```

외부 연동 수집 흐름:

```text
integrations.CONNECTED
  -> sources.COLLECTED
  -> sources.ANALYSIS_REQUESTED
  -> sources.ANALYZED
```

## Implementation Notes

- Java enum을 도입하면 DTO 응답의 `status` 필드는 enum 타입으로 선언할 수 있습니다.
- DB에는 enum name을 그대로 저장하는 방식을 권장합니다. 예: `@Enumerated(EnumType.STRING)`
- enum ordinal 저장은 금지합니다. enum 순서 변경 시 데이터 의미가 깨질 수 있습니다.
- frontend는 API enum 값을 기준으로 badge 색상과 표시 문구를 매핑합니다.
- 상태 변경 API를 만들 때는 Service 계층에서 가능한 다음 상태인지 검증해야 합니다.
