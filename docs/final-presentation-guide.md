# Final Presentation Guide

이 문서는 팀원이 최종 발표 자료를 만들거나, 다른 Codex에게 프로젝트 전체를 읽히고 발표 준비를 시킬 때 기준으로 사용할 최종 가이드입니다.

## 1. 프로젝트 소개

프로젝트명:

```text
SALESMAP 활동 자동화 AI Agent
```

문제:

```text
영업 담당자는 Gmail, JANDI, 회의록 등 여러 채널에서 발생한 영업 활동을 Salesmap에 직접 기록해야 한다.
이 과정은 반복적이고 누락 위험이 크다.
```

해결:

```text
커뮤니케이션 데이터를 자동 수집하고,
AI가 일정/고객사/제품/후속조치 정보를 추출한 뒤,
사용자가 확인 및 수정 후 승인하면
내부 캘린더와 Salesmap TODO에 반영한다.
```

## 2. 최종 시연 핵심 메시지

발표에서 강조할 메시지:

1. Gmail 실제 OAuth 연동을 통해 메일을 수집한다.
2. Gmail thread를 묶어서 대화 흐름 단위로 AI 분석한다.
3. FastAPI AI 모듈이 일정 생성/변경/취소 의도를 판단한다.
4. 사용자는 분석 결과를 그대로 등록하거나 직접 수정할 수 있다.
5. 승인 후 내부 Dashboard 캘린더에 반영된다.
6. Google Calendar 이벤트를 생성/수정/삭제한다.
7. Salesmap은 Google Calendar 양방향 연동을 통해 TODO가 자동 반영된다.

## 3. 최종 아키텍처

```text
React + Vite Frontend
  -> Spring Boot Backend
      -> MySQL
      -> Gmail API
      -> FastAPI AI Module
      -> Google Calendar API
          -> Salesmap Calendar Sync
              -> Salesmap TODO
```

기술 스택:

- Frontend: React, Vite, Axios
- Backend: Spring Boot, Java 17, Gradle, JPA, MySQL, Spring Security, JWT
- AI Module: Python, FastAPI
- External: Gmail API, Google Calendar API, Salesmap Calendar Sync

## 4. 구현된 사용자 흐름

```text
1. 회원가입/로그인
2. 설정 화면에서 Gmail 연결
3. Gmail 새로고침으로 메일 수집
4. 수집된 메일 목록에서 메일 선택
5. 메일 상세 확인
6. AI 분석 실행
7. 분석 결과 확인 및 수동 수정
8. 일정 등록/변경/삭제 승인
9. Dashboard 캘린더 반영
10. Google Calendar 반영
11. Salesmap TODO 반영
```

## 5. 화면별 발표 포인트

### 로그인 화면

- JWT 인증 기반 로그인
- 회원가입 후 바로 서비스 접근 가능

### 설정 화면

- Gmail 연결 상태 표시
- Gmail 연결/해제
- Google Calendar 경유 Salesmap 연동 안내
- Salesmap 연동 상태 표시

### Gmail/메일 화면

- 실제 Gmail에서 수집한 메일 목록 표시
- 긴 메일도 카드 레이아웃이 깨지지 않도록 처리
- 같은 발신자의 최근 메일 확인 가능

### 메일 상세 및 AI 분석

- 메일 본문 확인
- AI 분석 실행
- 분석 결과를 한글 라벨로 표시
- actionType을 배지로 표시
- 분석 결과 수동 수정 가능

### Dashboard

- 내부 일정 캘린더
- 오늘 일정/다가오는 일정
- 일정 유형별 색상
- 일정 직접 수정/삭제
- Google Calendar/Salesmap 연동 결과 확인

### Salesmap

- Google Calendar 양방향 연동 설정 후 TODO 자동 반영 확인
- 직접 TODO API 없이도 캘린더 경유로 연동 가능함을 보여줌

## 6. AI actionType 설명

| actionType | 화면 표시 | 의미 | 승인 후 동작 |
| --- | --- | --- | --- |
| `CREATE` | 일정 생성 | 새 미팅/상담/회의 요청 | Schedule 생성, Google Calendar 이벤트 생성 |
| `UPDATE` | 일정 변경 | 기존 일정 시간/내용 변경 | Schedule 수정, Google Calendar 이벤트 수정 |
| `CANCEL` | 일정 취소 | 기존 일정 취소 요청 | Schedule 삭제, Google Calendar 이벤트 삭제 |
| `CONFIRM` | 일정 확인 | 일정 확정/참석 가능 | 분석 이력 저장 중심 |
| `UNKNOWN` | 확인 필요 | 일반 영업 메일 | 일정 자동 생성 없음 |

## 7. 시연용 추천 메일

### 일정 생성

```text
제목: 2026-06-12 Nimbus Tech 도입 상담 미팅 요청

안녕하세요.
Nimbus Tech 김도윤입니다.

2026년 6월 12일 오전 10시에 CRM Automation 서비스 도입 상담 미팅을 요청드립니다.
참석자는 Nimbus Tech 김도윤, 영업팀 이민재입니다.

가능하시면 일정 등록 부탁드립니다.
감사합니다.
```

