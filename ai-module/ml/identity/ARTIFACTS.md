# ML-1 평가 자료 보관 — PR #48 후속

리뷰 대상 세 JSON은 총 1,306,074바이트였다. 필요한 학습 보고서와 회귀 검사 스냅샷만 남긴 현재 비교 대상은
397,471바이트로 약 69.6% 줄었다. 모델 계수·판정 기준·정답·평가 결과는 변경하지 않았다.

| 자료 | 처리 | 이유 |
| --- | --- | --- |
| fresh dataset.json (463,207바이트) | Git에서 제거, 고정 seed로 재생성 | 240명·560질문을 모델 없이 생성하고 원 datasetSha256 일치 확인 가능 |
| fresh predictions.json (457,570바이트) | Git에서 제거, 앞 56건의 기존 logistic 결과만 runtime_predictions.json에 보관 | 14유형의 기존 회귀 검사를 유지한다. 새 엔진으로 기대 정답을 만들지 않았다 |
| controlled report.json (385,297바이트) | 원본 유지 | 모델 설정·분할 해시·원 최종 성능·라이브러리 버전·코드 해시가 계수 내보내기와 재현에 필요하다. sourceReportSha256을 유지한다 |

`runtime_predictions.json`은 기존 예측 목록을 추출한 기록이다. 원 전체 예측의 의미적 SHA-256,
자료 해시·seed·그룹 수와 56건의 queryId/decision/personId/candidateIds를 보존한다.
테스트는 원 자료 해시, 스냅샷의 정확한 질문 집합, 전체 자료의 고정 평가 지표를 검사한다.
부스팅 등 전체 실험 결과 요약과 frozen_protocol도 그대로 보존한다.
원 보고서를 요약해 덮어쓰면 운영 계수의 출처 해시와 재현 검증이 깨지므로 이 파일은 삭제/재작성하지 않았다.

## 재생성

ai-module에서 실행한다. 출력 파일/폴더가 이미 있으면 중단하며 Git에 자동 추가하지 않는다.

```bash
# 기본 테스트 의존성만 사용. 모델 학습/네트워크 없음
python -m ml.identity.reproduce_fresh_data --output /tmp/a-fresh-dataset.json

# 원 고정 모델을 재현해 모든 모델 예측·보고서를 생성 (원 실험의 sklearn 버전 필요)
python -m ml.identity.fresh_validation --output-dir /tmp/a-fresh-full
```

첫 명령은 원 `frozen_protocol.json`의 seed와 해시를 사용한다. 두 번째 명령은 원 fit/calibration만 사용하며
새 평가 자료로 모델·기준을 다시 고르지 않는다. 시간/환경에 의존하는 성능 측정은 원 기록과 동일하다고 가정하지 않는다.
앞으로 생성 규칙이나 모델을 바꾸면 원 해시 일치 검사가 실패해야 한다. 기존 frozen 기록을 바꿔서 통과시키지 않는다.
정확한 과거 원본 파일은 main에 병합된 #48의 `b1874e7` 커밋에도 보존돼 있다.
