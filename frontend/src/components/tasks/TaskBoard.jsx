import { useState } from 'react';
import { TASK_TYPES, STATUS_LABELS, filterTasks } from './taskUtils';
import { TaskReviewPanel } from './TaskReviewPanel';

export function TaskBoard({ tasks, currentUserId, onSave, onSelectEvidence, people = [] }) {
  const [filters, setFilters] = useState({ mine: false, person: '', type: '', due: '' });
  const [showCancelled, setShowCancelled] = useState(false);
  const set = (field, value) => setFilters(old => ({ ...old, [field]: value }));
  const visible = filterTasks(tasks, { ...filters, currentUserId });
  const linkedPeople = [...new Set(tasks.map(t => t.counterpartPersonId).filter(Boolean))];
  const control = 'rounded-lg border border-slate-200 bg-white p-2 text-sm';
  return <section className="space-y-5 text-left">
    <div className="flex flex-wrap items-center gap-3">
      <label className="text-sm"><input type="checkbox" checked={filters.mine} disabled={!currentUserId} onChange={e => set('mine', e.target.checked)} /> 내 할 일</label>
      <select aria-label="사람 필터" value={filters.person} onChange={e => set('person', e.target.value)} className={control}><option value="">모든 사람</option>{linkedPeople.map((id, index) => <option key={id} value={id}>{people.find(person => person.personId === id)?.name || `이름 확인 필요 ${index + 1}`}</option>)}</select>
      <select aria-label="종류 필터" value={filters.type} onChange={e => set('type', e.target.value)} className={control}><option value="">모든 종류</option>{Object.entries(TASK_TYPES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
      <select aria-label="기한 필터" value={filters.due} onChange={e => set('due', e.target.value)} className={control}><option value="">모든 기한</option><option value="today">오늘</option><option value="overdue">기한 지남</option><option value="unknown">기한 미정</option></select>
      <label className="text-sm"><input type="checkbox" checked={showCancelled} onChange={e => setShowCancelled(e.target.checked)} /> 취소 포함</label>
    </div>
    <div className={`grid gap-4 ${showCancelled ? 'xl:grid-cols-4' : 'xl:grid-cols-3'}`}>
      {['DRAFT', 'CONFIRMED', 'DONE', ...(showCancelled ? ['CANCELLED'] : [])].map(status => <div key={status} className="min-w-0 rounded-2xl bg-slate-100 p-3">
        <h2 className="mb-3 flex justify-between font-semibold text-slate-800">{STATUS_LABELS[status]} <span className="text-sm text-slate-500">{visible.filter(t => t.status === status).length}</span></h2>
        <TaskReviewPanel tasks={visible.filter(t => t.status === status)} onSave={onSave} onSelectEvidence={onSelectEvidence} people={people} currentUserId={currentUserId} />
      </div>)}
    </div>
  </section>;
}
