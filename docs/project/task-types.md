# 할 일 11부류 정의 (초안 · 담당 C)

> ML-2의 정답 기준이자 준비 에이전트의 입구 규격. **C가 경계 예시를 채우고 B·D가 검토해서 확정**한다.
> 코드 값은 `contracts/task.schema.json`의 `taskType`과 같다.

| 코드 | 서비스 | 언제 이 종류인가 | 리드타임 | 구현 |
| --- | --- | --- | --- | --- |
| `MEETING_PREP` | S-01 고객 미팅 준비 브리핑 | 고객과 만나는 일정이 잡힘 | D-1 | **구현** |
| `VISIT_PREP` | S-02 현장 방문·출장 준비 | 지역 이동이 있는 방문·출장 (예: "광주 출장") | D-1 | **구현** |
| `CONTRACT_PREP` | S-03 계약 체결 준비 | 계약서 작성·송부·대조, 계약 유형 판정 | 투입일 역산 (1–2주) | **구현** |
| `RENEWAL_INTERVIEW` | S-04 계약 만료 대응 면담 | 계약 종료가 다가와 면담이 필요 | D-60/30/7 | 명세 |
| `PROPOSAL_SUBMISSION` | S-05 제안서·인력 리스트 제출 | 공고·인력 요청에 후보를 제출 | 마감 D-5 | 명세 |
| `REJECTION_NOTICE` | S-06 미채택 후보 통보 | 선정 결과 후 탈락 후보에게 알림 | 당일 | 명세 |
| `DOC_COLLECTION` | S-07 서류 취합·회수 독촉 | 보낸 서류·받을 서류가 안 돌아옴 | 발송 D+7/D+14 | 명세 |
| `END_EVALUATION` | S-08 종료 평가 작성 | 투입 종료 후 평가 작성 | D-0 | 명세 |
| `PROFITABILITY_REVIEW` | S-09 수익성 검토 | 조건 협의 전 마진 검토 | 협의 전 | 명세 (학생 범위 밖) |
| `SOURCING_START` | S-10 신규 인력 소싱 착수 | 인력 요청이 들어와 후보 탐색 시작 | 1영업일 | 명세 |
| `NONE` | 해당 없음 | 위 어디에도 안 맞음. **억지로 넣지 않는다** (쓸데없는 알림이 늘어남) | — | — |

## 헷갈리기 쉬운 경계 (예시를 채울 것)

| 쌍 | 구분 기준 (초안) | 예시 문장 → 정답 |
| --- | --- | --- |
| `MEETING_PREP` vs `VISIT_PREP` | 이동·출장·현장/설비 점검이 명시되면 VISIT, 단순 고객사 방문·논의는 MEETING (D-024) | "11월 첫 주 재방문", "고객 사무실 방문 준비" → MEETING_PREP / "다음 주 광주 현장 점검" → VISIT_PREP |
| 대상·목적 불명, 내부 회의 vs `NONE` | 고객인지·내부인지·이동인지 모르면 추측하지 않고 NONE. 내부 회의는 고객 자료를 다뤄도 NONE | "오늘 방문 준비", "고객 자료 검토 내부 미팅" → NONE |
| `CONTRACT_PREP` vs `DOC_COLLECTION` | 작성·송부·대조·협약 문서는 CONTRACT, 받아야 할 서류의 수집·반환·독촉은 DOC | "계약서 초안 금요일까지 송부", "계약서 다시 송부" → CONTRACT_PREP / "지난주 보낸 계약서 아직 회신 없음" → DOC_COLLECTION |
| `RENEWAL_INTERVIEW` vs `CONTRACT_PREP` | 재계약·갱신 의사 면담은 RENEWAL, 재계약서 작성은 CONTRACT | "재계약 면담 포인트 정리" → RENEWAL_INTERVIEW / "재계약서 초안 작성" → CONTRACT_PREP |
| `END_EVALUATION` vs `NONE` | 인력 투입·프로젝트 종료 평가만 END. 월간 영업 성과·하루 업무 평가는 NONE | "투입 종료 평가 작성" → END_EVALUATION / "주말 영업 활동 종합 평가" → NONE |
| `SOURCING_START` vs `NONE` / `PROPOSAL_SUBMISSION` | 인력 요청 후 후보 탐색 착수만 SOURCING. 공급업체·자재 탐색은 NONE. 찾은 후보 제출은 PROPOSAL | "채용 포털에서 후보 검색" → SOURCING_START / "새 공급업체 탐색" → NONE / "후보 프로필 전달" → PROPOSAL_SUBMISSION |
| `REJECTION_NOTICE` vs `NONE` | 미채택 결과·사유를 알려야 REJECTION. 단순 재연락은 NONE | "탈락 결과 통보" → REJECTION_NOTICE / "미채택 후보에게 다시 연락" → NONE |
| `PROPOSAL_SUBMISSION` vs `NONE` | 제출 준비·작성·송부는 PROPOSAL, 이미 냈는지 확인만 하면 NONE | "제안서 준비" → PROPOSAL_SUBMISSION / "제안서 제출 확인" → NONE |
| `PROFITABILITY_REVIEW` vs `CONTRACT_PREP` | 마진·마진율·수익률 계산은 계약 조건이 배경이어도 PROFITABILITY | "계약 조건별 수익성 분석" → PROFITABILITY_REVIEW |
| 암묵적 후속 vs `NONE` | 상대의 "검토해 보겠다"는 우리 쪽 준비물이 없으면 NONE (기한만 관리) | "투입 시기는 내부 검토 후 회신" → NONE |
| 여러 행위가 섞인 항목 | B에서 약속을 나누는 게 원칙. 안 나뉜 복합 항목은 NONE으로 두고 사람이 확인 | "계약서 작성 및 인력 소싱" → NONE |

## 데이터 만들기 규칙

- 학습용: LLM으로 부류별 예문 60–100개 생성 → **다른 팀원이 교차 검수**. 검수자 간 불일치 20% 이상인 부류는 정의부터 다시 본다.
- 평가용: 학습용과 **출처를 분리**한다 (같은 방식으로 만든 예문으로 평가하면 점수가 부풀려진다). 실제 발화 가명 예문을 받으면 평가 전용으로만 쓴다.
- 프롬프트에 실제 고객명·인명을 넣지 않는다.
