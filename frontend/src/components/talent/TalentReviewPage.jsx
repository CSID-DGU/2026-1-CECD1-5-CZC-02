import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, Loader2, X } from 'lucide-react';
import { confirmTalent, deleteTalent, getTalent, updateTalent } from '../../api/talents';
import { getApiErrorMessage } from '../../api/errors';
import {
  buildPatch, careerText, findEvidenceLine, formFromDocument, GRADE_LABELS, isValidBirthYear, MISSING_LABELS,
  periodText, SKILL_CATEGORIES, SOURCE_LABELS, sourceRows, STATUS_LABELS,
} from './talentUtils';

const SOURCE_STYLE = {
  RULE: 'bg-slate-100 text-slate-600',
  LLM: 'bg-violet-50 text-violet-700',
  HUMAN: 'bg-blue-50 text-blue-700',
};

function SourceBadge({ source }) {
  if (!source) return null;
  return <span className={`rounded px-1 py-px text-[10px] font-semibold ${SOURCE_STYLE[source] || ''}`}>{SOURCE_LABELS[source] || source}</span>;
}

/** 근거 글. 누르면 왼쪽 원문에서 그 줄을 강조한다. 사람이 넣은 값은 원문 근거가 없다. */
function Evidence({ item, onShow }) {
  if (!item?.evidence) return null;
  if (item.source === 'HUMAN') return <span className="text-[11px] text-slate-400">사람이 입력</span>;
  return (
    <button type="button" onClick={() => onShow(item.evidence)} className="max-w-full truncate text-left text-[11px] text-slate-500 underline decoration-dotted hover:text-blue-600" title={item.evidence}>
      근거: {item.evidence}
    </button>
  );
}

