import { useCallback, useEffect, useState } from 'react';
import { AlertCircle, CalendarDays, CheckCircle2, ClipboardCheck, Loader2, RefreshCw } from 'lucide-react';
import { getPreparationBriefing } from '../../api/briefings';

function toDateInput(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function PreparationCard({ item }) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start gap-3">
        <span className="rounded-xl bg-violet-50 p-2 text-violet-600"><CalendarDays className="h-5 w-5" /></span>
        <div className="min-w-0 flex-1">
          <p className="text-base font-bold text-slate-800">{item.content || '내용이 없는 작업'}</p>
          <p className="mt-1 text-xs text-slate-400">
            {item.taskId} · {item.taskType || '분류 없음'}{item.dueDate ? ` · 예정일 ${item.dueDate}` : ''}
          </p>
        </div>
      </div>
      <div className="mt-5 rounded-xl bg-slate-50 p-4">
        <div className="mb-3 flex items-center gap-2">
          <ClipboardCheck className="h-4 w-4 text-violet-600" />
          <h2 className="text-sm font-bold text-slate-700">준비할 항목</h2>
        </div>
        {item.preparationItems?.length ? (
          <ul className="space-y-2">
            {item.preparationItems.map((preparationItem) => (
              <li key={preparationItem} className="flex items-start gap-2 text-sm text-slate-600">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-500" />
                <span>{preparationItem}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-slate-400">등록된 준비 항목이 없습니다.</p>
        )}
      </div>
    </article>
  );
}

export function PreparationBriefingPage() {
  const [date, setDate] = useState(() => toDateInput(new Date()));
  const [briefing, setBriefing] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  const loadBriefing = useCallback(async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      setBriefing(await getPreparationBriefing(date));
    } catch (error) {
      setErrorMessage(error.response?.data?.message || '미팅 준비 브리핑을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => { loadBriefing(); }, [loadBriefing]);

  return (
    <div className="h-full overflow-y-auto p-6 lg:p-8">
      <div className="mx-auto max-w-4xl">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="mb-1 text-sm font-semibold text-violet-600">D · 일정·브리핑</p>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">미팅 준비 브리핑</h1>
            <p className="mt-1 text-sm text-slate-500">기준일 다음 날의 확정 미팅과 준비 항목을 확인합니다.</p>
          </div>
          <div className="flex items-center gap-2">
            <label htmlFor="preparation-date" className="text-sm text-slate-500">기준일</label>
            <input id="preparation-date" type="date" value={date} onChange={(event) => setDate(event.target.value)} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm" />
            <button type="button" onClick={loadBriefing} disabled={loading} className="rounded-lg border border-slate-200 bg-white p-2 text-slate-600 shadow-sm hover:bg-slate-50 disabled:opacity-50" aria-label="준비 브리핑 새로고침">
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>

        {errorMessage && <div className="mb-5 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"><AlertCircle className="h-4 w-4" /><span>{errorMessage}</span></div>}
        {loading && !briefing ? (
          <div className="flex min-h-64 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-400"><Loader2 className="mr-2 h-5 w-5 animate-spin" />브리핑을 불러오는 중입니다.</div>
        ) : (
          <>
            <div className="mb-5 flex items-center justify-between rounded-2xl border border-violet-100 bg-violet-50/70 px-5 py-4">
              <div><p className="text-xs font-semibold text-violet-600">준비 대상 날짜</p><p className="mt-1 text-lg font-bold text-slate-800">{briefing?.briefingDate || '날짜 없음'}</p></div>
              <span className="rounded-full bg-white px-3 py-1 text-sm font-semibold text-violet-700">{briefing?.items?.length || 0}건</span>
            </div>
            {briefing?.items?.length ? <div className="space-y-4">{briefing.items.map((item) => <PreparationCard key={item.taskId} item={item} />)}</div> : <div className="rounded-2xl border border-dashed border-slate-200 bg-white px-5 py-12 text-center text-sm text-slate-400">다음 날 준비할 확정 작업이 없습니다.</div>}
            {briefing?.generatedAt && <p className="mt-5 text-right text-xs text-slate-400">생성 시각: {new Date(briefing.generatedAt).toLocaleString('ko-KR')}</p>}
          </>
        )}
      </div>
    </div>
  );
}

