import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { errorMessage } from './taskUtils';

async function listTaskStatusSuggestions() {
  return (await api.get('/api/task-status-suggestions')).data.data;
}

async function decide(item, decision) {
  await api.post(`/api/task-status-suggestions/${encodeURIComponent(item.taskId)}/${decision}`, { sourceId: item.sourceId });
}

function formatSentAt(value) {
  return value ? value.slice(5, 16).replace('-', '/').replace('T', ' ') : '';
}

/** 메일로 끝난 것 같은 할 일을 제안한다. 상태는 사람이 눌러야 바뀐다 (D-025). */
export function TaskStatusSuggestions({ refreshKey, onDecided }) {
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  useEffect(() => {
    let active = true;
    listTaskStatusSuggestions()
      .then((result) => { if (active) { setItems(result); setError(''); } })
      .catch((e) => { if (active) setError(`상태 제안을 불러오지 못했어요. ${errorMessage(e)}`); });
    return () => { active = false; };
  }, [refreshKey]);

  async function choose(item, decision) {
    setBusyId(item.taskId);
    setError('');
    try {
      await decide(item, decision);
      setItems((prev) => prev.filter((other) => other.taskId !== item.taskId));
      await onDecided?.();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  if (!items.length && !error) {
    return null;
  }
  return (
    <section aria-label="할 일 상태 제안" className="mb-5 rounded-xl border border-amber-200 bg-amber-50 p-4">
      {items.length > 0 && <h2 className="font-semibold text-amber-900">완료로 옮길까요? {items.length}건</h2>}
      {error && <p role="alert" className="mt-1 text-sm text-red-700">{error}</p>}
      <ul className="mt-2 space-y-2">
        {items.map((item) => (
          <li key={item.taskId} className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-white p-3">
            <div className="min-w-0 flex-1">
              <p className="font-medium text-slate-800">{item.content}</p>
              <p className="mt-0.5 text-sm text-slate-600">{item.reason}</p>
              <p className="mt-0.5 truncate text-xs text-slate-500">근거 메일 · {formatSentAt(item.sourceSentAt)} · {item.sourceTitle}</p>
            </div>
            <div className="flex shrink-0 gap-2">
              <button type="button" disabled={busyId !== null} onClick={() => choose(item, 'accept')} className="rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-40">완료로 옮기기</button>
              <button type="button" disabled={busyId !== null} onClick={() => choose(item, 'dismiss')} className="rounded-lg border bg-white px-3 py-2 text-sm text-slate-600 disabled:opacity-40">아니에요</button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
