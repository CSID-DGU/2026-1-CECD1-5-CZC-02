import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AlertTriangle, Download, FileUp, Loader2 } from 'lucide-react';
import { downloadTalentCsv, listTalents, uploadResume } from '../../api/talents';
import { getApiErrorMessage } from '../../api/errors';
import { availabilityText, careerText, GRADE_LABELS, STATUS_LABELS } from './talentUtils';

const TABS = [
  { value: '', label: '전체' },
  { value: 'DRAFT', label: '확인 대기' },
  { value: 'CONFIRMED', label: '등록됨' },
  { value: 'PROCESSING', label: '분석 중' },
  { value: 'FAILED', label: '읽지 못함' },
];
const STATUS_STYLE = {
  PROCESSING: 'bg-slate-100 text-slate-600',
  DRAFT: 'bg-amber-50 text-amber-700',
  CONFIRMED: 'bg-emerald-50 text-emerald-700',
  FAILED: 'bg-red-50 text-red-700',
};
const POLL_MS = 5000;

/** 인력 메뉴: 이력서 올리기 + 인력 후보 풀 목록·필터. 고객 담당자(사람 메뉴)와 따로다. */
export function TalentPoolPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const status = searchParams.get('status') || '';
  const [filters, setFilters] = useState({ q: '', skills: '', minYears: '', industry: '', availableBy: '' });
  const [applied, setApplied] = useState(filters);
  const [rows, setRows] = useState(null);
  const [message, setMessage] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadNotes, setUploadNotes] = useState([]);
  const fileInput = useRef(null);

  // 결과는 then 안에서만 상태에 넣는다 (화면을 떠난 뒤 도착한 응답은 버린다)
  const fetchRows = useCallback(() => listTalents({
    status,
    q: applied.q,
    skills: applied.skills,
    minCareerMonths: applied.minYears === '' ? '' : Math.round(Number(applied.minYears) * 12),
    industry: applied.industry,
    availableBy: applied.availableBy,
  }), [status, applied]);

  const load = useCallback(() => fetchRows()
    .then((data) => { setRows(data); setMessage(''); })
    .catch((error) => setMessage(getApiErrorMessage(error))), [fetchRows]);

  useEffect(() => {
    let ignore = false;
    fetchRows()
      .then((data) => { if (!ignore) { setRows(data); setMessage(''); } })
      .catch((error) => { if (!ignore) setMessage(getApiErrorMessage(error)); });
    return () => { ignore = true; };
  }, [fetchRows]);

  // 분석 중인 이력서가 있으면 몇 초마다 다시 본다 (줄글 이력서는 몇 분 걸릴 수 있다)
  const processing = rows?.some((row) => row.status === 'PROCESSING');
  useEffect(() => {
    if (!processing) return undefined;
    const timer = setInterval(load, POLL_MS);
    return () => clearInterval(timer);
  }, [processing, load]);

  const handleFiles = async (files) => {
    if (!files?.length) return;
    setUploading(true);
    const notes = [];
    for (const file of Array.from(files)) {
      try {
        await uploadResume(file);
        notes.push({ name: file.name, ok: true });
      } catch (error) {
        notes.push({ name: file.name, ok: false, text: getApiErrorMessage(error) });
      }
    }
    setUploadNotes(notes);
    setUploading(false);
    if (fileInput.current) fileInput.current.value = '';
    window.dispatchEvent(new Event('talents:changed'));
    load();
  };

  const handleExport = async () => {
    try {
      await downloadTalentCsv();
    } catch (error) {
      setMessage(getApiErrorMessage(error));
    }
  };

  const setField = (key) => (event) => setFilters((prev) => ({ ...prev, [key]: event.target.value }));

  return (
    <div className="h-full overflow-auto p-6">
      <div className="mx-auto max-w-6xl text-left">
        <div className="mb-4 flex flex-wrap items-baseline gap-3">
          <h2 className="text-[22px] font-bold tracking-tight text-slate-900" style={{ fontFamily: 'var(--display)' }}>
            인력
          </h2>
          <p className="text-sm text-slate-500">
            이력서를 올리면 AI가 정리해요. 사람이 확인하고 확정한 사람만 후보 풀에 등록돼요. 고객 담당자(사람 메뉴)와는 따로 관리해요.
          </p>
        </div>

        <div className="mb-4 rounded-2xl border border-dashed border-slate-300 bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-center gap-3">
            <button
              type="button"
              disabled={uploading}
              onClick={() => fileInput.current?.click()}
              className="flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-60"
            >
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileUp className="h-4 w-4" />}
              이력서 올리기 (.docx)
            </button>
            <input
              ref={fileInput}
              type="file"
              accept=".docx"
              multiple
              className="hidden"
              onChange={(event) => handleFiles(event.target.files)}
            />
            <p className="text-xs text-slate-500">
              여러 개를 한 번에 올릴 수 있어요. .doc·.hwp는 워드에서 .docx로 다시 저장해 주세요. 줄글 이력서는 분석에 몇 분 걸릴 수 있어요.
            </p>
            <button
              type="button"
              onClick={handleExport}
              className="ml-auto flex items-center gap-1.5 rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
              title="확정한 후보만 SALESMAP 연락처 가져오기용 CSV로 받아요. 자동으로 보내지 않아요."
            >
              <Download className="h-3.5 w-3.5" /> 등록된 후보 CSV
            </button>
          </div>
          {uploadNotes.length > 0 && (
            <ul className="mt-3 space-y-1 text-xs">
              {uploadNotes.map((note, index) => (
                <li key={`${note.name}-${index}`} className={note.ok ? 'text-slate-600' : 'text-red-600'}>
                  {note.ok ? '접수됨' : '올리지 못함'} · {note.name}{note.text ? ` — ${note.text}` : ''}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="mb-3 flex flex-wrap gap-1.5">
          {TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              onClick={() => setSearchParams(tab.value ? { status: tab.value } : {})}
              className={`rounded-full px-3 py-1 text-xs font-semibold ${status === tab.value ? 'bg-slate-900 text-white' : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'}`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <form
          className="mb-4 grid grid-cols-2 gap-2 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-6"
          onSubmit={(event) => { event.preventDefault(); setApplied(filters); }}
        >
          <input value={filters.q} onChange={setField('q')} placeholder="이름·기술 검색" className="rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
          <input value={filters.skills} onChange={setField('skills')} placeholder="기술 (쉼표로: Java,Oracle)" className="rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
          <input value={filters.minYears} onChange={setField('minYears')} type="number" min="0" step="0.5" placeholder="최소 경력(년)" className="rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
          <input value={filters.industry} onChange={setField('industry')} placeholder="산업 (예: 금융)" className="rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
          <label className="flex items-center gap-1 text-xs text-slate-500">
            투입 가능
            <input value={filters.availableBy} onChange={setField('availableBy')} type="date" className="min-w-0 flex-1 rounded-lg border border-slate-200 px-2 py-1.5 text-sm" />
          </label>
          <button type="submit" className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-semibold text-white hover:bg-slate-700">찾기</button>
        </form>

        <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
          {message && <p className="p-4 text-sm text-red-600">{message}</p>}
          {!message && rows === null && (
            <p className="flex items-center gap-2 p-4 text-sm text-slate-400"><Loader2 className="h-4 w-4 animate-spin" /> 불러오는 중…</p>
          )}
          {rows?.length === 0 && <p className="p-8 text-center text-sm text-slate-500">조건에 맞는 인력이 없어요.</p>}
          {rows?.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-100 text-left text-xs text-slate-500">
                  <tr>
                    <th className="px-4 py-2">이름</th>
                    <th className="px-2 py-2">상태</th>
                    <th className="px-2 py-2">경력</th>
                    <th className="px-2 py-2">등급</th>
                    <th className="px-2 py-2">주요 기술</th>
                    <th className="px-2 py-2">산업</th>
                    <th className="px-2 py-2">투입 가능</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr
                      key={row.profileId}
                      onClick={() => row.status !== 'PROCESSING' && navigate(`/talents/${row.profileId}`)}
                      className={`border-b border-slate-50 ${row.status === 'PROCESSING' ? '' : 'cursor-pointer hover:bg-slate-50'}`}
                    >
                      <td className="px-4 py-2.5">
                        <div className="font-semibold text-slate-800">{row.name || '(이름 모름)'}{row.birthYear ? <span className="ml-1 text-xs font-normal text-slate-400">{row.birthYear}년생</span> : null}</div>
                        <div className="truncate text-xs text-slate-400" style={{ maxWidth: 220 }}>{row.fileName}</div>
                      </td>
                      <td className="px-2 py-2.5">
                        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_STYLE[row.status]}`}>
                          {row.status === 'PROCESSING' && <Loader2 className="h-3 w-3 animate-spin" />}
                          {STATUS_LABELS[row.status]}
                          {row.status === 'PROCESSING' && row.queuePosition > 1 ? ` · 대기 ${row.queuePosition - 1}건` : ''}
                        </span>
                        {row.llmFailed && (
                          <div className="mt-1 flex items-center gap-1 text-[11px] text-amber-700" title="AI(로컬 LLM)가 응답하지 않아 표에서 읽은 값만 있어요. 빈 칸을 직접 확인해 주세요.">
                            <AlertTriangle className="h-3 w-3" /> AI 실패 · 규칙 결과만
                          </div>
                        )}
                        {row.status === 'FAILED' && row.errorMessage && <div className="mt-1 text-[11px] text-red-600">{row.errorMessage}</div>}
                      </td>
                      <td className="px-2 py-2.5 text-slate-700">{row.status === 'PROCESSING' ? '' : careerText(row.careerMonths)}</td>
                      <td className="px-2 py-2.5 text-slate-700">{GRADE_LABELS[row.grade] || ''}</td>
                      <td className="px-2 py-2.5">
                        <div className="flex flex-wrap gap-1">
                          {row.skills.slice(0, 6).map((skill) => (
                            <span key={skill} className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-700">{skill}</span>
                          ))}
                          {row.skills.length > 6 && <span className="text-[11px] text-slate-400">+{row.skills.length - 6}</span>}
                        </div>
                      </td>
                      <td className="px-2 py-2.5 text-xs text-slate-600">{row.industries.join(', ')}</td>
                      <td className="px-2 py-2.5 text-xs text-slate-600">{row.status === 'PROCESSING' ? '' : availabilityText(row)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
