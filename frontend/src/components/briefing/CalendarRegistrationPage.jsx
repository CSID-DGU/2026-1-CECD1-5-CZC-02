import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, CalendarCheck2, CheckCircle2, Clock3, Loader2, RefreshCw } from 'lucide-react';
import { getConfirmedTasks, registerTaskOnCalendar } from '../../api/briefings';
import { calendarRegistrationResult } from './calendarRegistrationResult';
import { taskTypeLabel } from './taskTypeLabel';

function toDateInput(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function toDateTimeInput(item) {
  return '';
}

export function CalendarRegistrationPage() {
  const [date, setDate] = useState(() => toDateInput(new Date()));
  const [briefing, setBriefing] = useState(null);
  const [selectedTaskId, setSelectedTaskId] = useState('');
  const [scheduleDateTime, setScheduleDateTime] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [unregisteredMessage, setUnregisteredMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  const loadBriefing = useCallback(async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      const data = await getConfirmedTasks(date);
      setBriefing(data);
      const firstTask = data?.items?.[0];
      setSelectedTaskId(firstTask?.taskId || '');
      setScheduleDateTime(toDateTimeInput(firstTask));
    } catch (error) {
      setErrorMessage(error.response?.data?.message || '등록할 작업을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => { loadBriefing(); }, [loadBriefing]);

  const tasks = useMemo(() => {
    return briefing?.items || [];
  }, [briefing]);

  const handleTaskChange = (event) => {
    const taskId = event.target.value;
    const task = tasks.find((item) => item.taskId === taskId);
    setSelectedTaskId(taskId);
    setScheduleDateTime(toDateTimeInput(task));
    setMessage('');
    setUnregisteredMessage('');
    setErrorMessage('');
  };

  const submitRegistration = async () => {
    if (!selectedTaskId || !scheduleDateTime) {
      setErrorMessage('작업과 등록 시간을 모두 선택해 주세요.');
      return;
    }

    setSaving(true);
    setMessage('');
    setUnregisteredMessage('');
    setErrorMessage('');
    try {
      const schedule = await registerTaskOnCalendar(selectedTaskId, `${scheduleDateTime}:00`);
      const result = calendarRegistrationResult(schedule);
      if (result.registered) setMessage(result.message);
      else setUnregisteredMessage(result.message);
    } catch (error) {
      setErrorMessage(error.response?.data?.message || '캘린더 등록에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    submitRegistration();
  };

  return (
    <div className="h-full overflow-y-auto p-6 lg:p-8">
      <div className="mx-auto max-w-4xl">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">할 일 캘린더 등록</h1>
            <p className="mt-1 text-sm text-slate-500">확정된 작업에 사람이 직접 시간을 정해 일정을 등록합니다.</p>
          </div>
          <div className="flex items-center gap-2">
            <label htmlFor="registration-date" className="text-sm text-slate-500">기준일</label>
            <input id="registration-date" type="date" value={date} onChange={(event) => setDate(event.target.value)} className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm" />
            <button type="button" onClick={loadBriefing} disabled={loading} className="rounded-lg border border-slate-200 bg-white p-2 text-slate-600 shadow-sm hover:bg-slate-50 disabled:opacity-50" aria-label="작업 새로고침"><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} /></button>
          </div>
        </div>

        {errorMessage && <div className="mb-5 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"><AlertCircle className="h-4 w-4" /><span>{errorMessage}</span></div>}
        {message && <div className="mb-5 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700"><CheckCircle2 className="h-4 w-4" /><span>{message}</span></div>}
        {unregisteredMessage && <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800"><span className="flex items-center gap-2"><AlertCircle className="h-4 w-4 shrink-0" />{unregisteredMessage}</span><button type="button" onClick={submitRegistration} disabled={saving} className="rounded-lg border border-amber-300 bg-white px-3 py-1.5 text-xs font-bold text-amber-800 hover:bg-amber-100 disabled:opacity-50">{saving ? '다시 시도 중...' : '캘린더 등록 다시 시도'}</button></div>}

        {loading && !briefing ? <div className="flex min-h-64 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-400"><Loader2 className="mr-2 h-5 w-5 animate-spin" />등록할 작업을 불러오는 중입니다.</div> : (
          <div className="grid gap-5 lg:grid-cols-[1fr_1.2fr]">
            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="mb-4 flex items-center gap-3"><span className="rounded-xl bg-emerald-50 p-2 text-emerald-600"><CalendarCheck2 className="h-5 w-5" /></span><div><h2 className="font-bold text-slate-800">등록할 작업</h2><p className="text-xs text-slate-500">CONFIRMED 작업만 표시됩니다.</p></div></div>
              {tasks.length ? <div className="space-y-2">{tasks.map((task) => <button type="button" key={task.taskId} onClick={() => handleTaskChange({ target: { value: task.taskId } })} className={`w-full rounded-xl border p-3 text-left transition ${selectedTaskId === task.taskId ? 'border-emerald-400 bg-emerald-50' : 'border-slate-200 hover:border-emerald-200 hover:bg-slate-50'}`}><p className="text-sm font-semibold text-slate-800">{task.content || '내용이 없는 작업'}</p><p className="mt-1 text-xs text-slate-400">{taskTypeLabel(task.taskType)}{task.dueDate ? ` · 기한 ${task.dueDate}` : ''}</p></button>)}</div> : <p className="rounded-xl border border-dashed border-slate-200 px-4 py-8 text-center text-sm text-slate-400">등록할 확정 작업이 없습니다.</p>}
            </section>

            <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="mb-5 flex items-center gap-3"><Clock3 className="h-5 w-5 text-emerald-600" /><div><h2 className="font-bold text-slate-800">등록 시간 선택</h2><p className="text-xs text-slate-500">시간은 자동 확정하지 않고 사용자가 선택합니다.</p></div></div>
              <label htmlFor="task-select" className="mb-2 block text-sm font-semibold text-slate-700">작업</label>
              <select id="task-select" value={selectedTaskId} onChange={handleTaskChange} disabled={!tasks.length || saving} className="mb-5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 disabled:bg-slate-50"><option value="">작업을 선택하세요</option>{tasks.map((task) => <option key={task.taskId} value={task.taskId}>{task.content || '내용 없음'}</option>)}</select>
              <label htmlFor="schedule-date-time" className="mb-2 block text-sm font-semibold text-slate-700">캘린더 등록 일시</label>
              <input id="schedule-date-time" type="datetime-local" value={scheduleDateTime} onChange={(event) => setScheduleDateTime(event.target.value)} disabled={!selectedTaskId || saving} className="mb-5 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 disabled:bg-slate-50" />
              <button type="submit" disabled={!selectedTaskId || !scheduleDateTime || saving} className="flex w-full items-center justify-center gap-2 rounded-lg bg-emerald-600 px-4 py-3 text-sm font-bold text-white transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-300"><CalendarCheck2 className="h-4 w-4" />{saving ? '등록 중...' : '캘린더에 등록'}</button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}

