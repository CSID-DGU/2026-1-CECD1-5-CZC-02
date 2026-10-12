# contracts — 모듈 사이 데이터 형식

A·B·C·D가 서로 주고받는 데이터의 **유일한 기준**입니다. 각자 코드는 이 형식대로 내보내고, 이 형식대로 받는다고 가정하고 만듭니다.

## 흐름과 파일

```
A 인물 식별 ──identity_resolution──▶ B 회의록 ──meeting_note──▶ C 할 일 ──task──▶ D 일정·브리핑
     │ person (인물 대장)                                                          ▲
     └──────────── A·B·C 확인 화면 ──revision (ML-4 수정 기록)──────────────────────┘
```

| 파일 | 만드는 사람 → 쓰는 사람 | 한 줄 설명 |
| --- | --- | --- |
| `person.schema.json` | A → A·B·C·D | 인물 대장 한 명 (ML-1 비교 쌍의 원본) |
| `identity_resolution.schema.json` | A → B | 인물 언급 하나의 식별 결과. 확정 / 후보(최대 3) / 신규 |
| `meeting_note.schema.json` | B → C | 표준 회의록. 모든 항목에 원문 근거, 모르는 칸은 비워 두고 표시 |
| `task.schema.json` | C → D | 할 일 (무엇을·언제까지·누가 + 11부류 종류) |
| `revision.schema.json` | A·B·C → D | 사람이 확인·수정한 기록. 재학습 정답 |
| `briefing` (예정) | D → 화면 | D가 정의해서 추가 |

`examples/`에는 형식마다 가짜 샘플이 있고, 샘플끼리 ID가 이어지는 하나의 시나리오입니다 (홍길동 부장 미팅 → 회의록 → 할 일 3건 → 수정 기록 2건). **앞사람 코드가 없을 때 이 샘플을 가짜 입력으로 쓰세요.**

## 공통 규칙

- 키 이름은 **camelCase** (백엔드 API와 같음)
- ID는 접두어 + 문자열: `p_` 인물, `ir_` 식별 결과, `mn_` 회의록, `t_` 할 일, `rv_` 수정 기록, `src_` 원본
  - DB에 저장된 것은 **접두어 + DB 숫자 PK** (예: person PK 17 → `p_17`, source PK 101 → `src_101`). 받는 쪽은 접두어를 떼면 PK
  - DB에 넣기 전 가짜 데이터·샘플은 임의 번호 (`p_001`)
- 날짜 `YYYY-MM-DD`, 시각 `YYYY-MM-DDTHH:mm:ss` (KST, 시간대 표기 없음. `+09:00` 붙이지 않음)
- 모르는 값은 **추측하지 말고 `null`** (배열이면 빈 배열)
- 내용을 뽑은 항목에는 **근거 원문(`evidence`)을 그대로** 붙인다
- 레포에 들어가는 데이터는 **전부 가짜**. 실제 사람·회사처럼 보이는 이름도 피한다

## 바꾸고 싶을 때

1. 혼자 바꾸지 않습니다. 팀 채팅에 "무엇을, 왜" 먼저 공유
2. 스키마 + `examples/` 샘플을 같이 고치고, `docs/project/decisions.md`에 한 줄 기록
3. PR 제목에 `[contract]`를 붙이고 **영향 받는 사람 모두 승인** 후 합치기
4. 검사: `cd ai-module && python -m pytest tests/test_contracts.py`
