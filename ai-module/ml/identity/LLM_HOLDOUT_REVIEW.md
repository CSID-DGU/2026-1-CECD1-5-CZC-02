# B holdout 36문장 — A 교차 검수 의견

> 2026-11-22, A 작업 세션의 AI 보조 검수. 팀원 개인이 직접 독립 검수·승인했다고 표시한 기록은 아니다.

## 대상과 방법

- 대상: [B holdout.json](../llm_baseline/holdout.json)의 36문장. 원래 label과 why가 보이는 상태에서 문장 의미를 기준 문서와 대조했다. 블라인드 라벨링이 아니다.
- 기준: [task-types.md](../../../docs/project/task-types.md)의 정의·경계표와 [decisions.md D-024](../../../docs/project/decisions.md). 새 분류 규칙을 임의로 만들지 않았다.
- 모델 예측·비교 결과 파일을 읽지 않았고 Ollama·유료 API·새 학습을 실행하지 않았다. 이 검수는 모델 성능 평가가 아니다.
- B 평가셋의 text·label·why·reviewStatus와 C 데이터·공용 문서는 수정하지 않았다. 검수 의견은 A 폴더에만 남긴다.
- 아래 해시는 이 로컬 검수 시점의 파일 바이트를 식별한다. 모델 실행 전 고정 이력·학습 자료와의 독립성 전체를 새로 인증한 것은 아니다.

| 파일 | SHA-256 |
| --- | --- |
| `ml/llm_baseline/holdout.json` | `10a786e7a5f33074d8d6b9c10b7be12e77abda220ea3e7c49693c6ff3d2fb8a9` |
| `docs/project/task-types.md` | `c8601c9d55f34758a6cf0dd699ec343a6aa5ccddc14e19b260aaf8946f327218` |

## 결과

**현재 분류 기준에 동의 36건 / 라벨 변경 의견 0건 / 라벨 판단 유보 0건 / 표현 보완 의견 1건.**
10개 업무 부류는 각 3문장, NONE은 경계 사례 6문장이다. 이 숫자는 원래 라벨과 이번 검수 의견의 일치 건수이며 모델 정확도나 사람 간 일치율이 아니다. 모든 실제 업무 문장의 정답이 검증됐다는 뜻도 아니다.

