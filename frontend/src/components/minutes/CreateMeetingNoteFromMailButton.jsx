import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, NotebookPen } from 'lucide-react';
import { createMeetingNoteFromSource } from '../../api/meetingNotes';
import { getApiErrorMessage } from '../../api/errors';

/**
 * 메일 상세(MessageView)의 "회의록 만들기" (B-5). 그 메일의 스레드 전체로 회의록 초안을 만들고 확인 화면으로 간다.
 * 같은 메일(스레드)로 이미 만든 회의록이 있으면 새로 만들지 않고 그 회의록을 연다.
 */
export function CreateMeetingNoteFromMailButton({ sourceId }) {
  const navigate = useNavigate();
  const [isCreating, setIsCreating] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const handleClick = async () => {
    setIsCreating(true);
    setErrorMessage('');
    try {
      const result = await createMeetingNoteFromSource(sourceId);
      navigate(`/meeting-notes/${result.noteId}`, {
        state: result.created
          ? {
            identityResolutions: result.draft.identityResolutions,
            droppedItems: result.draft.droppedItems,
          }
          : { notice: '이 메일(대화)로 이미 만든 회의록이 있어서 그 회의록을 열었어요.' },
      });
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
      setIsCreating(false);
    }
  };

  return (
    <span className="inline-flex flex-col items-end">
      <button
        type="button"
        onClick={handleClick}
        disabled={isCreating}
        title="이 메일과 같은 대화의 메일들로 회의록 초안을 만들어요. 확정하기 전엔 아무것도 등록되지 않아요."
        className="inline-flex items-center gap-1 rounded border border-blue-200 px-2 py-0.5 text-xs text-blue-700 hover:bg-blue-50 disabled:opacity-60"
      >
        {isCreating ? <Loader2 className="h-3 w-3 animate-spin" /> : <NotebookPen className="h-3 w-3" />}
        {isCreating ? 'AI가 정리하는 중…' : '회의록 만들기'}
      </button>
      {errorMessage && <span className="mt-1 text-[11px] text-red-600">{errorMessage}</span>}
    </span>
  );
}
