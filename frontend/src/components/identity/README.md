# A 참석자 후보 선택 영역

D-017의 A 담당 영역이다. 기존 `minutes/ParticipantCandidatesPanel.jsx`는 이 컴포넌트로 넘기는 wrapper만 남겼다.
B의 `MeetingNoteReviewPage` 틀·문항·확정 로직과 C 할 일 코드는 수정하지 않았다.

## 구현한 것

- 서버에 저장된 후보 최대 3명·실제 인물 이름/소속·이유를 표시한다. RULE 점수를 확률로 표시하지 않는다.
- 본인 인물 대장의 직접 검색·이전/다음 페이지, 새 인물 입력 폼을 제공한다.
- 라디오 선택만으로 저장하지 않는다. 사용자가 명시적으로 선택 저장/신규 등록 버튼을 눌러야 한다.
- 신규 등록은 대표 이름과 데이터 유형을 직접 확인해야 한다. 모르는 소속은 null, 연락처는 []로 보낸다.
- 정보 부족 NEW는 확인 필요로 표시하고 바로 신규 등록을 유도하지 않는다. 직접 검색은 가능하다.
- 선택·신규 등록·수정 기록은 **서버의 선택 API 한 번**으로 요청한다. 브라우저에서 인물과 revision을
  따로 저장하거나 로컬 상태만 HUMAN으로 바꾸지 않는다.
- 저장 성공 응답에서만 HUMAN/저장됨을 표시한다. 다시 불러와도 서버 저장본을 사용한다.
- 선택 변경은 명시적 버튼으로 시작하고 버전 검사를 거친다. 실패하면 입력을 유지한다.
- 확정·보관·저장 중인 회의록은 읽기 전용이며 서버도 확정/보관 상태 변경을 거절한다.
- 이름·이메일 대응이 여러 명이면 첫 후보를 임의로 사용하지 않는다. 같은 resolutionId를 두 참석자에게 붙이지 않는다.
- 기존 샘플 fallback과 샘플 인물 라벨을 제거했다. 저장된 결과가 없으면 없다고 표시한다.

## 서버 연결

기존 JWT axios 클라이언트를 읽기만 하며 공용 파일은 수정하지 않았다.

| 호출 | 용도 |
| --- | --- |
| GET `/api/person-identities/for-note/{noteId}` | 해당 본인 회의록 원본의 저장된 식별 결과와 etag |
| GET `/api/persons/{personId}` | 후보의 실제 본인 인물 정보 |
| GET `/api/persons?q=...&page=0&size=20` | 본인 대장 직접 검색 |
| POST `/api/person-identities/for-note/{noteId}/{resolutionId}/selection` | 선택과 수정 기록을 함께 저장 |

선택에는 `If-Match`로 목록에서 읽은 etag가 필요하다. 요청은 `{ "personId": "p_17" }` 또는
`{ "newPerson": { "name": "가상인물_0001", "isSynthetic": true } }` 중 하나다.
사용자·proposedBy·원본 제안·HUMAN 플래그는 요청으로 받지 않고 서버가 정한다.

409는 자동 덮어쓰기·자동 재시도 없이 다시 불러오도록 안내한다. 기록 저장 실패도 성공처럼 표시하지 않는다.

## A 저장과 B 회의록 반영은 구분한다

현재 A는 자기 식별 저장소의 선택과 D 수정 기록을 저장한다.
**B 회의록의 participants/perPerson, C 할 일의 상대방을 직접 수정하지 않는다.**
따라서 저장 후에도 “회의록·할 일의 상대방은 아직 바뀌지 않았어요”라고 표시한다.
회신·확정 흐름까지 실사용 가능하다고 판단해서는 안 된다. B 담당이 선택을 회의록에 반영하는
호출 지점, 동명이인/이메일만 있는 참석자의 대응, 확인 화면 상태 갱신과 확정 전 검사 범위를 검토해야 한다.
이는 2번에서 남긴 B 검토 사항과 연결된다. 사용자 동의 없이 B 테이블을 A 코드에서 갱신하는 방식은 쓰지 않았다.

기존 노트나 mock 모드의 가짜 식별 결과는 A 저장소에 없는 것이 정상이다. 이 경우 수정 기록의
가짜 RULE 제안자를 만들어 저장하지 않고 “저장된 결과 없음”을 표시한다. 실제 HTTP 식별 연결에서
새 초안이 만들어지면 A가 검증한 제안을 저장하므로 화면에서 사용할 수 있다.

## 검사

frontend에서 기존 잠금 파일대로 의존성을 설치한 뒤 실행한다.

```bash
npm ci
node --test src/components/identity/*.test.mjs
npm exec -- eslint src/components/identity src/components/minutes/ParticipantCandidatesPanel.jsx
npm run build
```

초기 프론트 검사 12개: 불명확한 대응 방지·정보 부족·등록 입력·API 버전·실패/가짜 성공 거절·
검색/재조회와 후보·읽기 전용·HUMAN·미저장 결과의 실제 React 렌더를 검사한다.
SSR 렌더 검사는 WebSocket을 끄며 외부 네트워크가 필요 없다. 백엔드 서비스의 실제 MySQL 검사는 8개를 통과했다
([결과](../../../../backend/src/main/java/com/salesmap/backend/person/MYSQL_VERIFICATION.md)). JWT 필터부터 A API·격리 MySQL까지 추가 검증했다. 브라우저 클릭 통합은 아직 하지 않았다.
공용 CI 설정은 변경하지 않았다. 프론트 기본 CI는 기존과 같이 빌드를 실행하며, 위 Node 검사는 별도 명령이다.

## ⑤ 사람 화면 후속

목록·검색·상세·읽기 전용 확정 미팅 이력과 독립 개발 진입점은 [PEOPLE_SCREEN.md](PEOPLE_SCREEN.md)를 참고한다. 공용 메뉴/라우트는 연결됐으며 B/C 데이터 반영 경계가 남아 있다.

사용자 승인으로 공용 앱에 사람 화면을 연결했다. `/people`은 기존 로그인 보호와 DashboardLayout을 사용하며 공용 메뉴에 **사람**이 표시된다. 이전 독립 HTML은 `/people`로 이동한다. 화면 내부는 목록/상세 2열·타임라인 시안을 적용하고, 가상 예시 전환은 DB에 저장하지 않는다. 공용 변경은 `App.jsx`의 import/보호 경로와 `DashboardLayout.jsx`의 메뉴/aria-current 표시다.

2026-11-13 MODEL 후보 렌더 검사 추가: 모델 점수를 실제 업무 확률처럼 표시하지 않고 이유와 사용자 확인을 보여 준다. 현재 상태는 [HANDOFF](../../../../ai-module/ml/identity/HANDOFF.md)를 우선한다.