| 문장 ID | 원래 label | 검수 label | 판단 | 기준·근거 |
| --- | --- | --- | --- | --- |
| `ho_meeting_prep_0` | `MEETING_PREP` | `MEETING_PREP` | 동의 | 고객사 담당자와 잡힌 대면 미팅의 견적 이력 준비. 출장·현장 점검이 아님. |
| `ho_meeting_prep_1` | `MEETING_PREP` | `MEETING_PREP` | 동의 | 발주처 PM과 정기 미팅이 명시됨. 고객이라는 단어 유무보다 대상·행위를 봄. |
| `ho_meeting_prep_2` | `MEETING_PREP` | `MEETING_PREP` | 동의 | 고객을 만날 일정과 논의 목적이 있음. 인력 규모를 듣는 것은 아직 후보 탐색 착수가 아님. |
| `ho_visit_prep_0` | `VISIT_PREP` | `VISIT_PREP` | 동의 | 지역 이동(KTX)과 현장 실사가 명시됨. D-024의 이동·현장 경계에 해당. |
| `ho_visit_prep_1` | `VISIT_PREP` | `VISIT_PREP` | 동의 | 지역 이동을 수반한 공장 설비 확인 및 숙박 준비. 단순 고객 미팅이 아님. |
| `ho_visit_prep_2` | `VISIT_PREP` | `VISIT_PREP` | 동의 | 지방 지점을 도는 출장 일정 준비가 명시됨. |
| `ho_contract_prep_0` | `CONTRACT_PREP` | `CONTRACT_PREP` | 동의 | 협의된 조건을 계약서 문구에 반영하는 작성·수정. 마진 계산이나 회수 독촉이 아님. |
| `ho_contract_prep_1` | `CONTRACT_PREP` | `CONTRACT_PREP` | 동의 | 계약서 최종본 송부. 문서가 등장해도 받아야 할 서류 수집이 아님. |
| `ho_contract_prep_2` | `CONTRACT_PREP` | `CONTRACT_PREP` | 동의 | 표준 계약서와 수정본을 조항별로 대조. CONTRACT 정의의 대조에 해당. |
| `ho_renewal_interview_0` | `RENEWAL_INTERVIEW` | `RENEWAL_INTERVIEW` | 동의 | 계약 종료를 앞둔 인력의 연장 의사를 묻는 면담. 재계약서 작성이 아님. |
| `ho_renewal_interview_1` | `RENEWAL_INTERVIEW` | `RENEWAL_INTERVIEW` | 동의 | 계약 갱신 시점의 연장 면담 일정. 일반 신규 고객 미팅과 구분됨. |
| `ho_renewal_interview_2` | `RENEWAL_INTERVIEW` | `RENEWAL_INTERVIEW` | 동의 | 만료 대응 면담의 처우 질문 준비. 계약서 문서 작업이 아님. |
| `ho_proposal_submission_0` | `PROPOSAL_SUBMISSION` | `PROPOSAL_SUBMISSION` | 동의 | 입찰 마감까지 제안서를 완성해 제출. 제출 여부 확인만 하는 문장과 구분됨. |
| `ho_proposal_submission_1` | `PROPOSAL_SUBMISSION` | `PROPOSAL_SUBMISSION` | 동의 | 고객 요청에 맞춘 개발자 이력서 제출. 후보를 새로 찾는 단계가 아님. |
| `ho_proposal_submission_2` | `PROPOSAL_SUBMISSION` | `PROPOSAL_SUBMISSION` | 동의 | 공고 요건에 맞춰 추천 인력 명단을 제출. 명단 정리는 제출 행위의 준비 단계. |
| `ho_rejection_notice_0` | `REJECTION_NOTICE` | `REJECTION_NOTICE` | 동의 | 선정되지 않았다는 결과를 지원자에게 전달함. 단순 재접촉이 아님. |
| `ho_rejection_notice_1` | `REJECTION_NOTICE` | `REJECTION_NOTICE` | 동의 | 탈락 결과와 사유를 후보에게 통보. 메일 발송 채널이 분류를 바꾸지 않음. |
| `ho_rejection_notice_2` | `REJECTION_NOTICE` | `REJECTION_NOTICE` | 동의 | 고객 인터뷰 후 미채택 결과 통보. 경계표의 미채택 알림과 일치. |
| `ho_doc_collection_0` | `DOC_COLLECTION` | `DOC_COLLECTION` | 동의 | 받아야 할 재직증명서·자격증 사본을 수집. 경계표는 수집도 DOC에 포함하므로 지연이 없어도 해당. |
| `ho_doc_collection_1` | `DOC_COLLECTION` | `DOC_COLLECTION` | 동의 | 보낸 서약서가 돌아오지 않아 재요청. 작성·최초 송부가 아니라 회수 독촉. |
| `ho_doc_collection_2` | `DOC_COLLECTION` | `DOC_COLLECTION` | 동의 | 받기로 한 서명·날인본의 미반환 독촉. 계약 문서 작성으로 분류하지 않음. |
| `ho_end_evaluation_0` | `END_EVALUATION` | `END_EVALUATION` | 동의 | 평가 대상이 철수 인력의 프로젝트 수행임. D-024의 종료 평가 범위에 해당하며 작성 시점은 아래 보완 의견 참조. |
| `ho_end_evaluation_1` | `END_EVALUATION` | `END_EVALUATION` | 동의 | 투입이 끝난 개발자에 대한 종료 후 평가. 일반 월간 영업 성과 평가가 아님. |
| `ho_end_evaluation_2` | `END_EVALUATION` | `END_EVALUATION` | 동의 | 종료된 운영 지원 과제의 인력별 수행 평가. 프로젝트 종료 평가로 명시됨. |
| `ho_profitability_review_0` | `PROFITABILITY_REVIEW` | `PROFITABILITY_REVIEW` | 동의 | 제시 단가로 남는 금액을 따지는 것은 마진 검토. 계약서 작성 자체가 아님. |
| `ho_profitability_review_1` | `PROFITABILITY_REVIEW` | `PROFITABILITY_REVIEW` | 동의 | 인건비 변화에 따른 이익률 계산. 수익률 계산 기준에 직접 해당. |
| `ho_profitability_review_2` | `PROFITABILITY_REVIEW` | `PROFITABILITY_REVIEW` | 동의 | 단가 협상 전 원가·매출 비교로 수익성 검토. 계약 조건이 배경이어도 PROFITABILITY. |
| `ho_sourcing_start_0` | `SOURCING_START` | `SOURCING_START` | 동의 | 고객의 인력 요청에 맞는 후보를 찾기 시작. 제출 단계와 구분됨. |
| `ho_sourcing_start_1` | `SOURCING_START` | `SOURCING_START` | 동의 | 구인 사이트·인력 풀에서 경력자 탐색. 경계표의 채용 포털 후보 검색과 같은 유형. |
| `ho_sourcing_start_2` | `SOURCING_START` | `SOURCING_START` | 동의 | 신규 인력 요청 뒤 후보군 구성에 착수. 명시적인 인력 소싱 시작. |
| `ho_none_0` | `NONE` | `NONE` | 동의 | 내부 팀의 영업 실적 공유 회의. 고객 자료나 회의라는 단어만으로 MEETING으로 넣지 않음. |
| `ho_none_1` | `NONE` | `NONE` | 동의 | 노트북 공급업체 탐색. SOURCING은 인력 후보 탐색에 한정하므로 NONE. |
| `ho_none_2` | `NONE` | `NONE` | 동의 | 이미 제출한 제안서의 접수 상태 확인만 함. 새 제출 준비·작성·송부가 아님. |
| `ho_none_3` | `NONE` | `NONE` | 동의 | 미채택 후보에게 새 공고를 안내하는 재접촉. 탈락 결과 통보나 후보 탐색 착수를 추측하지 않음. |
| `ho_none_4` | `NONE` | `NONE` | 동의 | 제안서 송부와 수익성 표 작성이라는 별도 두 행위. 미분리 복합 항목은 NONE으로 두고 분리 확인. |
| `ho_none_5` | `NONE` | `NONE` | 동의 | 외근 대상·목적·지역 이동·현장 점검이 불명. 고객 미팅이나 출장으로 추측하지 않음. |

