import { useState } from 'react';
import { TASK_TYPES, NEXT_STATUSES, STATUS_LABELS, editedDocument, errorMessage } from './taskUtils';

function TaskEditor({ task, readOnly, onSave, onSelectEvidence, selectedEvidence, people, currentUserId }) {
  const [changes, setChanges] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const current = editedDocument(task, changes);
  const disabled = readOnly || busy || !onSave || task.status === 'CANCELLED';
  const change = (field, value) => setChanges(previous => ({ ...previous, [field]: value }));
  async function save(status) {
    setBusy(true); setError('');
    try {
      await onSave(editedDocument(task, { ...changes, status }));
      setChanges({});
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  const inputClass = 'mt-1 w-full rounded-lg border border-slate-200 bg-white p-2 text-sm disabled:bg-slate-50';
  return (
    <article className={`rounded-xl border bg-white p-4 text-left ${selectedEvidence === task.evidence ? 'border-blue-500 ring-2 ring-blue-100' : 'border-slate-200'}`}>
      <div className="mb-2 flex items-center justify-between gap-2">
        <span className="rounded bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-700">{STATUS_LABELS[task.status]}</span>
        <span className="text-xs text-slate-500">{task.commitmentKind === 'EXPLICIT' ? '명시적 약속' : '후속 확인'}</span>
      </div>
      <label className="block text-xs text-slate-600">할 일
        <textarea aria-label="할 일 내용" value={current.content} disabled={disabled} onChange={e => change('content', e.target.value)} className={inputClass} />
      </label>
      <div className="mt-3 grid grid-cols-2 gap-3">
        <label className="text-xs text-slate-600">담당자
          <select aria-label="담당자" value={current.assignee || ''} disabled={disabled} onChange={e => change('assignee', e.target.value || null)} className={inputClass}>
            <option value="">미정</option>
            {currentUserId && <option value={currentUserId}>나</option>}
            {current.assignee && current.assignee !== currentUserId && <option value={current.assignee}>{current.assignee.startsWith('user_') ? '배정된 담당자 · 이름 확인 필요' : current.assignee}</option>}
          </select>
        </label>
        <label className="text-xs text-slate-600">기한 · 미정이면 비워 두기
          <input aria-label="기한" type="date" value={current.dueDate || ''} disabled={disabled} onChange={e => change('dueDate', e.target.value || null)} className={inputClass} />
        </label>
        <label className="text-xs text-slate-600">상대방
          <select aria-label="상대방" value={current.counterpartPersonId || ''} disabled={disabled} onChange={e => change('counterpartPersonId', e.target.value || null)} className={inputClass}>
            <option value="">미정 · 확인 필요</option>
            {current.counterpartPersonId && !people.some(person => person.personId === current.counterpartPersonId) && <option value={current.counterpartPersonId}>연결된 상대방 · 이름 확인 필요</option>}
            {people.map(person => <option key={person.personId} value={person.personId}>{person.name}</option>)}
          </select>
        </label>
        <label className="text-xs text-slate-600">종류
          <select aria-label="종류" value={current.taskType} disabled={disabled} onChange={e => change('taskType', e.target.value)} className={inputClass}>
            {Object.entries(TASK_TYPES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select>
        </label>
      </div>
      {(!current.assignee || !current.dueDate || !current.counterpartPersonId) && <p className="mt-2 text-xs text-amber-700">비어 있는 항목 · 확인 필요</p>}
      {current.dueDateIsDefault && <p className="mt-2 text-xs text-amber-700">기한은 기본값 · 확인 필요</p>}
      <button type="button" className="mt-3 w-full rounded-lg bg-slate-50 p-3 text-left text-xs leading-5 text-slate-600" onClick={() => onSelectEvidence?.(task.evidence)}>
        <span className="font-semibold">원문 근거</span><br />{task.evidence}
      </button>
      {error && <p role="alert" className="mt-2 text-sm text-red-700">{error}</p>}
      {!readOnly && onSave && <div className="mt-3 flex flex-wrap gap-2">
        <button type="button" disabled={disabled || !current.content.trim()} onClick={() => save(task.status)} className="rounded-lg border px-3 py-2 text-sm disabled:opacity-40">{busy ? '저장 중…' : '수정 저장'}</button>
        {NEXT_STATUSES[task.status].map(status => <button type="button" key={status} disabled={disabled || !current.content.trim()} onClick={() => save(status)} className={`rounded-lg px-3 py-2 text-sm disabled:opacity-40 ${status === 'CANCELLED' ? 'border text-slate-600' : 'bg-blue-600 text-white'}`}>
          {status === 'CONFIRMED' ? (task.status === 'DONE' ? '다시 열기' : '확정하기') : status === 'DONE' ? '완료하기' : '취소하기'}
        </button>)}
      </div>}
    </article>
  );
}

/** onSave는 영구 저장+revision 기록이 성공했을 때만 resolve해야 한다. */
export function TaskReviewPanel({ tasks = [], readOnly = false, onSave, onSelectEvidence, selectedEvidence, people = [], currentUserId = null }) {
  return <div className="space-y-3">
    {!tasks.length && <p className="rounded-xl bg-white p-4 text-sm text-slate-500">할 일이 없어요.</p>}
    {tasks.map(task => <TaskEditor key={`${task.taskId}:${JSON.stringify(task)}`} task={task} readOnly={readOnly} onSave={onSave} onSelectEvidence={onSelectEvidence} selectedEvidence={selectedEvidence} people={people} currentUserId={currentUserId} />)}
    {!onSave && !readOnly && <p className="text-xs text-slate-500">저장 연결을 기다리는 중이에요. 현재는 조회만 가능해요.</p>}
  </div>;
}
