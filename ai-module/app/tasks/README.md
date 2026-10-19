# C 할 일 추출기 1차

`extract_tasks(note, assignee=None)`는 `meeting_note` 딕셔너리를 받아
`task.schema.json`을 따르는 딕셔너리 목록을 반환한다. 입력·출력을 스키마로
검사하며 네트워크·LLM·DB를 사용하지 않는다.

```python
import json
from pathlib import Path
from app.tasks import extract_tasks

note = json.loads(Path("../contracts/examples/meeting_note.example.json").read_text(encoding="utf-8"))
tasks = extract_tasks(note, assignee="user_demo")
print(json.dumps(tasks, ensure_ascii=False, indent=2))
```

## 추출 규칙

- FULL: `commitments`, `nextContact`만 추출한다. 요청·논의는 약속으로 추측하지 않는다.
- LIGHT: 사람별 `followUp`만 추출하고 해당 사람의 `personId`를 유지한다.
- 고객 측 약속은 원문 내용을 유지하고 `이행 여부 확인`을 붙인 IMPLICIT 초안으로 만든다.
  확인 날짜의 기본값은 합의되지 않아 null이다. 종류도 NONE으로 둔다.
- 다른 항목은 EXPLICIT 초안이다. B가 후속 계획으로 정리한 항목을 입력으로 가정한다.
- 담당자는 호출자가 명시한 값만 사용한다. FULL에서 참석자가 둘 이상이면 상대 ID는 null이다.
- 근거는 입력 항목의 `evidence`를 그대로 보존한다. 같은 내용·근거·상대의 중복만 제거한다.
- ID는 회의록 ID + 목록 번호다. DB 저장 시 D-008에 따라 DB PK 기반 ID로 치환해야 한다.
  초안 재생성 ID는 영구 ID가 아니므로 수정 기록이 생긴 저장본을 덮어쓰면 안 된다.
- 모든 결과는 DRAFT다. 확정·저장·수정 기록·캘린더 등록은 수행하지 않는다.

## 날짜와 분류의 한계

날짜는 명시된 ISO 날짜, 접촉일 기준 오늘·내일·모레, 요일, 이번/다음/다다음 주 요일,
월말, 같은 해의 월·일을 처리한다. 단독 요일은 접촉일 당일을 포함한 다음 해당 요일이다.
첫 주·다음 달·조건부 표현·충돌하는 복수 날짜는 null이다. 기한 기본값을 넣지 않아
`dueDateIsDefault`는 false다. 날짜는 요약 `text`를 기준으로 계산하며 근거에서 별도 날짜를 추측하지 않는다.

11부류는 키워드 기준 모델이다. 출장과 미팅은 출장 우선, 계약서 회수와 작성은 회수 우선,
만료 면담은 일반 미팅보다 우선한다. 여러 종류가 충돌하거나 해당 규칙이 없으면 NONE이다.
`taskTypeConfidence`는 일치 규칙이 하나면 1.0, NONE이면 0.0인 **규칙 신뢰도**이며
통계적으로 보정된 확률이나 실측 정확도가 아니다. ML-2 성능 지표는 아직 측정하지 않았다.

API 등록·백엔드·화면·학습 모델은 후속 작업이다. 공용 파일이나 계약은 변경하지 않았다.

## 검증

ai-module에서 `python -m pytest app/tasks` 또는 공통 검사
`python -m pytest tests app ml`을 실행한다. 기존 B 정답 회의록 10건을 재사용해
FULL/LIGHT 추출 누락, 근거 보존, 출력 스키마도 검사한다. 이는 추출 성능 평가셋을 대신하지 않는다.
