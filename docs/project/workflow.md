# 작업 방법 (사람용)

## AI 도구 쓰는 법

- **Claude Code**는 `CLAUDE.md`, **Codex**는 `AGENTS.md`를 자동으로 읽는다. `CLAUDE.md`는 `AGENTS.md`를 불러오기만 하므로 **두 도구가 같은 규칙**을 본다. 따로 "이거 읽어"라고 할 필요 없다.
- 작업을 시작할 때 **내 역할과 할 일을 첫 줄에** 말해 주면 AI가 맞는 문서를 찾아 읽는다.

### 프롬프트 예시

```
나는 A(인물 식별) 담당이야. contracts/person.schema.json과 identity_resolution.schema.json을 읽고,
가짜 인물 300명과 비교 쌍 3,000개를 만드는 스크립트를 ai-module/ml/identity/에 만들어 줘.
동명이인과 표기 변형(호칭, 영문, 오타, 소속 이동)을 섞고, 인물 단위로 학습/평가를 나눠 줘.
```

```
나는 C 담당이야. contracts/examples/meeting_note.example.json을 입력으로 받아
task.schema.json 형식의 할 일 목록을 내는 추출기를 ai-module/app/tasks/에 만들어 줘.
결과가 스키마에 맞는지 검사하는 테스트도 같이.
```

```
나는 D 담당이야. contracts/revision.schema.json 형식으로 수정 기록을 저장하는 API를
backend revision 패키지에 만들어 줘. 기존 analysis_revisions 테이블과의 관계도 정리해 줘.
```

### AI가 이상하게 굴 때

- 남의 폴더를 고치려 하거나 `contracts/`를 바꾸려 하면 **멈추고 이유를 물어보기**. 필요하면 팀에 공유 후 진행.
- 결과에 실제 사람·회사 이름이 들어가면 바로 지우기.

## 브랜치·커밋·PR

### 브랜치 이름 — 2학기는 `s2/`로 시작

```
s2/<담당>-<주제>
```

| 부분 | 규칙 | 예 |
| --- | --- | --- |
| `s2/` | 2학기 작업 표시 (1학기 `feature/…` 브랜치와 구분) | |
| 담당 | `a` · `b` · `c` · `d` · `common`(공용·총괄·여러 역할에 걸친 작업) | |
| 주제 | 영어 소문자, 단어는 `-`로 연결 | `identity-baseline`, `revision-api` |

예) `s2/a-identity-baseline`, `s2/b-minutes-prompt`, `s2/c-task-extractor`, `s2/d-revision-api`, `s2/common-ci-setup`

- 규칙에 안 맞는 이름으로 PR을 열면 **CI의 "브랜치 이름 규칙" 검사가 실패**한다. 이미 만든 브랜치는 `git branch -m s2/<담당>-<주제>`로 이름을 바꾼 뒤 다시 push.
- `feature/…`, `dev` 등 1학기 브랜치는 기록용으로 남겨 두고 **새 작업을 시작하지 않는다**. 2학기 작업은 항상 최신 `main`에서 `s2/` 브랜치를 만든다.

### 커밋·PR

- 커밋: 기존 형식 유지 `feat(ai): …`, `fix(backend): …`, `docs: …` (한국어 설명 OK)
- PR은 작게 자주. 템플릿의 체크리스트를 채운다.
- 데이터 형식을 바꾸는 PR은 제목에 `[contract]`, 영향 받는 사람 모두 승인 후 합친다.
- PR을 열면 개발 총괄이 `CODEOWNERS`로 자동 리뷰어가 된다.
- **`main`은 보호돼 있다.** 직접 push할 수 없고, ① 자동 검사 4개 통과 ② 개발 총괄 승인이 있어야 merge 버튼이 켜진다. 승인 뒤에 커밋을 더 올리면 승인이 풀리므로 다시 리뷰를 요청한다.

### 자동 검사 (GitHub Actions)

PR을 열거나 새 커밋을 push하면 4가지가 자동으로 돈다. 하나라도 빨간불이면 고친 뒤 다시 push.

| 검사 | 내용 |
| --- | --- |
| 브랜치 이름 규칙 | `s2/<담당>-<주제>` 형식인지 |
| AI 모듈 테스트 + 데이터 형식 검사 | `ai-module/tests` 전체 (`requirements-test.txt`만 설치하므로 테스트에서 새 라이브러리를 import하면 그 파일에도 추가) |
| 백엔드 단위 테스트 | 이름이 `...Test`로 끝나는 테스트 전부 (MySQL이 필요한 `BackendApplicationTests`는 제외) |
| 프론트엔드 빌드 | `npm run build`가 성공하는지 |

## 매주 할 일

1. **이어 붙이기**: 넷의 최신 결과를 실제로 연결해 샘플 입력 하나가 끝까지 도는지 확인
2. **결정 기록**: 이번 주에 정한 것을 `decisions.md`에 추가
3. **제약·한계 기록**: 못 한 것과 이유 (발표 3부 재료이자 인수인계 자료)

## 테스트

PR 전에 로컬에서 먼저 돌려 보면 빨간불을 줄일 수 있다.

```bash
# AI 모듈 (데이터 형식 검사 포함, 네트워크·유료 API 호출 없음)
cd ai-module
python -m pytest tests

# 데이터 형식만
python -m pytest tests/test_contracts.py
```

백엔드 테스트는 루트 `README.md`의 "테스트와 평가" 참고.
