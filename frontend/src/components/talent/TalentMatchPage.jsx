import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, CheckSquare, Loader2, RotateCcw, Search, Square } from 'lucide-react';
import { matchForTask, matchText, saveSubmission } from '../../api/talents';
import { getApiErrorMessage } from '../../api/errors';
import {
  availabilityText, careerText, GRADE_LABELS, MATCH_TASK_LABELS, requirementSummary, sameSelection,
  SOURCE_ORIGIN_LABELS, toggleSelection,
} from './talentUtils';

function Condition({ label, found, children }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white px-3 py-2">
      <p className="text-[11px] font-semibold text-slate-500">{label}</p>
      {found ? (
        <>
          <p className="text-sm font-semibold text-slate-800">{children}</p>
          <p className="mt-0.5 truncate text-[11px] text-slate-400" title={found.evidence}>근거: {found.evidence}</p>
        </>
      ) : (
        <p className="text-sm text-amber-700">확인 필요</p>
      )}
    </div>
  );
}

function RequirementPanel({ requirement }) {
  const required = requirement.skills.filter((skill) => skill.required);
  const preferred = requirement.skills.filter((skill) => !skill.required);
  return (
    <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
      <p className="mb-3 text-sm font-bold text-slate-700">요청에서 꺼낸 조건 <span className="font-normal text-slate-400">(규칙으로 찾음 · AI 추측 없음)</span></p>
      <div className="grid grid-cols-2 gap-2 md:grid-cols-3">
        <Condition label="필수 기술" found={required.length ? { evidence: required.map((skill) => skill.evidence).filter((v, i, a) => a.indexOf(v) === i).join(' / ') } : null}>
          {required.map((skill) => skill.name).join(' · ')}
        </Condition>
        <Condition label="인원" found={requirement.headcount}>{requirement.headcount?.value}명</Condition>
        <Condition label="최소 경력" found={requirement.minCareerMonths}>
          {requirement.minCareerMonths && `${careerText(requirement.minCareerMonths.value)} 이상`}
        </Condition>
        <Condition label="투입 시기" found={requirement.startFrom}>{requirement.startFrom?.value}부터</Condition>
        {requirement.minGrade && <Condition label="최소 등급" found={requirement.minGrade}>{GRADE_LABELS[requirement.minGrade.value]} 이상</Condition>}
        {preferred.length > 0 && (
          <Condition label="우대 기술 (가점)" found={{ evidence: preferred.map((skill) => skill.evidence).join(' / ') }}>
            {preferred.map((skill) => skill.name).join(' · ')}
          </Condition>
        )}
        {requirement.industries.length > 0 && (
          <Condition label="산업 경험 (가점)" found={{ evidence: requirement.industries.map((item) => item.evidence).join(' / ') }}>
            {requirement.industries.map((item) => item.name).join(' · ')}
          </Condition>
        )}
      </div>
      <p className="mt-3 text-xs text-amber-700">
        <AlertTriangle className="mr-1 inline h-3.5 w-3.5" />
        요청에 없는 것 — 고객에게 확인: {requirement.unknowns.join(', ')}
      </p>
    </div>
  );
}

