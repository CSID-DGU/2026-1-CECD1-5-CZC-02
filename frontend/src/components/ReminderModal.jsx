import { X, Bell, CalendarClock } from 'lucide-react';

// 알림 상세에 들어오는 memo가 "summary:/보낸 사람:/참석자:" 등 라벨 형태이므로
// 사람이 읽기 좋은 요약만 남긴다.
function cleanReminderContent(content = '') {
  return content
    .split('\n')
    .filter((line) => {
      const trimmed = line.trim();
      if (!trimmed) return false;
      return !/^(보낸 사람|참석자|업무 유형|actionReason|판단 근거)\s*[:：]/.test(trimmed);
    })
    .map((line) => line.replace(/^(summary|nextAction|요약|다음 행동)\s*[:：]\s*/i, '').trim())
    .filter(Boolean)
    .join('\n');
}

export function ReminderModal({ isOpen, onClose, reminder }) {
  if (!isOpen || !reminder) return null;

  const isUrgent = reminder.kind === 'THIRTY_MINUTES';
  const accent = isUrgent
    ? { from: 'from-rose-500', to: 'to-orange-500', text: 'text-rose-600', soft: 'from-rose-50 to-orange-50/50' }
    : { from: 'from-sky-500', to: 'to-indigo-500', text: 'text-blue-600', soft: 'from-blue-50 to-violet-50/50' };
  const content = cleanReminderContent(reminder.content);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
      <div className="w-full max-w-md overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <div className="flex items-center gap-2.5">
            <span className={`flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-br ${accent.from} ${accent.to} shadow-sm`}>
              <Bell className="h-4 w-4 text-white" />
            </span>
            <h3 className="text-sm font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>
              {isUrgent ? '30분 전 일정 알림' : '일정 알림'}
            </h3>
          </div>
          <button onClick={onClose} className="rounded-lg p-1.5 hover:bg-slate-100">
            <X className="h-4 w-4 text-slate-500" />
          </button>
        </div>

        <div className="space-y-4 px-5 py-5">
          {isUrgent && (
            <div className="rounded-xl border border-rose-100 bg-gradient-to-br from-rose-50 to-orange-50/60 p-3 text-left">
              <p className="text-xs font-semibold text-rose-600">곧 시작되는 일정입니다</p>
              <p className="mt-0.5 text-sm text-rose-700">30분 뒤 예정되어 있어요.</p>
            </div>
          )}

          <div className={`rounded-xl border border-slate-100 bg-gradient-to-br ${accent.soft} p-4 text-left`}>
            <p className={`text-xs font-semibold ${accent.text}`}>{reminder.label || '예정된 일정'}</p>
            <p className="mt-1 text-base font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>
              {reminder.title}
            </p>
          </div>

          {reminder.items?.length > 0 ? (
            <div className="space-y-2">
              {reminder.items.map((item) => (
                <div
                  key={`${item.id}-${item.dateTime}`}
                  className="flex items-start gap-2.5 rounded-xl border border-slate-100 bg-slate-50/60 p-3 text-left"
                >
                  <CalendarClock className="mt-0.5 h-4 w-4 shrink-0 text-blue-500" />
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-slate-800">{item.title}</p>
                    <p className="mt-0.5 text-xs text-slate-500">{item.date} · {item.time}</p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-xl border border-slate-100 bg-slate-50/60 px-3 py-2 text-left">
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">일정 종류</p>
                  <p className="mt-0.5 text-sm font-medium text-slate-700">{reminder.type || '일정'}</p>
                </div>
                <div className="rounded-xl border border-slate-100 bg-slate-50/60 px-3 py-2 text-left">
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">날짜 / 시간</p>
                  <p className="mt-0.5 text-sm font-medium text-slate-700">{reminder.date} {reminder.time}</p>
                </div>
              </div>

              {content && (
                <div className="text-left">
                  <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">상세 내용</p>
                  <p className="whitespace-pre-wrap break-words text-sm text-slate-600">{content}</p>
                </div>
              )}
            </>
          )}
        </div>

        <div className="border-t border-slate-200 px-5 py-4">
          <button
            onClick={onClose}
            className={`w-full rounded-lg bg-gradient-to-r ${accent.from} ${accent.to} py-2.5 text-sm font-semibold text-white shadow-sm transition hover:opacity-90`}
          >
            확인
          </button>
        </div>
      </div>
    </div>
  );
}
