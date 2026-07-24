import { Clock, Wind } from 'lucide-react';

export function JandiComingSoon() {
  return (
    <div className="flex h-full min-h-[70vh] items-center justify-center bg-gradient-to-b from-[#F6F8FC] to-[#EEF2F9] p-8">
      <div className="relative w-full max-w-md overflow-hidden rounded-2xl border border-white/60 bg-white/80 p-8 text-center shadow-[0_10px_40px_-18px_rgba(37,99,235,0.35)] backdrop-blur">
        <div className="absolute -right-16 -top-16 h-56 w-56 rounded-full bg-gradient-to-br from-blue-400/20 to-violet-400/10 blur-2xl" />
        <div className="relative">
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-500 to-indigo-500 shadow-lg shadow-blue-500/20">
            <Clock className="h-8 w-8 text-white" strokeWidth={2} />
          </div>
          <div className="mb-2 text-xl font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>
            JANDI 연동 준비 중
          </div>
          <p className="text-sm leading-relaxed text-slate-500">
            사내 메신저(JANDI)도 영업 메시지 소스로 연동하려 했으나, JANDI가 공식 메시지
            수신 API를 제공하지 않아 대체 수집 방식을 검토하고 있습니다.
          </p>
          <p className="mt-4 inline-flex items-center gap-1.5 text-xs font-medium text-blue-500">
            <Wind className="h-3.5 w-3.5" /> 현재는 Gmail 소스가 정상적으로 동작합니다.
          </p>
        </div>
      </div>
    </div>
  );
}