function CandidateCard({ candidate, rank, selectable, checked, onToggle }) {
  return (
    <article className={`rounded-2xl border bg-white p-4 shadow-sm ${checked ? 'border-blue-400 ring-1 ring-blue-200' : 'border-slate-200'}`}>
      <div className="flex items-start gap-3">
        {selectable && (
          <button type="button" onClick={onToggle} className="mt-0.5 text-blue-600" aria-label={checked ? '제출 리스트에서 빼기' : '제출 리스트에 넣기'}>
            {checked ? <CheckSquare className="h-5 w-5" /> : <Square className="h-5 w-5 text-slate-400" />}
          </button>
        )}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-baseline gap-2">
            <span className="text-xs font-semibold text-slate-400">{rank}</span>
            <Link to={`/talents/${candidate.profileId}`} className="text-base font-bold text-slate-900 hover:underline">{candidate.name || '(이름 모름)'}</Link>
            {candidate.birthYear && <span className="text-xs text-slate-400">{candidate.birthYear}년생</span>}
            <span className="text-xs text-slate-600">{GRADE_LABELS[candidate.grade] || '등급 모름'} · 경력 {careerText(candidate.careerMonths)} · 투입 {availabilityText(candidate)}</span>
            {candidate.late && <span className="rounded-full bg-red-50 px-2 py-0.5 text-[11px] font-semibold text-red-700">요청 시기보다 늦음</span>}
            <span className="ml-auto rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700" title="필수 조건을 채우면 50점, 가점을 더한 값 (규칙 계산)">{candidate.score}점</span>
          </div>
          <ul className="mt-2 space-y-1">
            {candidate.reasons.map((reason) => (
              <li key={`${reason.kind}-${reason.text}`} className="text-sm text-slate-700">
                <span className="mr-1 text-emerald-600">✓</span>{reason.text}
                {reason.points > 0 && <span className="ml-1 text-xs font-semibold text-blue-600">+{reason.points}</span>}
                {reason.evidence && <span className="ml-2 text-[11px] text-slate-400" title={reason.evidence}>이력서: {reason.evidence.length > 60 ? `${reason.evidence.slice(0, 60)}…` : reason.evidence}</span>}
              </li>
            ))}
          </ul>
          <div className="mt-2 flex flex-wrap gap-1">
            {candidate.checkNeeded.map((item) => (
              <span key={item} className="rounded bg-amber-50 px-1.5 py-0.5 text-[11px] text-amber-800">확인 필요 · {item}</span>
            ))}
          </div>
        </div>
      </div>
    </article>
  );
}

