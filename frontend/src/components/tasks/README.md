# C 할 일 화면

## 독립 실행

frontend에서 `npm ci`, `npm run dev` 실행 후 `/src/components/tasks/preview.html`을 연다.
가짜 데이터 체험은 메모리 안에서만 동작한다. 새로고침하면 사라진다.
`내 할 일`은 기존 JWT API를 사용하므로 같은 origin의 기존 `/login`에서 먼저 로그인해야 한다.
기존 backend(MySQL 연결)와 C AI 서버(8001)도 실행한다. 기존 로그인 계정의 실제 고객 데이터를
새 샘플이나 평가 데이터로 복사하지 않는다.

```bash
# frontend에서
npm run build
npx vite build --config src/components/tasks/vite.config.js
node --test src/components/tasks/taskUtils.test.mjs
npx eslint src/components/tasks/*.jsx src/components/tasks/taskUtils.js src/components/tasks/taskApi.js src/components/minutes/TaskDraftPanel.jsx
```

독립 빌드는 `dist/tasks`에 출력된다. 공용 App.jsx·메뉴·vite 설정은 수정하지 않았다.

## 연결

- `TasksPage`: 목록, 로그인 사용자 조회, 회의록에서 가져오기, 직접 추가, 필터·수정·확정·상태 관리.
- `TaskBoard`: `tasks`, `currentUserId`, `onSave`, `onSelectEvidence`, `people` props.
- `TaskReviewPanel`: `tasks`, `readOnly`, `onSave`, `selectedEvidence`, `onSelectEvidence`, `people`, `currentUserId` props.
- 기존 C 자리인 `minutes/TaskDraftPanel.jsx`는 위 컴포넌트를 감싼다.
  B가 **저장된** task와 `onSave`를 전달하기 전에는 조회만 가능하며 샘플 fallback은 없다.

`onSave(task)`는 수정 기록까지 영구 저장한 뒤 resolve해야 한다. 거절하면 입력을 유지하고
오류를 표시한다. TasksPage는 조회 때 받은 ETag를 If-Match로 보내고 충돌 시 자동 덮어쓰지 않는다.
종류 변경의 HUMAN 메타데이터와 수정 기록은 서버에서 처리한다. 원문 근거는 수정하지 않는다.

계약에 없는 진행 중 상태나 상태 자동 변경 제안은 추가하지 않았다. 필터의 날짜는 KST로 계산한다.
알림이나 외부 발송은 없다. 취소 항목은 선택해서 볼 수 있다.

## 선택적 브라우저 검증

Playwright를 로컬 별도 경로에 설치하고 Chrome을 이용한다. 공용 package.json은 바꾸지 않는다.

```bash
npm install --prefix /tmp/czc-browser-tools playwright --no-save
# 별도 터미널: npx vite --host 127.0.0.1 --port 5175
NODE_PATH=/tmp/czc-browser-tools/node_modules node src/components/tasks/browser-check.cjs
```

필요하면 CHROME_PATH, TASK_PREVIEW_URL을 지정한다. 가짜 데이터 상호작용과 HTTP 응답을
대체한 실제 TasksPage의 ETag·409·입력 유지·재시도를 검사한다. 실제 DB 통합 검증은 아니다.

인물 목록은 person 계약의 personId/name을 사용한다. 현재 사용자 이외의 사용자 디렉터리가 없으므로
담당자는 미정/나 선택과 기존 배정 보존을 제공한다. 현재 상대 이름을 모르면 확인 필요로 표시하며
원시 ID를 사람이 입력하도록 요구하지 않는다. 회의록은 B의 목록 API에서 제목·접촉일로 선택한다.
