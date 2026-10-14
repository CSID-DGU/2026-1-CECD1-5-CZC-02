# 회의록 평가셋 (B-1)

회의록 엔진(`app/minutes/`, B-2)을 만들기 전에 정해 둔 "잘 만들었다"의 기준. 가짜 미팅 메모 10개와 사람이 직접 쓴 정답 `meeting_note`, 채점 스크립트로 이뤄진다. **데이터는 전부 가짜**다.

## 파일

| 경로 | 내용 |
| --- | --- |
| `cases.json` | 케이스 목록. `caseId`, 제목, 상황 태그, 엔진 입력(`input`: 원본 종류·시각·장소·만난 사람) |
| `memos/<caseId>.txt` | 영업 담당자가 쓴 메모 원문 (엔진 입력 텍스트) |
| `gold/<caseId>.json` | 정답 회의록. `contracts/meeting_note.schema.json`을 그대로 따른다 |
| `score.py` | 채점 스크립트 |

| 케이스 | 상황 |
| --- | --- |
| m01 | 약속 여러 개, 우려 없음, "이번 주 금요일" |
| m02 | 상대 2명, 우려 2개, "다음 달 초" |
| m03 | 잡담 위주(골프·입시·맛집), 다음 접촉 없음 |
| m04 | 짧은 통화, 장소 없음(`null`), 문항 3개 비어 있음 |
| m05 | **LIGHT** 연구소 순회 4명, 빈 칸 섞임 |
| m06 | **LIGHT** 전시회 부스 3명 + 잡담 |
| m07 | 상대 2명, 약속 5개(양쪽), "내일 오전"·"다음 주 수요일"·"월말" |
| m08 | 우려·요청만 있고 약속·다음 접촉 없음 |
| m09 | 화상회의, 회의와 무관한 기록 섞임 |
| m10 | 상대 3명, 모든 문항 채워짐 + 잡담 |

## 실행 (ai-module 폴더에서)

```bash
python -m eval.minutes.score --self                                  # 정답끼리 → 만점이어야 정상
python -m eval.minutes.score --pred eval/results/minutes_<실행이름>    # 엔진 결과 채점
python -m eval.minutes.score --pred <폴더> --verbose --json <폴더>/report.json
```

엔진 결과는 `<caseId>.json`(meeting_note) 10개를 한 폴더에 둔다. `eval/results/`는 git에 올라가지 않는다. 네트워크·LLM 호출이 없어서 CI 테스트(`tests/test_minutes_eval.py`)에서도 돈다.

## 지표

| 지표 | 계산 |
| --- | --- |
| 문항별 정밀도·재현율·F1 | 예측 항목과 정답 항목을 1:1로 짝지음. `text` 또는 `evidence`의 글자 bigram 유사도(Dice) 중 큰 값 ≥ 0.5면 같은 항목. LIGHT는 같은 사람 안에서만 짝지음. 전체는 마이크로 평균 |
| 근거 일치율 | 예측 항목 중 `evidence`가 원문에 그대로 있는 비율 (공백 차이만 무시) |
| missingFields 정확도 | 칸마다 "비어 있음" 판단이 정답과 같은 비율 + 회의록 단위 완전 일치율 |
| 참고 | 스키마 통과율, `mode` 일치율 |

## 정답 작성 규칙 (엔진도 이 규칙을 따른다)

- 항목 하나 = 사실·요청·약속 하나. `evidence`는 그 항목의 근거가 되는 **원문 부분 문자열 그대로** (메모의 `- ` 머리표는 빼도 됨)
- 회의와 무관한 잡담(골프, 맛집, 가족 소식, 화면 공유 끊김 등)은 어느 문항에도 넣지 않는다
- 기한 표현은 원문 그대로 둔다("이번 주 금요일", "다음 달 초"). 날짜 계산은 C의 규칙 코드가 한다
- `commitments`는 결과물이 있는 약속. 고객이 하기로 한 일은 끝에 `(고객 측)`
- `nextContact`는 다음 만남·연락 계획. 같은 문장을 `commitments`에 겹쳐 넣지 않는다
- 고객이 **내용을 알려주기로** 하면 `commitments`에 `(고객 측)`, **연락만 주기로** 하면 `nextContact` (예: m01 "결재 라인 정리해서 알려주신다고"·m07 "법무팀 검토 일정 확인해서 알려주신다고" → `commitments`, m09 "예산 확정되면 연락 주시기로" → `nextContact`)
- 걱정·불만·위험은 `concerns`, 고객이 원하는 것은 `customerRequests`, 그 밖의 오간 내용은 `discussion`
- `participants`는 입력으로 받은 만난 사람 그대로. 평가셋에서는 A의 식별 결과를 넣지 않으므로 `resolutionId`·`personId`는 `null`
- `status`는 `DRAFT`, `generatedBy`는 `HUMAN`(정답이므로)
- **FULL**: 빈 문항 이름을 `missingFields`에 (`["concerns"]`)
- **LIGHT** (여러 사람을 짧게 차례로 만난 방문): `fields` 5문항은 모두 빈 배열이고 `missingFields`에 넣지 않는다. 사람별 빈 칸만 `perPerson[<순서>].<칸>` 형식으로 적는다 (`["perPerson[1].request"]`). 채점할 때는 순서 대신 사람 이름으로 맞춰 본다
- `perPerson[n]`의 `n`은 `perPerson` 배열의 순서이며 **0부터** 센다 (첫 사람이 `perPerson[0]`)

정답을 고치거나 케이스를 추가하면 `python -m pytest tests/test_minutes_eval.py`로 스키마·근거·missingFields 규칙을 확인한다. 정답은 만든 사람이 아닌 팀원이 한 번 검수한다 (roles.md 공통 과업).
