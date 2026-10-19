import './tasks.css';
import { createRoot } from 'react-dom/client';
import { useState } from 'react';
import '../../index.css';
import { TaskBoard } from './TaskBoard';
import { TasksPage } from './TasksPage';

const sample = { taskId: 't_demo_1', noteId: 'mn_demo', content: '계약서 초안 송부', dueDate: null,
  dueDateIsDefault: false, assignee: 'user_demo', counterpartPersonId: 'p_demo', commitmentKind: 'EXPLICIT',
  taskType: 'CONTRACT_PREP', taskTypeConfidence: 1, taskTypeSource: 'RULE', evidence: '계약서 초안을 보내기로 함', status: 'DRAFT' };
export function Preview() {
  const [live, setLive] = useState(false);
  const [tasks, setTasks] = useState([sample, { ...sample, taskId: 't_demo_2', content: '미팅 준비', taskType: 'MEETING_PREP', evidence: '다음 미팅을 준비하기로 함', status: 'CONFIRMED' }]);
  const [revisions, setRevisions] = useState([]);
  const [evidence, setEvidence] = useState('');
  async function save(next) {
    const before = tasks.find(task => task.taskId === next.taskId);
    const changed = Object.keys(next).filter(field => JSON.stringify(next[field]) !== JSON.stringify(before[field]));
    setRevisions(previous => [...previous, ...changed.map(field => ({ targetId: next.taskId, fieldName: field, proposedValue: before[field], finalValue: next[field] }))]);
    setTasks(previous => previous.map(task => task.taskId === next.taskId ? next : task));
  }
  return <div className="task-workspace min-h-screen bg-[#f4f6fb] text-slate-800">
    <nav className="flex flex-wrap items-center gap-3 border-b bg-white p-4"><b>할 일</b><button onClick={() => setLive(false)} className="rounded border px-3 py-1">가짜 데이터 체험</button><button onClick={() => setLive(true)} className="rounded border px-3 py-1">내 할 일</button></nav>
    {live ? <TasksPage /> : <main className="p-6"><h1 className="mb-2 text-2xl font-bold">할 일 확인과 관리</h1><p className="mb-6 text-sm text-slate-500">가짜 데이터 체험 · 변경 사항은 이 화면에서만 유지돼요.</p><TaskBoard tasks={tasks} currentUserId="user_demo" onSave={save} onSelectEvidence={setEvidence} people={[{ personId: 'p_demo', name: '검증인물_A', isSynthetic: true }]} />{evidence && <p className="mt-4 rounded bg-white p-4">원문 근거: {evidence}</p>}<p className="mt-4 text-sm text-slate-500">체험 수정 기록 {revisions.length}건</p></main>}
  </div>;
}
createRoot(document.getElementById('root')).render(<Preview />);
