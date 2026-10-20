export const TASK_TYPES = {
  MEETING_PREP: '미팅 준비', VISIT_PREP: '방문·출장 준비', CONTRACT_PREP: '계약 준비',
  RENEWAL_INTERVIEW: '만료 대응 면담', PROPOSAL_SUBMISSION: '제안서·인력 제출',
  REJECTION_NOTICE: '미채택 통보', DOC_COLLECTION: '서류 회수', END_EVALUATION: '종료 평가',
  PROFITABILITY_REVIEW: '수익성 검토', SOURCING_START: '인력 소싱 착수', NONE: '해당 없음',
};
export const STATUS_LABELS = { DRAFT: '확인 전', CONFIRMED: '할 일', DONE: '완료', CANCELLED: '취소' };
export const NEXT_STATUSES = {
  DRAFT: ['CONFIRMED', 'CANCELLED'], CONFIRMED: ['DONE', 'CANCELLED'], DONE: ['CONFIRMED'], CANCELLED: [],
};
export function kstToday(now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}
export function filterTasks(tasks, filters, today = kstToday()) {
  return tasks.filter(task => {
    if (filters.mine && (!filters.currentUserId || task.assignee !== filters.currentUserId)) return false;
    if (filters.person && task.counterpartPersonId !== filters.person) return false;
    if (filters.type && task.taskType !== filters.type) return false;
    if (filters.due === 'unknown' && task.dueDate !== null) return false;
    if (filters.due === 'today' && task.dueDate !== today) return false;
    if (filters.due === 'overdue' && (!task.dueDate || task.dueDate >= today || ['DONE', 'CANCELLED'].includes(task.status))) return false;
    return true;
  });
}
export function editedDocument(task, changes) {
  const next = { ...task, ...changes };
  if (next.dueDate !== task.dueDate) next.dueDateIsDefault = false;
  return next;
}
export function errorMessage(error) {
  return error?.response?.data?.message || error?.message || '처리하지 못했어요. 다시 시도해 주세요.';
}
