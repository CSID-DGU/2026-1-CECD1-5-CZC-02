import { useState } from 'react';
import { CalendarCheck2 } from 'lucide-react';
import { registerTaskOnCalendar } from '../../api/briefings';
import { calendarRegistrationResult } from './calendarRegistrationResult';

export function TaskCalendarRegistrationButton({ taskId }) {
  const [scheduleDateTime, setScheduleDateTime] = useState('');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);

  const register = async () => {
    if (!scheduleDateTime) {
      setMessage('등록할 날짜와 시간을 선택하세요.');
      return;
    }
    setSaving(true);
    setMessage('');
    try {
      const schedule = await registerTaskOnCalendar(taskId, `${scheduleDateTime}:00`);
      setMessage(calendarRegistrationResult(schedule).message);
    } catch (error) {
      setMessage(error.response?.data?.message || '캘린더 등록에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">
      <input aria-label="캘린더 등록 날짜와 시간" type="datetime-local" value={scheduleDateTime}
        onChange={(event) => { setScheduleDateTime(event.target.value); setMessage(''); }}
        disabled={saving} className="rounded-lg border border-slate-200 px-2 py-1.5 text-xs text-slate-700" />
      <button type="button" onClick={register} disabled={saving}
        className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-emerald-700 disabled:bg-slate-300">
        <CalendarCheck2 className="h-3.5 w-3.5" />{saving ? '등록 중...' : '캘린더에 등록'}
      </button>
      {message && <span className="text-xs text-slate-500">{message}</span>}
    </div>
  );
}
