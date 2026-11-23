# 인물 병합·되돌리기 (D-025·D-026)

사람 화면에서 유지할 대표 인물을 선택하고 **중복 인물 병합·기록**을 연다. 합칠 인물을 검색해 두 인물의 이름·소속·연락처를 비교하고 확인 체크 후 병합한다. 되돌리기도 감사 기록의 버튼을 누르고 한 번 더 확인해야 한다. 가상 화면 예시에는 쓰기 기능을 노출하지 않는다.

## 저장과 보존

- A `person_entries.merged_into_id`와 새 `person_merge_entries` 감사 테이블을 사용한다. JPA의 기존 ddl-auto=update 설정으로 반영한다. 기존 행의 merged_into_id는 null이다.
- 대표 이름·현재 소속·부서·직함·사진 여부는 대표 인물의 값을 유지한다. 등록 별칭(원래 이름 포함)·연락처·채널·명시된 이전 소속은 중복을 제거해 함께 조회·검색한다. 다른 현재 회사를 이전 회사였다고 추측하지 않는다. 서로 다른 스칼라 값은 원본 및 병합 전 감사 정보에서 확인할 수 있다.
- 두 인물의 원본 document·생성 정보는 수정하거나 삭제하지 않는다. 감사 기록에 병합 전 양쪽 정보와 mergedBy/mergedAt, 해제 시 undoneBy/undoneAt을 남긴다. 시각은 KST다.
- D-026에 따라 AI 제안 없는 수동 결정은 감사 기록이며, 학습용 revision에 가짜 RULE/MODEL 제안자를 넣지 않는다.
- 병합된 인물은 일반 목록·AI 비교 대장에서 빠지고 대표 인물로 합쳐진다. 과거 source ID의 상세 조회는 대표 인물을 반환한다.
- B 회의록 및 C·D 데이터의 원래 personId는 바꾸지 않는다. A 미팅 이력에서는 명시적으로 병합한 ID 집합만 함께 조회한다. 화면의 약속 기록도 같은 ID 집합을 사용한다. 이름이 같다는 이유로 이력을 합치지 않는다.
- 되돌리면 해당 연결만 해제하고 검색·비교 대장·A 미팅 이력이 다시 분리된다. 다른 병합 연결과 원본 문서·회의록을 덮어쓰지 않는다. C·D의 독자적인 인물별 조회를 바꾸는 작업은 포함하지 않는다.

## 관리 API (기존 Person·IdentityResolution 계약 변경 없음)

- POST `/api/persons/{targetPersonId}/merges`: `{ "sourcePersonId": "p_18", "confirmed": true }`
- GET `/api/persons/{personId}/merges?page=0&size=20`: `{ personIds, records }`를 기존 ApiResponse data로 반환한다. personIds는 현재 병합 그룹, records는 최신순 감사 기록이다. 최대 size=100.
- POST `/api/persons/{targetPersonId}/merges/{mergeId}/undo`: `{ "confirmed": true }`
- mergeId는 `pm_<DB PK>`. 사용자는 로그인 정보에서 가져오며 body의 소유자·실행자 지정은 받지 않는다. 타 사용자 데이터는 404, 미확인/잘못된 입력은 400, 중복·상태 충돌은 409다.
- 인물 쌍을 PK 오름차순으로 잠그고 READ_COMMITTED 쓰기 트랜잭션에서 연결·검색·감사 기록을 함께 커밋한다. 기록 저장 실패 시 연결도 롤백한다.

## 제한과 확인

한 대표 인물에 여러 원본 인물을 병합할 수 있다. 이미 다른 인물을 대표하는 그룹 전체를 다시 다른 대표로 합치거나 병합된 원본을 다시 합치는 작업은 거절한다. 기존 병합을 먼저 되돌린 후 대표를 선택한다. 따라서 순환·중첩 병합을 만들지 않는다.

확인 명령:

```bash
cd backend
bash ./gradlew test --tests '*Test'
python src/test/java/com/salesmap/backend/person/run_mysql_verification.py
cd ../frontend
node --test src/components/identity/*.test.mjs
npx eslint src/components/identity/PersonMergePanel.jsx src/components/identity/PeoplePage.jsx src/components/identity/peopleApi.js src/components/identity/peopleUtils.js
npm run build
```

MySQL 검증은 실행마다 새 임시 DB·한정 계정을 만들고 정리한다. 원본 보존, 검색·대장 복원, 미팅 원래 ID 보존, JWT/소유자, 동시 요청, 병합 및 되돌리기 감사 저장 실패의 롤백을 확인한다. 브라우저에서는 실제 PeoplePage와 가짜 API를 연결해 확인 체크 전 비활성, 병합·연락처/이력 결합, 되돌리기 및 감사 정보 표시를 검사했다. 실제 사용자 데이터나 유료 AI 호출을 사용하지 않았다.
