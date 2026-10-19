import { TaskReviewPanel } from '../tasks/TaskReviewPanel';

/** C 영역. B가 저장된 task와 onSave를 연결하면 편집·확정을 활성화한다. */
export function TaskDraftPanel({ tasks = [], selectedEvidence, readOnly, onSelectEvidence, onSave, people, currentUserId }) {
  return <TaskReviewPanel tasks={tasks} selectedEvidence={selectedEvidence}
    readOnly={readOnly} onSelectEvidence={onSelectEvidence} onSave={onSave} people={people} currentUserId={currentUserId} />;
}
