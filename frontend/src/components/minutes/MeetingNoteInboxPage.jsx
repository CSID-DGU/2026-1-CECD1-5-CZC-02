import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { getMeetingNotes } from '../../api/meetingNotes';
import { getApiErrorMessage } from '../../api/errors';
import { MeetingNoteListItem } from './MeetingNotePage';

/**
 * 확인 대기함: 아직 확정하지 않은 회의록(DRAFT). 할 일(C)·식별 후보(A)는 각 담당이 API를 만든 뒤 여기에 붙인다.
 * 회의록은 메일함과 섞지 않고 이 화면과 미팅 기록에서만 보여준다 (D-020).
 */
export function MeetingNoteInboxPage() {
  const navigate = useNavigate();
  const [notes, setNotes] = useState(null);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let ignore = false;
    getMeetingNotes('DRAFT')
      .then((result) => {
        if (!ignore) {
          setNotes(result);
        }
      })
      .catch((error) => {
        if (!ignore) {
          setMessage(getApiErrorMessage(error));
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  return (
    <div className="h-full overflow-auto p-6">
      <div className="mx-auto max-w-3xl text-left">
        <div className="mb-4 flex flex-wrap items-baseline gap-3">
          <h2 className="text-[22px] font-bold tracking-tight text-slate-900" style={{ fontFamily: 'var(--display)' }}>
            확인 대기함
          </h2>
          <p className="text-sm text-slate-500">AI가 정리했지만 아직 확정하지 않은 회의록이에요. 확정하기 전엔 아무것도 등록되지 않아요.</p>
        </div>

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
            {(notes || []).map((note) => (
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
