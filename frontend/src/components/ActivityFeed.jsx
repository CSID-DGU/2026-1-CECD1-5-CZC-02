import { CheckCircle2, Inbox, Loader2, Sparkles, X } from 'lucide-react';
import { ActivityContext } from './useActivityFeed';

export function ActivityProvider({ value, children }) {
  return <ActivityContext.Provider value={value}>{children}</ActivityContext.Provider>;
}

export function AnalysisProgressChip({ job, onClick }) {
  if (!job?.running) {
    return null;
  }

  return (
    <button
      type="button"
      onClick={onClick}
      title={job.currentTitle ? `분석 중: ${job.currentTitle}` : 'AI 분석 진행 중'}
      className="inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border border-blue-200 bg-blue-50 px-2.5 py-0.5 text-[11px] font-semibold text-blue-700 hover:bg-blue-100"
    >
      <Loader2 className="h-3 w-3 animate-spin" />
      {job.trigger === 'AUTO' ? '자동 분석' : 'AI 분석'} {job.done}/{job.total}
    </button>
  );
}

const TOAST_STYLES = {
  COLLECTED: { Icon: Inbox, className: 'text-sky-600' },
  ANALYSIS_STARTED: { Icon: Sparkles, className: 'text-indigo-500' },
  ANALYSIS_FINISHED: { Icon: CheckCircle2, className: 'text-green-600' },
};

export function ActivityToasts({ toasts, onDismiss, onOpen }) {
  if (toasts.length === 0) {
    return null;
  }

  return (
    <div className="pointer-events-none fixed right-4 top-16 z-50 flex w-80 flex-col gap-2">
      {toasts.map((toast) => {
        const { Icon, className } = TOAST_STYLES[toast.type] || TOAST_STYLES.ANALYSIS_STARTED;
        return (
          <div
            key={toast.id}
            className="pointer-events-auto flex items-start gap-2.5 rounded-xl border border-slate-200 bg-white/95 px-3.5 py-2.5 text-left shadow-lg backdrop-blur"
          >
            <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${className}`} />
            <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-left text-xs leading-5 text-slate-700">
              {toast.message}
            </button>
            <button type="button" onClick={() => onDismiss(toast.id)} aria-label="알림 닫기" className="text-slate-400 hover:text-slate-600">
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
