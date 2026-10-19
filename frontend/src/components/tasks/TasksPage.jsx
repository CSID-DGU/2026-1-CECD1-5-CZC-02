import './tasks.css';
import { getMeetingNotes } from '../../api/meetingNotes';
import { noteTitle, formatMeetingDateTime } from '../minutes/meetingNoteUtils';
import { getMe } from '../../api/auth';
import { useCallback, useEffect, useRef, useState } from 'react';
import { TaskBoard } from './TaskBoard';
import { createTask, importTasks, listTasks, loadTask, saveTask } from './taskApi';
import { errorMessage } from './taskUtils';

async function fetchDetails() {
  const items = await listTasks();
  return Promise.all(items.map(task => loadTask(task.taskId)));
}

/** 통합 시 로그인 보호 경로 아래 배치한다. 현재 페이지도 JWT API만 사용한다. */
export function TasksPage({ currentUserId = null, people = [] }) {
  const [tasks, setTasks] = useState([]);
  const [actorId, setActorId] = useState(currentUserId);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [noteId, setNoteId] = useState('');
  const [notes, setNotes] = useState([]);
  const [notesError, setNotesError] = useState('');
  const [content, setContent] = useState('');
  const [evidence, setEvidence] = useState('');
  const [selectedEvidence, setSelectedEvidence] = useState('');
  const versions = useRef(new Map());
  const reload = useCallback(async () => {
    const details = await fetchDetails();
    versions.current = new Map(details.map(({ task, etag }) => [task.taskId, etag]));
    setTasks(details.map(({ task }) => task));
  }, []);
  useEffect(() => {
    let active = true;
    Promise.all([fetchDetails(), currentUserId ? Promise.resolve(currentUserId) : getMe().then(user => user?.id == null ? null : `user_${user.id}`)]).then(([details, userId]) => {
      if (!active) return;
      versions.current = new Map(details.map(({ task, etag }) => [task.taskId, etag]));
      setTasks(details.map(({ task }) => task));
      setActorId(userId);
    }).catch(e => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, [currentUserId]);
  useEffect(() => {
    let active = true;
    getMeetingNotes().then(items => { if (active) setNotes(items); })
      .catch(e => { if (active) setNotesError(errorMessage(e)); });
    return () => { active = false; };
  }, []);
  async function run(action) {
    setBusy(true); setError(''); setMessage('');
    try { await action(); await reload(); }
    catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  async function save(task) {
    await saveTask(task, versions.current.get(task.taskId));
    await reload();
    setMessage('수정 기록과 함께 저장했어요.');
  }
  return <main className="task-workspace min-h-screen bg-[#f4f6fb] p-6 text-left text-slate-800">
    <header className="mb-6 flex items-center justify-between"><div><h1 className="text-2xl font-bold">할 일</h1><p className="mt-1 text-sm text-slate-500">약속을 확인하고, 다음 행동을 준비하세요.</p></div><button type="button" disabled={busy} onClick={() => run(async () => {})} className="rounded-lg border bg-white px-4 py-2">새로고침</button></header>
    {error && <p role="alert" className="mb-4 rounded-xl bg-red-50 p-3 text-red-700">{error}</p>}
    {message && <p role="status" className="mb-4 rounded-xl bg-blue-50 p-3 text-blue-700">{message}</p>}
    <details className="mb-5 rounded-xl border bg-white p-4"><summary className="cursor-pointer font-semibold">할 일 가져오기 / 직접 추가</summary>
      <form onSubmit={e => { e.preventDefault(); run(() => importTasks(noteId)); }} className="mt-4 flex flex-wrap gap-2"><label>회의록 <select aria-label="회의록 선택" required value={noteId} onChange={e => setNoteId(e.target.value)} className="rounded-lg border p-2"><option value="">회의록을 선택하세요</option>{notes.map(note => <option key={note.noteId} value={note.noteId}>{noteTitle(note)} · {formatMeetingDateTime(note.occurredAt)}</option>)}</select></label><button disabled={busy} className="rounded-lg bg-blue-600 px-3 text-white">회의록에서 가져오기</button></form>
      {notesError && <p className="mt-2 text-sm text-amber-700">회의록 목록을 불러오지 못했어요. {notesError}</p>}
      <form onSubmit={e => { e.preventDefault(); run(async () => {
        await createTask({ taskId: 't_manual', noteId: null, content, evidence, dueDate: null, dueDateIsDefault: false,
          assignee: actorId, counterpartPersonId: null, commitmentKind: 'EXPLICIT', taskType: 'NONE',
          taskTypeConfidence: 1, taskTypeSource: 'HUMAN', status: 'DRAFT' });
        setContent(''); setEvidence('');
      }); }} className="mt-4 flex flex-col gap-2"><label>할 일 내용<input aria-label="새 할 일 내용" required value={content} onChange={e => setContent(e.target.value)} className="ml-2 rounded-lg border p-2" /></label><label>원문 근거<input aria-label="새 할 일 근거" required value={evidence} onChange={e => setEvidence(e.target.value)} className="ml-2 rounded-lg border p-2" /></label><button disabled={busy} className="w-fit rounded-lg border px-3 py-2">확인 전으로 추가</button></form>
    </details>
    <TaskBoard tasks={tasks} currentUserId={actorId} onSave={save} onSelectEvidence={setSelectedEvidence} people={people} />
    {selectedEvidence && <aside className="mt-5 rounded-xl border border-blue-200 bg-white p-4"><h2 className="font-semibold">선택한 원문 근거</h2><p className="mt-2 whitespace-pre-wrap">{selectedEvidence}</p></aside>}
    <p className="mt-5 text-xs text-slate-500">수정 기록을 보관해요. 확정한 할 일은 일정·브리핑 연결 후 활용돼요.</p>
  </main>;
}
