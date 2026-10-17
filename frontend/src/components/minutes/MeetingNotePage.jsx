import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, Loader2, Mic, X } from 'lucide-react';
import { createMeetingNoteDraft, getMeetingNotes } from '../../api/meetingNotes';
import { getApiErrorMessage } from '../../api/errors';
import {
  MODE_OPTIONS,
  formatMeetingDateTime,
  noteTitle,
  nowForDateTimeInput,
} from './meetingNoteUtils';

const inputClass = 'w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 '
  + 'focus:border-blue-400 focus:outline-none focus:ring-2 focus:ring-blue-100';

export function StatusBadge({ status }) {
  return status === 'CONFIRMED' ? (
    <span className="shrink-0 rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
      확정
    </span>
  ) : (
    <span className="shrink-0 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-700">
      확인 대기
    </span>
  );
}

export function MeetingNoteListItem({ note, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`w-full rounded-xl border p-3 text-left transition-colors ${
        note.status === 'CONFIRMED'
          ? 'border-slate-200 bg-white hover:border-blue-300'
          : 'border-amber-200 bg-amber-50/60 hover:border-amber-300'
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <p className="truncate text-sm font-semibold text-slate-800">{noteTitle(note)}</p>
        <StatusBadge status={note.status} />
      </div>
      <p className="mt-1 truncate text-xs text-slate-500">
        {formatMeetingDateTime(note.occurredAt)}
        {note.location ? ` · ${note.location}` : ''}
        {note.mode === 'LIGHT' ? ' · 사람별 간단 양식' : ''}
        {note.status !== 'CONFIRMED' && note.missingFieldCount > 0 ? ` · 확인 필요 ${note.missingFieldCount}칸` : ''}
      </p>
    </button>
  );
}

/** ② 미팅 기록: 메모 입력 → "AI로 정리하기" → ③ 확인 화면. 오른쪽은 최근 회의록. */
export function MeetingNotePage() {
  const navigate = useNavigate();
  const [occurredAt, setOccurredAt] = useState(nowForDateTimeInput);
  const [location, setLocation] = useState('');
  const [attendees, setAttendees] = useState([]);
  const [attendeeInput, setAttendeeInput] = useState('');
  const [text, setText] = useState('');
  const [mode, setMode] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [recentNotes, setRecentNotes] = useState([]);
  const [recentMessage, setRecentMessage] = useState('');

  useEffect(() => {
    let ignore = false;
    getMeetingNotes()
      .then((notes) => {
        if (!ignore) {
          setRecentNotes(notes.slice(0, 8));
        }
      })
      .catch((error) => {
        if (!ignore) {
          setRecentMessage(getApiErrorMessage(error));
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  const addAttendee = (value) => {
    const name = value.trim().replace(/,$/, '').trim();
    if (name && !attendees.includes(name)) {
      setAttendees((prev) => [...prev, name]);
    }
    setAttendeeInput('');
  };

  const handleAttendeeKeyDown = (event) => {
    if (event.nativeEvent.isComposing) {
      return;
    }
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      addAttendee(attendeeInput);
    } else if (event.key === 'Backspace' && !attendeeInput && attendees.length > 0) {
      setAttendees((prev) => prev.slice(0, -1));
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!text.trim() || isSubmitting) {
      return;
    }
    // 입력칸에 남은 이름도 만난 사람으로 넣는다
    const names = attendeeInput.trim() && !attendees.includes(attendeeInput.trim())
      ? [...attendees, attendeeInput.trim()]
      : attendees;

    setIsSubmitting(true);
    setErrorMessage('');
    try {
      const draft = await createMeetingNoteDraft({
        occurredAt: occurredAt ? `${occurredAt}:00` : null,
        location: location.trim() || null,
        attendees: names,
        text,
        mode: mode || null,
        title: null,
      });
      // A 식별·C 할 일 결과는 확인 화면의 A·C 영역이 쓴다 (새로고침해도 history state에 남음)
      navigate(`/meeting-notes/${draft.note.meetingNote.noteId}`, {
        state: {
          identityResolutions: draft.identityResolutions,
          tasks: draft.tasks,
          droppedItems: draft.droppedItems,
        },
      });
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
      setIsSubmitting(false);
    }
  };

  return (
    <div className="h-full overflow-auto p-6">
      <div className="mb-4 flex flex-wrap items-baseline gap-3 text-left">
        <h2 className="text-[22px] font-bold tracking-tight text-slate-900" style={{ fontFamily: 'var(--display)' }}>
          미팅 기록
        </h2>
        <p className="text-sm text-slate-500">미팅이 끝나면 메모를 남기세요. 정리는 AI가 하고, 확정은 직접 합니다.</p>
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,3fr)_minmax(300px,2fr)]">
        <form onSubmit={handleSubmit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 text-left shadow-sm">
          <h3 className="text-base font-bold text-slate-800" style={{ fontFamily: 'var(--display)' }}>새 미팅 기록</h3>

          <div className="flex gap-2">
            <span className="rounded-xl border border-blue-400 bg-blue-50 px-3 py-1.5 text-sm font-semibold text-blue-700">
              메모로 입력
            </span>
            <span
              title="메일을 골라 회의록을 만드는 기능은 다음 단계(B-5)에서 연결합니다."
              className="cursor-not-allowed rounded-xl border border-slate-200 px-3 py-1.5 text-sm text-slate-400"
            >
              메일함에서 가져오기 · 준비 중
            </span>
          </div>

          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <label className="block">
              <span className="mb-1 block text-xs font-semibold text-slate-600">언제</span>
              <input
                type="datetime-local"
                value={occurredAt}
                onChange={(event) => setOccurredAt(event.target.value)}
                className={inputClass}
              />
            </label>
            <label className="block">
              <span className="mb-1 block text-xs font-semibold text-slate-600">어디서</span>
              <input
                type="text"
                value={location}
                onChange={(event) => setLocation(event.target.value)}
                placeholder="예: 가상전자 본사"
                maxLength={255}
                className={inputClass}
              />
            </label>
          </div>

          <div>
            <span className="mb-1 block text-xs font-semibold text-slate-600">
              만난 사람 <span className="font-normal text-slate-400">(이름을 쓰고 Enter · 여러 명이면 하나씩)</span>
            </span>
            <div className="flex flex-wrap items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-2 py-1.5 focus-within:border-blue-400 focus-within:ring-2 focus-within:ring-blue-100">
              {attendees.map((name) => (
                <span key={name} className="inline-flex items-center gap-1 rounded-lg bg-blue-50 px-2 py-0.5 text-sm text-blue-700">
                  {name}
                  <button
                    type="button"
                    aria-label={`${name} 빼기`}
                    onClick={() => setAttendees((prev) => prev.filter((item) => item !== name))}
                    className="text-blue-400 hover:text-blue-700"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </span>
              ))}
              <input
                type="text"
                value={attendeeInput}
                onChange={(event) => setAttendeeInput(event.target.value)}
                onKeyDown={handleAttendeeKeyDown}
                onBlur={() => attendeeInput.trim() && addAttendee(attendeeInput)}
                placeholder={attendees.length === 0 ? '예: 홍 부장' : ''}
                className="min-w-[8rem] flex-1 border-none bg-transparent px-1 py-0.5 text-sm text-slate-800 focus:outline-none"
              />
            </div>
          </div>

          <label className="block">
            <span className="mb-1 block text-xs font-semibold text-slate-600">메모</span>
            <textarea
              value={text}
              onChange={(event) => setText(event.target.value)}
              rows={9}
              placeholder={'예)\n홍 부장님이 내년 상반기에 개발자 두 명 정도 더 필요하다고 하심.\n계약서 초안은 이번 주 금요일까지 보내드리기로 함.'}
              className={`${inputClass} resize-y leading-relaxed`}
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-xs font-semibold text-slate-600">양식</span>
            <select value={mode} onChange={(event) => setMode(event.target.value)} className={inputClass}>
              {MODE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>

          {errorMessage && (
            <p className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{errorMessage}</p>
          )}

          <div className="flex items-center justify-between gap-3">
            <span className="inline-flex items-center gap-1.5 rounded-xl border border-dashed border-slate-300 px-3 py-2 text-xs text-slate-400">
              <Mic className="h-3.5 w-3.5" /> 녹음 파일 첨부 · 추가 검토 중
            </span>
            <button
              type="submit"
              disabled={!text.trim() || isSubmitting}
              className="inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" /> AI가 정리하는 중…
                </>
              ) : (
                <>
                  AI로 정리하기 <ArrowRight className="h-4 w-4" />
                </>
              )}
            </button>
          </div>
          {isSubmitting && (
            <p className="text-right text-xs text-slate-400">로컬 AI는 몇 분 걸릴 수 있어요. 메모는 이미 저장됐어요.</p>
          )}
        </form>

        <div className="h-fit rounded-2xl border border-slate-200 bg-white p-5 text-left shadow-sm">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-base font-bold text-slate-800" style={{ fontFamily: 'var(--display)' }}>최근 회의록</h3>
            <button
              type="button"
              onClick={() => navigate('/meeting-notes/inbox')}
              className="text-xs text-blue-600 hover:underline"
            >
              확인 대기함 →
            </button>
          </div>
          {recentMessage && <p className="text-sm text-red-600">{recentMessage}</p>}
          {!recentMessage && recentNotes.length === 0 && (
            <p className="text-sm text-slate-400">아직 회의록이 없어요.</p>
          )}
          <div className="space-y-2">
            {recentNotes.map((note) => (
              <MeetingNoteListItem
                key={note.noteId}
                note={note}
                onClick={() => navigate(`/meeting-notes/${note.noteId}`)}
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