/** 후보 추천: 할 일(인력 리스트 제출·소싱)의 고객 요청 → 조건 → 후보 풀에서 거르고 순서 매기기. 고르는 것은 담당자. */
export function TalentMatchPage() {
  const { taskId } = useParams();
  const [result, setResult] = useState(null);
  const [text, setText] = useState('');
  const [edited, setEdited] = useState(false);
  const [selected, setSelected] = useState([]);
  const [saved, setSaved] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [notice, setNotice] = useState('');
  const [showExcluded, setShowExcluded] = useState(false);

  const apply = useCallback((data, keepSelection) => {
    setResult(data);
    if (!keepSelection) {
      setSelected(data.submission || []);
      setSaved(data.submission || []);
    }
    setMessage('');
  }, []);

  useEffect(() => {
    if (!taskId) return undefined;
    let ignore = false;
    matchForTask(taskId)
      .then((data) => {
        if (ignore) return;
        apply(data, false);
        setText(data.sources.map((line) => line.text).join('\n'));
      })
      .catch((error) => { if (!ignore) setMessage(getApiErrorMessage(error)); });
    return () => { ignore = true; };
  }, [taskId, apply]);

  const search = async () => {
    if (!text.trim()) return;
    setLoading(true);
    try {
      apply(await matchText({ taskId, text }), Boolean(taskId));
      setEdited(true);
    } catch (error) {
      setMessage(getApiErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  const reset = async () => {
    setLoading(true);
    try {
      const data = await matchForTask(taskId);
      apply(data, true);
      setText(data.sources.map((line) => line.text).join('\n'));
      setEdited(false);
    } catch (error) {
      setMessage(getApiErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  const save = async () => {
    setLoading(true);
    try {
      const rows = await saveSubmission(taskId, selected);
      const ids = rows.map((row) => row.profileId);
      setSaved(ids);
      setSelected(ids);
      setNotice(`제출 리스트에 ${ids.length}명을 저장했어요. 이 고객과의 미팅 준비 브리핑에 보여요.`);
    } catch (error) {
      setMessage(getApiErrorMessage(error));
    } finally {
      setLoading(false);
    }
  };

  const task = result?.task;
  const headcount = result?.requirement?.headcount?.value;
  const dirty = !sameSelection(selected, saved);

  return (
    <div className="h-full overflow-auto p-6">
      <div className="mx-auto max-w-5xl text-left">
        <Link to="/talents" className="mb-3 inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-800"><ArrowLeft className="h-3.5 w-3.5" /> 인력</Link>
        <h2 className="text-[22px] font-bold tracking-tight text-slate-900" style={{ fontFamily: 'var(--display)' }}>후보 추천</h2>
        <p className="mb-4 text-sm text-slate-500">고객 요청에서 조건을 꺼내 확정된 후보 풀에서 찾아요. 점수는 규칙으로 계산하고, 누구를 낼지는 담당자가 골라요.</p>

        {task && (
          <div className="mb-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-xs font-semibold text-blue-600">{MATCH_TASK_LABELS[task.taskType] || task.taskType}{task.dueDate ? ` · 기한 ${task.dueDate}` : ' · 기한 확인 필요'}</p>
            <p className="mt-1 text-base font-bold text-slate-800">{task.content}</p>
            <ul className="mt-2 space-y-1">
              {result.sources.map((line, index) => (
                <li key={`${line.origin}-${index}`} className="text-xs text-slate-600">
                  <span className="mr-1 rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-500">{line.label || SOURCE_ORIGIN_LABELS[line.origin]}</span>{line.text}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="mb-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <label htmlFor="match-text" className="text-xs font-semibold text-slate-500">{taskId ? '요청 글 (고쳐서 다시 찾을 수 있어요. 할 일·회의록은 바뀌지 않아요)' : '요청 글'}</label>
          <textarea
            id="match-text"
            value={text}
            onChange={(event) => setText(event.target.value)}
            rows={3}
            placeholder="예: 자바·스프링 백엔드 3명, 경력 5년 이상, 2월부터 투입. 금융 경험 있으면 좋음"
            className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
          />
          <div className="mt-2 flex gap-2">
            <button type="button" onClick={search} disabled={loading || !text.trim()} className="flex items-center gap-1.5 rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-50">
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />} {taskId ? '이 글로 다시 찾기' : '찾기'}
            </button>
            {taskId && edited && (
              <button type="button" onClick={reset} disabled={loading} className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50">
                <RotateCcw className="h-4 w-4" /> 원래 요청으로
              </button>
            )}
          </div>
        </div>

        {message && <p className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{message}</p>}
        {taskId && !result && !message && <p className="flex items-center gap-2 text-sm text-slate-400"><Loader2 className="h-4 w-4 animate-spin" /> 찾는 중…</p>}

        {result && (
          <>
            <RequirementPanel requirement={result.requirement} />
            <p className="mb-3 mt-5 text-sm text-slate-600">
              <span className="font-semibold text-slate-800">{requirementSummary(result.requirement)}</span>
              {' — '}확정된 후보 {result.poolSize}명 중 {result.candidates.length}명 추천 · {result.excluded.length}명 제외
              {result.draftCount > 0 && <span className="text-slate-400"> · 확인 대기 이력서 {result.draftCount}건은 확정해야 추천에 들어가요</span>}
            </p>
            {result.poolSize === 0 && (
              <p className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
                아직 확정된 후보가 없어요. <Link to="/talents" className="text-blue-600 underline">인력</Link>에서 이력서를 올리고 확정해 주세요.
              </p>
            )}
            <div className="space-y-3">
              {result.candidates.map((candidate, index) => (
                <CandidateCard
                  key={candidate.profileId}
                  candidate={candidate}
                  rank={index + 1}
                  selectable={Boolean(taskId)}
                  checked={selected.includes(candidate.profileId)}
                  onToggle={() => setSelected((previous) => toggleSelection(previous, candidate.profileId))}
                />
              ))}
            </div>
            {result.excluded.length > 0 && (
              <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-4">
                <button type="button" onClick={() => setShowExcluded((value) => !value)} className="text-sm font-semibold text-slate-600">
                  필수 조건을 못 채운 후보 {result.excluded.length}명 {showExcluded ? '접기' : '보기'}
                </button>
                {showExcluded && (
                  <ul className="mt-2 space-y-1">
                    {result.excluded.map((item) => (
                      <li key={item.profileId} className="text-sm text-slate-600">
                        <Link to={`/talents/${item.profileId}`} className="font-semibold hover:underline">{item.name || '(이름 모름)'}</Link> — {item.reasons.join(', ')}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </>
        )}

        {taskId && result && (
          <div className="sticky bottom-0 mt-6 rounded-2xl border border-slate-200 bg-white/95 px-4 py-3 shadow-lg backdrop-blur">
            <div className="flex flex-wrap items-center gap-3">
            <p className="text-sm text-slate-700">
              제출 리스트 <span className="font-bold">{selected.length}명</span>{headcount ? ` / 요청 ${headcount}명` : ''}
              {notice && !dirty && <span className="ml-2 text-xs text-emerald-700">{notice}</span>}
            </p>
            <p className="text-[11px] text-slate-400">메일을 보내지 않아요. 고른 후보를 이 할 일에 붙여 두기만 해요.</p>
            <button type="button" onClick={save} disabled={loading || !dirty} className="ml-auto rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-40">
              제출 리스트 저장
            </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