기대:

- 고객사: Nimbus Tech
- 제품: CRM Automation
- 처리 유형: 일정 생성
- Dashboard/Google Calendar/Salesmap TODO에 일정 생성

### 일정 변경

```text
제목: Nimbus Tech 도입 상담 미팅 일정 변경 요청

기존 2026년 6월 12일 오전 10시 미팅을
2026년 6월 12일 오후 2시로 변경 부탁드립니다.

참석자는 동일합니다.
감사합니다.
```

기대:

- 처리 유형: 일정 변경
- 기존 일정 시간이 변경됨

### 일정 취소

```text
제목: Nimbus Tech 도입 상담 미팅 취소 요청

2026년 6월 12일 오전 10시에 예정되어 있던
Nimbus Tech CRM Automation 서비스 도입 상담 미팅은 취소 부탁드립니다.

내부 일정 조정으로 이번 미팅은 진행하지 않겠습니다.
감사합니다.
```

기대:

- 처리 유형: 일정 취소
- 등록된 일정 삭제
- Google Calendar 이벤트 삭제
- Salesmap TODO 삭제 또는 동기화 해제

### 일반 영업 메일

```text
제목: 견적서 전달드립니다

안녕하세요.
요청하신 견적서를 첨부드립니다.
검토 후 문의사항 있으시면 연락 부탁드립니다.
감사합니다.
```

기대:

- 처리 유형: 확인 필요
- 일정 생성 없음

## 8. 발표 스토리 예시

```text
기존 Salesmap은 영업 활동을 사람이 직접 기록해야 합니다.
저희는 Gmail에서 영업 관련 메일을 자동으로 수집하고,
AI가 미팅 생성/변경/취소 의도를 분석한 뒤,
사용자가 결과를 확인하고 승인하면 캘린더와 Salesmap에 반영되는 시스템을 만들었습니다.

특히 Salesmap TODO 직접 생성 API가 제공되지 않는 문제는
Google Calendar 양방향 연동을 활용해 해결했습니다.
즉, 우리 서비스가 Google Calendar 이벤트를 만들면,
Salesmap이 이를 자동으로 TODO로 가져오는 방식입니다.
```

## 9. 팀원 Codex에게 줄 프롬프트

다른 팀원의 Codex에게 발표 자료 준비를 시킬 때 아래 프롬프트를 그대로 사용할 수 있습니다.

```text
현재 프로젝트는 Dongguk University 종합설계 프로젝트
"SALESMAP 활동 자동화 AI Agent"입니다.

레포 전체 파일을 읽고, 특히 docs 폴더의 다음 문서를 먼저 읽어주세요.

- docs/final-presentation-guide.md
- docs/backend-status.md
- docs/e2e-test-flow.md
- docs/frontend-api-guide.md
- docs/api-spec.md
- docs/db-design.md
- docs/domain-enums.md

이 프로젝트는 Gmail OAuth로 실제 메일을 수집하고,
FastAPI AI 모듈로 메일을 분석한 뒤,
사용자가 분석 결과를 수정/승인하면
내부 Dashboard 일정과 Google Calendar 이벤트에 반영합니다.
Salesmap은 Google Calendar 양방향 연동을 통해 TODO가 자동 생성/수정/삭제되는 구조입니다.

최종 발표 자료를 준비하기 위해 다음을 정리해주세요.

1. 프로젝트 문제 정의
2. 해결 아이디어
3. 전체 아키텍처
4. 주요 기능
5. Gmail 수집 흐름
6. AI 분석 흐름
7. 사용자 승인 및 수동 수정 흐름
8. Google Calendar 경유 Salesmap 연동 방식
9. 시연 순서
10. 한계와 향후 개선점

발표자는 개발자가 아닌 청중도 이해할 수 있게 설명해야 하므로,
기술 설명과 사용자 가치 설명을 함께 정리해주세요.
```

## 10. 발표 전 체크리스트

- [ ] MySQL 실행
- [ ] Backend 실행
- [ ] FastAPI 실행
- [ ] Frontend 실행
- [ ] Gmail OAuth 연결 확인
- [ ] Google Calendar 권한 scope 포함 확인
- [ ] Salesmap Calendar 양방향 연동 확인
- [ ] CREATE 메일 분석 성공
- [ ] UPDATE 메일 분석 성공
- [ ] CANCEL 메일 분석 성공
- [ ] Dashboard 반영 확인
- [ ] Google Calendar 반영 확인
- [ ] Salesmap TODO 반영 확인

## 11. 남은 한계

- JANDI 실제 연동은 보류 상태
- Salesmap TODO 직접 쓰기 API는 없음
- Salesmap 반영은 Google Calendar 동기화 지연에 영향을 받을 수 있음
- 운영 환경에서는 OAuth 토큰 암호화 저장과 refresh 정책 보강 필요
- AI 분석은 규칙 기반 보정이 포함되어 있어 더 다양한 메일 패턴 학습이 필요
