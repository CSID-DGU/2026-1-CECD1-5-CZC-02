import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, CalendarDays, CheckCircle2, Clock3, Loader2, RefreshCw } from 'lucide-react';
import { getTodayBriefing } from '../../api/briefings';
import { TaskCalendarRegistrationButton } from './TaskCalendarRegistrationButton';
import { taskTypeLabel } from './taskTypeLabel';

function toDateInput(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

const sections = [
  { key: 'dueToday', title: '오늘 할 일', description: '오늘까지 처리해야 하는 확정 작업', icon: CheckCircle2, color: 'blue' },
  { key: 'overdue', title: '기한 지난 일', description: '확정됐지만 기한을 넘긴 작업', icon: AlertCircle, color: 'rose' },
  { key: 'pendingConfirmation', title: '확인 대기', description: '사람 확인 후 확정할 작업', icon: Clock3, color: 'amber' },
  { key: 'tomorrowPreparation', title: '내일 준비', description: '내일 일정 전에 확인할 준비 항목', icon: CalendarDays, color: 'violet' },
];

const colorStyles = {
  blue: { icon: 'bg-blue-50 text-blue-600', badge: 'bg-blue-50 text-blue-700' },
  rose: { icon: 'bg-rose-50 text-rose-600', badge: 'bg-rose-50 text-rose-700' },
  amber: { icon: 'bg-amber-50 text-amber-600', badge: 'bg-amber-50 text-amber-700' },
  violet: { icon: 'bg-violet-50 text-violet-600', badge: 'bg-violet-50 text-violet-700' },
};

function BriefingItem({ item, canRegister }) {
  return (
    <article className="rounded-xl border border-slate-100 bg-white p-4 shadow-sm">
      <p className="text-sm font-semibold text-slate-800">{item.content || '내용이 없는 작업'}</p>
      <p className="mt-1 text-xs text-slate-400">
        {taskTypeLabel(item.taskType)}{item.dueDate ? ` · 기한 ${item.dueDate}` : ''}
      </p>
      {Array.isArray(item.preparationItems) && item.preparationItems.length > 0 && (
        <ul className="mt-3 space-y-1 border-t border-slate-100 pt-3 text-xs text-slate-600">
          {item.preparationItems.map((preparationItem) => (
            <li key={preparationItem} className="flex gap-2"><span className="text-violet-400">•</span><span>{preparationItem}</span></li>
          ))}
        </ul>
      )}
      {item.evidence && <p className="mt-3 border-t border-slate-100 pt-3 text-xs text-slate-500">출처: {item.evidence}</p>}
      {canRegister && <TaskCalendarRegistrationButton taskId={item.taskId} />}
    </article>
  );
}

function BriefingSection({ section, items }) {
  const Icon = section.icon;
  const styles = colorStyles[section.color];
  return (
    <section className="rounded-2xl border border-slate-200/70 bg-slate-50/70 p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className={`rounded-xl p-2 ${styles.icon}`}><Icon className="h-5 w-5" /></span>
          <div><h2 className="font-bold text-slate-800">{section.title}</h2><p className="text-xs text-slate-500">{section.description}</p></div>
        </div>
        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${styles.badge}`}>{items.length}</span>
      </div>
      {items.length > 0 ? <div className="space-y-2">{items.map((item) => <BriefingItem key={item.taskId} item={item} canRegister={section.key !== 'pendingConfirmation'} />)}</div> : (
        <p className="rounded-xl border border-dashed border-slate-200 bg-white px-4 py-5 text-center text-sm text-slate-400">현재 표시할 작업이 없습니다.</p>
      )}
    </section>
  );
}

export function TodayBriefingPage() {
  const [date, setDate] = useState(() => toDateInput(new Date()));
  const [briefing, setBriefing] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  const loadBriefing = useCallback(async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      setBriefing(await getTodayBriefing(date));
    } catch (error) {
      setErrorMessage(error.response?.data?.message || '오늘 브리핑을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => { loadBriefing(); }, [loadBriefing]);
  const counts = useMemo(() => sections.map(({ key }) => briefing?.[key]?.length || 0), [briefing]);

  return (
    <div className="h-full overflow-y-auto p-6 lg:p-8"><div className="mx-auto max-w-7xl">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div><h1 className="text-2xl font-extrabold tracking-tight text-slate-900">오늘의 영업 브리핑</h1><p className="mt-1 text-sm text-slate-500">해야 할 일과 내일 준비를 한 번에 확인합니다.</p></div>
        <div className="flex flex-wrap items-center gap-2"><Link to="/briefing/calendar-registration" className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">다른 할 일 캘린더 등록</Link><label htmlFor="briefing-date" className="text-sm text-slate-500">기준일</label><input id="briefing-date" type="date" value={date} onChange={(event) => setDate(event.target.value)} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm" /><button type="button" onClick={loadBriefing} disabled={loading} className="rounded-lg border border-slate-200 bg-white p-2 text-slate-600 shadow-sm hover:bg-slate-50 disabled:opacity-50" aria-label="브리핑 새로고침"><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} /></button></div>
      </div>
      {errorMessage && <div className="mb-5 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"><AlertCircle className="h-4 w-4" /><span>{errorMessage}</span></div>}
      {loading && !briefing ? <div className="flex min-h-64 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-400"><Loader2 className="mr-2 h-5 w-5 animate-spin" />브리핑을 불러오는 중입니다.</div> : <>
        <div className="mb-5 grid grid-cols-2 gap-3 md:grid-cols-4">{sections.map((section, index) => <div key={section.key} className="rounded-xl border border-slate-200/70 bg-white px-4 py-3 shadow-sm"><p className="text-xs text-slate-500">{section.title}</p><p className="mt-1 text-2xl font-extrabold text-slate-900">{counts[index]}</p></div>)}</div>
        <div className="grid gap-5 xl:grid-cols-2">{sections.map((section) => <BriefingSection key={section.key} section={section} items={briefing?.[section.key] || []} />)}</div>
        {briefing?.generatedAt && <p className="mt-5 text-right text-xs text-slate-400">생성 시각: {new Date(briefing.generatedAt).toLocaleString('ko-KR')}</p>}
      </>}
    </div></div>
  );
}

