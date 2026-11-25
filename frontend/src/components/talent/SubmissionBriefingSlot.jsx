import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users } from 'lucide-react';
import { relatedSubmissions } from '../../api/talents';
import { availabilityText, careerText, GRADE_LABELS, isStoredTaskId, RELATION_LABELS } from './talentUtils';

/**
 * 미팅 준비 브리핑(S-01) 한 칸: 이 미팅과 이어진 제출 리스트 후보 요약.
 * 같은 할 일 · 같은 회의록 · 같은 고객 담당자의 제출 리스트만. 없으면 아무것도 그리지 않는다.
 */
export function SubmissionBriefingSlot({ taskId }) {
  const [lists, setLists] = useState([]);

  useEffect(() => {
    if (!isStoredTaskId(taskId)) return undefined;
    let ignore = false;
    relatedSubmissions(taskId)
      .then((data) => { if (!ignore) setLists(data || []); })
      .catch(() => { if (!ignore) setLists([]); });   // 브리핑 본문을 막지 않는다
    return () => { ignore = true; };
  }, [taskId]);

  if (!lists.length) return null;
  return (
    <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50/60 p-4">
      <div className="mb-2 flex items-center gap-2">
        <Users className="h-4 w-4 text-blue-600" />
        <h2 className="text-sm font-bold text-slate-700">제출한 후보</h2>
      </div>
      {lists.map((list) => (
        <div key={list.taskId} className="mb-2 last:mb-0">
          <p className="text-xs text-slate-500">
            {RELATION_LABELS[list.relation]} · {list.taskContent}{list.dueDate ? ` (기한 ${list.dueDate})` : ''}
            {' '}<Link to={`/talents/match/${list.taskId}`} className="text-blue-600 hover:underline">추천 다시 보기</Link>
          </p>
          <ul className="mt-1 space-y-1">
            {list.candidates.map((candidate) => (
              <li key={candidate.profileId} className="text-sm text-slate-700">
                <Link to={`/talents/${candidate.profileId}`} className="font-semibold hover:underline">{candidate.name || '(이름 모름)'}</Link>
                <span className="text-xs text-slate-500">
                  {' '}· {GRADE_LABELS[candidate.grade] || '등급 모름'} · 경력 {careerText(candidate.careerMonths)} · 투입 {availabilityText(candidate)}
                  {candidate.topSkills.length ? ` · ${candidate.topSkills.join(', ')}` : ''}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}
