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

- 브랜치: `feature/<역할>-<주제>` 예) `feature/a-identity-baseline`, `feature/d-revision-api`
- 커밋: 기존 형식 유지 `feat(ai): …`, `fix(backend): …`, `docs: …` (한국어 설명 OK)
- PR은 작게 자주. 템플릿의 체크리스트를 채운다.
- 데이터 형식을 바꾸는 PR은 제목에 `[contract]`, 영향 받는 사람 모두 승인 후 합친다.

## 매주 할 일

1. **이어 붙이기**: 넷의 최신 결과를 실제로 연결해 샘플 입력 하나가 끝까지 도는지 확인
2. **결정 기록**: 이번 주에 정한 것을 `decisions.md`에 추가
3. **제약·한계 기록**: 못 한 것과 이유 (발표 3부 재료이자 인수인계 자료)

## 테스트

```bash
# AI 모듈 (데이터 형식 검사 포함, 네트워크·유료 API 호출 없음)
cd ai-module
python -m pytest tests

# 데이터 형식만
python -m pytest tests/test_contracts.py
```

백엔드 테스트는 루트 `README.md`의 "테스트와 평가" 참고.
