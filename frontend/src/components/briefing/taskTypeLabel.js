const TASK_TYPE_LABELS = {
  MEETING_PREP: '미팅 준비',
  VISIT_PREP: '방문·출장 준비',
  CONTRACT_PREP: '계약 준비',
  RENEWAL_INTERVIEW: '만료 협의 미팅',
  PROPOSAL_SUBMISSION: '제안서 제출',
  REJECTION_NOTICE: '거절 안내',
  DOC_COLLECTION: '서류 수집',
  END_EVALUATION: '종료 평가',
  PROFITABILITY_REVIEW: '수익성 검토',
  SOURCING_START: '인력 소싱 착수',
  NONE: '분류 없음',
};

export function taskTypeLabel(taskType) {
  return TASK_TYPE_LABELS[taskType] || (taskType ? '기타' : '분류 없음');
}