function Row({ label, children }) {
  return (
    <div className="grid grid-cols-[88px_1fr] items-start gap-2 border-b border-slate-50 py-2">
      <div className="pt-1 text-xs font-semibold text-slate-500">{label}</div>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

/** 이력서 확인 화면: 원문 ↔ 뽑은 값. 사람이 고치고 확정해야 후보 풀에 등록된다 (수정 기록이 남는다). */
export function TalentReviewPage() {
  const { profileId } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState(null);
  const [form, setForm] = useState(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [focusLine, setFocusLine] = useState(-1);
  const [newSkill, setNewSkill] = useState({ name: '', category: 'OTHER' });
  const [newIndustry, setNewIndustry] = useState('');
  const sourcePane = useRef(null);

  const apply = (data) => {
    setDetail(data);
    setForm(formFromDocument(data.document));
  };

  useEffect(() => {
    let ignore = false;
    getTalent(profileId)
      .then((data) => { if (!ignore) apply(data); })
      .catch((error) => { if (!ignore) setMessage(getApiErrorMessage(error)); });
    return () => { ignore = true; };
  }, [profileId]);

  const rows = useMemo(() => sourceRows(detail?.sourceText), [detail?.sourceText]);
  const evidenceLines = useMemo(() => {
    const document = detail?.document;
    if (!document) return new Set();
    const texts = [document.name, document.birthYear, document.grade, ...(document.projects || []), ...(document.skills || []),
      ...(document.industries || []), ...(document.certifications || [])]
      .filter((item) => item && item.source !== 'HUMAN').map((item) => item.evidence);
    return new Set(texts.map((text) => findEvidenceLine(rows, text)).filter((index) => index >= 0));
  }, [detail?.document, rows]);

  const showEvidence = (evidence) => {
    const index = findEvidenceLine(rows, evidence);
    setFocusLine(index);
    if (index >= 0) {
      sourcePane.current?.querySelector(`[data-line="${index}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  };

  if (message && !detail) {
    return <div className="p-6 text-sm text-red-600">{message}</div>;
  }
  if (!detail || !form) {
    return <div className="flex items-center gap-2 p-6 text-sm text-slate-400"><Loader2 className="h-4 w-4 animate-spin" /> 불러오는 중…</div>;
  }

  const { summary, document } = detail;
  const editable = summary.status === 'DRAFT' || summary.status === 'CONFIRMED';
  const patch = document ? buildPatch(document, form) : {};
  const dirty = Object.keys(patch).length > 0;
  const birthOk = isValidBirthYear(form.birthYear);

  const save = async () => {
    if (!dirty || !birthOk) return null;
    setBusy(true);
    try {
      const data = await updateTalent(profileId, patch);
      apply(data);
      setMessage('저장했어요. 고친 칸은 수정 기록으로 남아요.');
      return data;
    } catch (error) {
      setMessage(getApiErrorMessage(error));
      throw error;
    } finally {
      setBusy(false);
    }
  };

  const confirm = async () => {
    if (!window.confirm('이 사람을 인력 후보 풀에 등록할까요? 확정 후에도 값은 고칠 수 있어요.')) return;
    try {
      if (dirty) await save();
      setBusy(true);
      apply(await confirmTalent(profileId));
      setMessage('후보 풀에 등록했어요.');
      window.dispatchEvent(new Event('talents:changed'));
    } catch (error) {
      setMessage(getApiErrorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!window.confirm('이 이력서와 뽑은 내용을 지울까요? 되돌릴 수 없어요.')) return;
    try {
      await deleteTalent(profileId);
      window.dispatchEvent(new Event('talents:changed'));
      navigate('/talents');
    } catch (error) {
      setMessage(getApiErrorMessage(error));
    }
  };

  const addSkill = () => {
    const name = newSkill.name.trim();
    if (!name || form.skills.some((skill) => skill.name === name)) return;
    setForm({ ...form, skills: [...form.skills, { name, category: newSkill.category }] });
    setNewSkill({ name: '', category: newSkill.category });
  };
  const addIndustry = () => {
    const name = newIndustry.trim();
    if (!name || form.industries.includes(name)) return;
    setForm({ ...form, industries: [...form.industries, name] });
    setNewIndustry('');
  };
  const skillSource = (name) => document?.skills?.find((skill) => skill.name === name);

  return (
    <div className="flex h-full flex-col text-left">
      <div className="flex flex-wrap items-center gap-3 border-b border-slate-200 bg-white px-6 py-3">
        <Link to="/talents" className="flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800"><ArrowLeft className="h-4 w-4" /> 인력</Link>
        <h2 className="text-lg font-bold text-slate-900">{document?.name?.value || '(이름 모름)'}</h2>
        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">{STATUS_LABELS[summary.status]}</span>
        <span className="truncate text-xs text-slate-400">{summary.fileName}</span>
        <div className="ml-auto flex gap-2">
          <button type="button" onClick={remove} disabled={busy} className="rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50">지우기</button>
          {editable && (
            <button type="button" onClick={() => save().catch(() => {})} disabled={busy || !dirty || !birthOk} className="rounded-xl border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-40">저장</button>
          )}
          {summary.status === 'DRAFT' && (
            <button type="button" onClick={confirm} disabled={busy || !birthOk} className="rounded-xl bg-blue-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-60">확정하고 후보 풀에 등록</button>
          )}
        </div>
      </div>

      <div className="space-y-2 px-6 pt-3">
        {message && <p className="text-sm text-slate-600">{message}</p>}
        {summary.status === 'FAILED' && <p className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{summary.errorMessage || '이력서를 읽지 못했어요.'} 다시 올려 주세요.</p>}
        {summary.llmFailed && (
          <p className="flex items-center gap-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
            <AlertTriangle className="h-4 w-4 shrink-0" /> AI(로컬 LLM)가 응답하지 않아 표에서 규칙으로 읽은 값만 있어요. 빈 칸은 원문을 보고 직접 채워 주세요.
          </p>
        )}
        {detail.duplicates?.length > 0 && (
          <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
            같은 사람일 수 있어요 (이름·생년 같음):{' '}
            {detail.duplicates.map((dup) => (
              <Link key={dup.profileId} to={`/talents/${dup.profileId}`} className="mr-2 font-semibold underline">{dup.name} · {STATUS_LABELS[dup.status]}</Link>
            ))}
            — 자동으로 합치지 않아요. 한쪽을 지우거나 그대로 두세요.
          </p>
        )}
        {summary.missingFields.length > 0 && summary.status !== 'FAILED' && (
          <p className="text-xs text-slate-500">이력서에서 못 찾은 칸: {summary.missingFields.map((key) => MISSING_LABELS[key] || key).join(', ')} (추측해서 채우지 않았어요)</p>
        )}
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 p-6 lg:grid-cols-2">
        <div ref={sourcePane} className="min-h-[240px] overflow-auto rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
          <div className="mb-2 text-xs font-semibold text-slate-500">원문 (노란 줄 = 값을 뽑은 곳, 근거를 누르면 위치로 가요)</div>
          {rows.length <= 1 && !rows[0]?.line && <p className="text-sm text-slate-400">원문이 없어요.</p>}
          <div className="text-[12px] leading-5">
            {rows.map((row) => (
              <div
                key={row.index}
                data-line={row.index}
                className={`flex gap-2 border-l-2 px-1 ${row.index === focusLine ? 'border-blue-500 bg-blue-100' : evidenceLines.has(row.index) ? 'border-amber-400 bg-amber-50' : 'border-transparent'}`}
              >
                <span className="w-8 shrink-0 select-none text-right tabular-nums text-slate-300">{row.index + 1}</span>
                {row.cells ? (
                  <span className="flex flex-wrap gap-1">
                    {row.cells.map((cell, index) => <span key={index} className="rounded border border-slate-200 px-1">{cell}</span>)}
                  </span>
                ) : (
                  <span className="whitespace-pre-wrap break-all">{row.line}</span>
                )}
              </div>
            ))}
          </div>
        </div>

        <div className="min-h-0 overflow-auto rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          {document && (
            <>
              <Row label="이름">
                <input value={form.name} disabled={!editable} onChange={(e) => setForm({ ...form, name: e.target.value })} className="w-full rounded-lg border border-slate-200 px-2 py-1 text-sm" />
                <div className="mt-0.5 flex items-center gap-1"><SourceBadge source={document.name?.source} /><Evidence item={document.name} onShow={showEvidence} /></div>
              </Row>
              <Row label="생년">
                <input value={form.birthYear} disabled={!editable} inputMode="numeric" onChange={(e) => setForm({ ...form, birthYear: e.target.value })} placeholder="모름" className={`w-32 rounded-lg border px-2 py-1 text-sm ${birthOk ? 'border-slate-200' : 'border-red-400'}`} />
                {!birthOk && <span className="ml-2 text-xs text-red-600">1900~2100 숫자</span>}
                <div className="mt-0.5 flex items-center gap-1"><SourceBadge source={document.birthYear?.source} /><Evidence item={document.birthYear} onShow={showEvidence} /></div>
              </Row>
              <Row label="등급">
                <select value={form.grade} disabled={!editable} onChange={(e) => setForm({ ...form, grade: e.target.value })} className="rounded-lg border border-slate-200 px-2 py-1 text-sm">
                  <option value="">모름 (적혀 있지 않음)</option>
                  {Object.entries(GRADE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
                <div className="mt-0.5 flex items-center gap-1"><SourceBadge source={document.grade?.source} /><Evidence item={document.grade} onShow={showEvidence} /></div>
              </Row>
              <Row label="총 경력">
                <div className="pt-1 text-sm text-slate-800">{careerText(document.totalCareer?.months)}
                  {document.totalCareer && <span className="ml-2 text-xs text-slate-400">{document.totalCareer.basis === 'PROJECTS' ? '프로젝트' : '근무'} 기간 {document.totalCareer.periodCount}개를 합침 (겹친 달은 한 번) · 규칙 계산</span>}
                </div>
              </Row>
              <Row label="투입 가능">
                <div className="pt-1 text-sm text-slate-800">
                  {document.availableFrom?.date || (document.availableFrom?.basis === 'NO_ONGOING_PROJECT' ? '진행 중 프로젝트 없음 (확인 필요)' : document.availableFrom ? '진행 중 · 종료일 모름' : '모름')}
                  <span className="ml-2 text-xs text-slate-400">진행 중 프로젝트 종료 다음 달 1일 · 규칙 계산</span>
                </div>
                {document.availableFrom?.evidence && <Evidence item={{ evidence: document.availableFrom.evidence, source: 'RULE' }} onShow={showEvidence} />}
              </Row>
              <Row label="기술">
                <div className="flex flex-wrap gap-1">
                  {form.skills.map((skill) => {
                    const original = skillSource(skill.name);
                    return (
                      <span key={skill.name} className={`group inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs ${original?.source === 'LLM' ? 'bg-violet-50 text-violet-800' : original ? 'bg-slate-100 text-slate-800' : 'bg-blue-50 text-blue-800'}`}>
                        <button type="button" onClick={() => original && showEvidence(original.evidence)} title={original?.evidence || '사람이 입력'}>{skill.name}</button>
                        {editable && <button type="button" onClick={() => setForm({ ...form, skills: form.skills.filter((item) => item.name !== skill.name) })} className="text-slate-400 hover:text-red-600"><X className="h-3 w-3" /></button>}
                      </span>
                    );
                  })}
                </div>
                {editable && (
                  <div className="mt-1.5 flex gap-1">
                    <input value={newSkill.name} onChange={(e) => setNewSkill({ ...newSkill, name: e.target.value })} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addSkill(); } }} placeholder="기술 추가" className="w-32 rounded-lg border border-slate-200 px-2 py-0.5 text-xs" />
                    <select value={newSkill.category} onChange={(e) => setNewSkill({ ...newSkill, category: e.target.value })} className="rounded-lg border border-slate-200 px-1 text-xs">
                      {SKILL_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}
                    </select>
                    <button type="button" onClick={addSkill} className="rounded-lg border border-slate-200 px-2 text-xs">추가</button>
                  </div>
                )}
                <p className="mt-1 text-[11px] text-slate-400">회색=표에서 규칙으로, 보라=AI가 줄글에서, 파랑=사람이 추가. 기술을 누르면 근거 위치로 가요.</p>
              </Row>
              <Row label="산업">
                <div className="flex flex-wrap gap-1">
                  {form.industries.map((name) => (
                    <span key={name} className="inline-flex items-center gap-1 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-800">
                      <button type="button" onClick={() => { const item = document.industries?.find((x) => x.name === name); if (item) showEvidence(item.evidence); }}>{name}</button>
                      {editable && <button type="button" onClick={() => setForm({ ...form, industries: form.industries.filter((item) => item !== name) })} className="text-slate-400 hover:text-red-600"><X className="h-3 w-3" /></button>}
                    </span>
                  ))}
                </div>
                {editable && (
                  <div className="mt-1.5 flex gap-1">
                    <input value={newIndustry} onChange={(e) => setNewIndustry(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addIndustry(); } }} placeholder="산업 추가 (예: 금융)" className="w-40 rounded-lg border border-slate-200 px-2 py-0.5 text-xs" />
                    <button type="button" onClick={addIndustry} className="rounded-lg border border-slate-200 px-2 text-xs">추가</button>
                  </div>
                )}
              </Row>
              {document.certifications?.length > 0 && (
                <Row label="자격증">
                  <ul className="space-y-0.5 text-sm">
                    {document.certifications.map((cert, index) => (
                      <li key={index} className="flex items-center gap-1">{cert.name}{cert.acquiredOn ? <span className="text-xs text-slate-400">({cert.acquiredOn})</span> : null}<SourceBadge source={cert.source} /></li>
                    ))}
                  </ul>
                </Row>
              )}

              <div className="mt-4 mb-1 text-xs font-semibold text-slate-500">프로젝트 {document.projects.length}개 (원문 기간으로 규칙이 읽음 · 여기서는 고치지 않아요)</div>
              <div className="space-y-1.5">
                {document.projects.map((project, index) => (
                  <button key={index} type="button" onClick={() => showEvidence(project.evidence)} className="block w-full rounded-xl border border-slate-100 p-2 text-left hover:border-blue-200 hover:bg-blue-50/40">
                    <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                      <span className="truncate">{project.name || '(이름 없음)'}</span><SourceBadge source={project.source} />
                      <span className="ml-auto shrink-0 text-xs font-normal text-slate-500">{periodText(project.period)}</span>
                    </div>
                    <div className="mt-0.5 truncate text-xs text-slate-500">
                      {[project.client, project.role, project.industry].filter(Boolean).join(' · ')}
                      {(project.environment?.languages?.length || project.environment?.dbms?.length) ? ` · ${[...(project.environment.languages || []), ...(project.environment.dbms || [])].join(', ')}` : ''}
                    </div>
                  </button>
                ))}
              </div>

              {detail.edits?.length > 0 && (
                <div className="mt-4">
                  <div className="mb-1 text-xs font-semibold text-slate-500">수정 기록</div>
                  <ul className="space-y-0.5 text-[11px] text-slate-500">
                    {detail.edits.map((edit) => (
                      <li key={edit.revisionId}>{edit.decidedAt?.replace('T', ' ')} · {edit.fieldName} · {edit.action === 'ACCEPTED' ? '확정' : '고침'}</li>
                    ))}
                  </ul>
                </div>
              )}
              <p className="mt-4 text-[11px] text-slate-400">
                {detail.llmModel ? `AI 모델 ${detail.llmModel} (로컬) · ` : ''}AI가 낸 값은 원문에 근거가 있는 것만 남겼어요.
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