## 표현 보완 의견 — 라벨 유지

`ho_end_evaluation_0`: “이달 말 철수하는 인력의 프로젝트 수행 평가서를 작성한다”.

END_EVALUATION은 투입·프로젝트 종료 평가라는 대상이 분명하므로 유지한다. 다만 정의표는 “투입 종료 후 평가 작성”이라고 하고 문장은 철수 시점과 작성 시점의 관계를 확정하지 않는다. 작성 시점까지 평가하려는 자료라면 B·C가 종료 후 작성임을 더 분명히 표현할지 검토할 수 있다. 이번 검수에서는 이를 잘못된 라벨이나 임의의 NONE으로 바꾸지 않는다.

예시 보완안: “이달 말 철수가 끝난 인력의 프로젝트 수행 평가서를 작성한다.” 기존 고정 평가 문장을 실제로 바꾸는 경우에는 B가 변경 이력·평가셋 버전을 남겨야 하며, 과거 결과와 같은 자료인 것처럼 비교하지 않는다.

## 경계 확인과 전달할 사항

- `ho_doc_collection_0`: 지연된 서류만 DOC라고 좁히지 않는다. 경계표는 받아야 할 서류의 수집도 포함한다.
- `ho_sourcing_start_1`: 인력 풀·구인 사이트의 후보 탐색은 기준 예시에 해당한다. 고객 요청 문구가 생략됐다는 이유만으로 NONE으로 바꾸지 않는다.
- `ho_none_3`: 탈락 후보에게 새 공고를 안내하는 재접촉이다. 탈락 결과 통보나 실제 후보 제출로 추측하지 않는다.
- `ho_none_4`: 서로 다른 행위를 분리하기 전에는 복합 항목이므로 NONE을 유지한다. 가능하면 앞 단계에서 두 할 일로 분리해야 한다.
- B 원본의 reviewStatus는 여전히 검수 전이다. 이 보고서만으로 “팀원의 사람 교차 검수 완료”라고 메타데이터를 바꾸지 않는다. 담당자는 AI 보조 의견임을 확인한 뒤 검수 상태와 기록 방식을 정한다.

## 확인

36개 원본 ID의 누락·중복 없음, 모든 검수 label이 기준 문서의 11개 부류 안에 있음, 원본 바이트 유지와 문서 링크를 확인했다. 기존 holdout 테스트로 부류 구성·문장 중복 및 C 자료/기준 예시와의 겹침 검사를 확인한다. 기본 테스트는 네트워크나 모델을 호출하지 않는다.

```bash
cd ai-module
python -m pytest ml/llm_baseline/test_llm_baseline_holdout.py -q
```
