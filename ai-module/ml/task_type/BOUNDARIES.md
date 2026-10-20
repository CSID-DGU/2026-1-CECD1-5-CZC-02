# C 부류 경계 제안 — #25 리뷰 후속

이 문서는 C 구현·라벨 정리 기준이다. B·D 검토 후 총괄이 task-types.md와 decisions.md에
반영할 제안이며 공용 문서나 contracts는 직접 수정하지 않았다.

| 경계 | C 제안 / 이번 처리 | 예시 |
| --- | --- | --- |
| MEETING / VISIT | 이동·출장·현장/설비 점검이 명시되면 VISIT. 단순 고객사 사무실 방문·논의는 MEETING | 고객 회사 방문 / 고객 사무실 방문 → 둘 다 MEETING. 지역 이동·현장 점검 → VISIT |
| 정보 부족 미팅·방문 | 고객인지, 내부인지, 이동인지 모르면 추측하지 않음. 반환은 NONE/0.0, 학습 후보는 NONE + trainingEligible=false | visit_prep_7·49, meeting_prep_20·54 |
| 내부 회의 | 고객 자료를 다루더라도 내부 회의 자체는 S-01 고객 미팅으로 분류하지 않음 | 고객 자료 검토를 위한 내부 미팅 → NONE |
| CONTRACT / DOC | 작성·송부·대조·협약 문서 준비는 CONTRACT. 받아야 할 서류의 수집·취합·반환·독촉은 DOC | 계약서를 다시 송부 → CONTRACT / 서명본 반환 독촉 → DOC |
| RENEWAL / CONTRACT | 재계약·갱신 의사 면담은 RENEWAL. 재계약서라는 문서의 작성은 CONTRACT | 재계약 면담 / 재계약서 초안 작성 |
| END / 일반 평가 | 인력 투입·프로젝트 종료 시 수행 평가 작성만 END. 월간 영업 성과·하루 업무 종료 평가는 제외 | end_evaluation_6 → NONE. _33은 월간 평가로 목적이 불명확해 학습 제외 |
| SOURCING / 공급업체 탐색 | S-10은 인력 요청 후 사람·후보를 찾는 착수. 자재·업체·공급망은 NONE | 기존 sourcing_start_0~59 → NONE, 인력 소싱 v2 60개 추가 |
| SOURCING / PROPOSAL | 후보를 찾기와 찾은 후보/인력 리스트 제출 구별 | 인력 후보 탐색 / 후보 프로필 제출 |
| REJECTION / 재접촉 | 미채택 결과·사유를 알리는 행위가 있어야 REJECTION. 단순 연락·재접촉은 NONE | rejection_notice_6 → NONE |
| PROPOSAL / 상태 확인 | 제출 준비·작성·송부는 PROPOSAL. 이미 제출/접수됐는지 확인만 하면 NONE | 제안서 준비 → PROPOSAL / none_29의 제출 확인 → NONE |
| PROFITABILITY / CONTRACT | 마진·마진율·수익률·수익성 계산은 PROFITABILITY. 계약 조건이 배경이라고 CONTRACT로 충돌시키지 않음 | 계약 조건별 수익성 분석 → PROFITABILITY |
| 복수 독립 행위 | B에서 약속을 나누는 것이 원칙. 분리되지 않은 복합 항목은 사람이 확인 | 계약서 작성 및 인력 소싱 → NONE |

정보 부족을 확정된 NONE 학습 정답으로 강제하지 않는다. 해당 후보는 원문·ID와 함께 남기되
trainingEligible=false로 제외하고, 다른 팀원의 검수 후 다시 포함할 수 있다.

미팅 범위를 보수적으로 바꿔 추출 회귀셋의 '다음 달 초 미팅' 1건도 종류 NONE으로 동기화했다.
이는 extraction.json(개발 회귀셋)의 기대 종류 변경이며, 교차 검수된 evaluation.json 55개는 변경하지 않았다.
일정·기한은 그대로 null로 유지한다.

## 총괄에게 제안할 결정 한 줄

> S-01/S-02는 고객사 사무실 논의와 이동·출장·현장 점검을 구별한다. 대상·목적 불명은 NONE으로 표시하고
> 학습에서는 보류한다. S-08은 투입/프로젝트 종료 평가, S-10은 인력 후보 탐색 착수로 한정하며,
> 미채택 재접촉·제출 상태 확인은 결과 통보·제출 준비로 추측하지 않는다.

training-review.json의 C 예비 정정은 독립 교차 검수 완료를 뜻하지 않는다.
