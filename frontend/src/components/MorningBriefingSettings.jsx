import { useEffect, useState } from 'react';
import { Bell } from 'lucide-react';
import { getApiErrorMessage } from '../api/errors';
import { getNotificationSettings, updateNotificationSettings } from '../api/notifications';

/** 설정: 아침 묶음 브리핑을 받을 시간. 앱을 연 동안 하루 한 번 보여 준다. */
export function MorningBriefingSettings() {
  const [time, setTime] = useState('');
  const [saved, setSaved] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    getNotificationSettings()
      .then(({ morningTime }) => {
        if (active) {
          setTime(morningTime);
          setSaved(morningTime);
        }
      })
      .catch((failure) => { if (active) setError(getApiErrorMessage(failure)); });
    return () => { active = false; };
  }, []);

  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setMessage('');
    setError('');
    try {
      const { morningTime } = await updateNotificationSettings(time);
      setTime(morningTime);
      setSaved(morningTime);
      setMessage('저장했어요.');
    } catch (failure) {
      setError(getApiErrorMessage(failure));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 text-left shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-50">
            <Bell className="h-5 w-5 text-blue-600" />
          </span>
          <div>
            <h3 className="text-base font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>아침 묶음 브리핑</h3>
            <p className="mt-1 max-w-xl text-sm text-slate-500">
              이 시간 이후 앱을 열면 오늘 챙길 일을 <b>하루 한 번</b> 묶어서 보여 줘요. 일정 30분 전 즉시 알림은 하루 3건까지만 떠요.
            </p>
          </div>
        </div>
        <form onSubmit={save} className="flex items-center gap-2">
          <label className="text-sm text-slate-600">
            <span className="sr-only">아침 알림 시간</span>
            <input
              type="time"
              required
              aria-label="아침 알림 시간"
              value={time}
              onChange={(event) => { setTime(event.target.value); setMessage(''); }}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm"
            />
          </label>
          <button type="submit" disabled={busy || !time || time === saved} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40">
            저장
          </button>
        </form>
      </div>
      {message && <p role="status" className="mt-3 text-sm text-emerald-700">{message}</p>}
      {error && <p role="alert" className="mt-3 text-sm text-red-600">{error}</p>}
    </section>
  );
}
