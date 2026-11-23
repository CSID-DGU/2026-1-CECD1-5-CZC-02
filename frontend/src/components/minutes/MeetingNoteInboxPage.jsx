import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { archiveMeetingNote, getMeetingNotes } from '../../api/meetingNotes';
import { getApiErrorMessage } from '../../api/errors';
import { identityApi } from '../identity/identityApi';
import { listTasks } from '../tasks/taskApi';
import { buildInboxSummary, runWithLimit } from './inboxItems';
import { MeetingNoteListItem } from './MeetingNotePage';
import { ARCHIVE_CONFIRM_MESSAGE } from './meetingNoteUtils';

const IDENTITY_REQUESTS_AT_ONCE = 4;
const OTHER_TASKS_SHOWN = 5;

const CHIP_TONES = {
  blue: 'bg-blue-100 text-blue-700',
  amber: 'bg-amber-100 text-amber-800',
  green: 'bg-emerald-100 text-emerald-700',
  gray: 'bg-slate-100 text-slate-500',
};

function Chip({ tone, children }) {
  return <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${CHIP_TONES[tone]}`}>{children}</span>;
}

/** 이 회의록을 확정하기 전에 남은 일. 불러오지 못한 값은 0으로 보이지 않는다. */
function RemainingChips({ draftTasks, pendingPeople }) {
  if (draftTasks === undefined || pendingPeople === undefined) {
    return <Chip tone="gray">남은 항목 불러오는 중</Chip>;
  }
  if (draftTasks === 0 && pendingPeople === 0) {
    return <Chip tone="green">회의록만 확인하면 끝</Chip>;
  }
  return (
    <>
      {draftTasks === null && <Chip tone="gray">할 일 불러오지 못함</Chip>}
      {draftTasks > 0 && <Chip tone="blue">할 일 {draftTasks}건 확인 전</Chip>}
      {pendingPeople === null && <Chip tone="gray">사람 불러오지 못함</Chip>}
      {pendingPeople > 0 && <Chip tone="amber">사람 {pendingPeople}명 확인 필요</Chip>}
    </>
  );
}

function Total({ label, value, unit }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="mt-0.5 text-xl font-bold text-slate-900">
        {typeof value === 'number' ? <>{value}<span className="ml-0.5 text-sm font-semibold text-slate-500">{unit}</span></> : '—'}
      </p>
    </div>
  );
}

/**
 * 확인 대기함: 아직 확정하지 않은 회의록(DRAFT)과, 회의록마다 남은 할 일(C)·식별 후보(A).
 * 회의록은 메일함과 섞지 않고 이 화면과 미팅 기록에서만 보여준다 (D-020).
 */
export function MeetingNoteInboxPage() {
  const navigate = useNavigate();
  const [notes, setNotes] = useState(null);
  // undefined = 불러오는 중, null = 불러오지 못함
  const [tasks, setTasks] = useState(undefined);
  const [identities, setIdentities] = useState({});
  const [message, setMessage] = useState('');
  const [busyNoteId, setBusyNoteId] = useState(null);

  useEffect(() => {
    let ignore = false;
    const controller = new AbortController();
    getMeetingNotes('DRAFT')
      .then((result) => {
        if (ignore) {
          return;
        }
        setNotes(result);
        listTasks()
          .then((list) => { if (!ignore) setTasks(list); })
          .catch(() => { if (!ignore) setTasks(null); });
        runWithLimit(result, IDENTITY_REQUESTS_AT_ONCE, async ({ noteId }) => {
          if (ignore) {
            return;
          }
          let items = null;
          try {
            items = await identityApi.list(noteId, controller.signal);
          } catch {
            items = null;
          }
          if (!ignore) {
            setIdentities((prev) => ({ ...prev, [noteId]: items }));
          }
        });
      })
      .catch((error) => {
        if (!ignore) {
          setMessage(getApiErrorMessage(error));
        }
      });
    return () => {
      ignore = true;
      controller.abort();
    };
  }, []);

  const summary = useMemo(() => buildInboxSummary(notes || [], tasks, identities), [notes, tasks, identities]);

  // 보관(숨김): 지우지 않고 대기함에서만 뺀다. 미팅 기록의 "보관함"에서 해제할 수 있다 (D-025)
  const handleArchive = async (note) => {
    if (!window.confirm(ARCHIVE_CONFIRM_MESSAGE)) {
      return;
    }
    setBusyNoteId(note.noteId);
    try {
      await archiveMeetingNote(note.noteId);
      setNotes((prev) => (prev || []).filter((item) => item.noteId !== note.noteId));
      window.dispatchEvent(new Event('meeting-notes:changed'));
    } catch (error) {
      setMessage(getApiErrorMessage(error));
    } finally {
      setBusyNoteId(null);
    }
  };

  return (
    <div className="h-full overflow-auto p-6">
      <div className="mx-auto max-w-3xl text-left">
        <div className="mb-4 flex flex-wrap items-baseline gap-3">
          <h2 className="text-[22px] font-bold tracking-tight text-slate-900" style={{ fontFamily: 'var(--display)' }}>
            확인 대기함
          </h2>
          <p className="text-sm text-slate-500">AI가 정리했지만 아직 사람이 확인하지 않은 항목이에요. 확정하기 전엔 아무것도 등록되지 않아요.</p>
        </div>

        {notes !== null && (
          <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Total label="회의록" value={summary.totals.notes} unit="건" />
            <Total label="확인 전 할 일" value={summary.totals.tasks} unit="건" />
            <Total label="확인할 사람" value={summary.totals.people} unit="명" />
          </div>
        )}

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          {message && <p className="text-sm text-red-600">{message}</p>}
          {!message && notes === null && (
            <p className="flex items-center gap-2 text-sm text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" /> 불러오는 중…
            </p>
          )}
          {notes?.length === 0 && (
            <div className="py-8 text-center">
              <p className="text-sm text-slate-500">확인할 회의록이 없어요.</p>
              <button
                type="button"
                onClick={() => navigate('/meeting-notes')}
                className="mt-3 rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700"
              >
                미팅 기록 남기기
              </button>
            </div>
          )}
          <div className="space-y-2">
            {summary.rows.map(({ note, draftTasks, pendingPeople }) => (
              <MeetingNoteListItem
                key={note.noteId}
                note={note}
                onClick={() => navigate(`/meeting-notes/${note.noteId}`)}
                actionLabel="보관"
                onAction={() => handleArchive(note)}
                actionDisabled={busyNoteId === note.noteId}
                footer={<RemainingChips draftTasks={draftTasks} pendingPeople={pendingPeople} />}
              />
            ))}
          </div>
        </div>

        {summary.otherDraftTasks.length > 0 && (
          <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-2 flex items-center justify-between gap-3">
              <div>
                <h3 className="text-sm font-bold text-slate-800">다른 확인 전 할 일 {summary.otherDraftTasks.length}건</h3>
                <p className="text-xs text-slate-500">직접 추가했거나 보관한 회의록에 남은 초안이에요. 할 일 보드에서 확정하거나 취소해 주세요.</p>
              </div>
              <button
                type="button"
                onClick={() => navigate('/tasks')}
                className="shrink-0 rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              >
                할 일 보드 열기
              </button>
            </div>
            <ul className="space-y-1">
              {summary.otherDraftTasks.slice(0, OTHER_TASKS_SHOWN).map((task) => (
                <li key={task.taskId} className="truncate text-sm text-slate-700">{task.content}</li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}
