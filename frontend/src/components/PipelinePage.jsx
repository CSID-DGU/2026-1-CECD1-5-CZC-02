import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Building2,
  RefreshCcw,
  Sparkles,
  ArrowRight,
  Clock,
  CircleDollarSign,
  ListChecks,
  TrendingUp,
} from 'lucide-react';
import { getPipeline } from '../api/customers';
import { getApiErrorMessage } from '../api/errors';

const STAGES = [
  { key: 'LEAD', label: '리드', hint: '문의·자료요청', dot: '#94a3b8', ring: 'ring-slate-200', head: 'from-slate-500/10 to-slate-500/0', text: 'text-slate-600' },
  { key: 'MEETING', label: '미팅 예정', hint: '일정 생성', dot: '#3b82f6', ring: 'ring-blue-200', head: 'from-blue-500/12 to-blue-500/0', text: 'text-blue-600' },
  { key: 'ADJUSTING', label: '일정 조율', hint: '변경 협의', dot: '#8b5cf6', ring: 'ring-violet-200', head: 'from-violet-500/12 to-violet-500/0', text: 'text-violet-600' },
  { key: 'CONFIRMED', label: '확정', hint: '진행 확정', dot: '#10b981', ring: 'ring-emerald-200', head: 'from-emerald-500/14 to-emerald-500/0', text: 'text-emerald-600' },
  { key: 'CANCELED', label: '보류·취소', hint: '재접촉 대상', dot: '#f43f5e', ring: 'ring-rose-200', head: 'from-rose-500/12 to-rose-500/0', text: 'text-rose-600' },
];

const STAGE_MAP = Object.fromEntries(STAGES.map((stage) => [stage.key, stage]));

function formatAmount(amount) {
  if (amount === null || amount === undefined) {
    return null;
  }
  if (amount >= 10000 && amount % 10000 === 0) {
    return `${(amount / 10000).toLocaleString('ko-KR')}만원`;
  }
  return `₩${amount.toLocaleString('ko-KR')}`;
}

function formatRelative(value) {
  if (!value) {
    return '';
  }
  const then = new Date(value);
  if (Number.isNaN(then.getTime())) {
    return '';
  }
  const diffMs = Date.now() - then.getTime();
  const diffMin = Math.round(diffMs / 60000);
  if (diffMin < 1) return '방금 전';
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHour = Math.round(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;
  const diffDay = Math.round(diffHour / 24);
  if (diffDay < 30) return `${diffDay}일 전`;
  return then.toLocaleDateString('ko-KR', { month: '2-digit', day: '2-digit' });
}

export function PipelinePage() {
  const navigate = useNavigate();
  const [deals, setDeals] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const loadPipeline = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      const result = await getPipeline();
      setDeals(Array.isArray(result) ? result : []);
    } catch (error) {
      console.error('Failed to load pipeline:', error);
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPipeline();
  }, [loadPipeline]);

  const grouped = useMemo(() => {
    const map = Object.fromEntries(STAGES.map((stage) => [stage.key, []]));
    deals.forEach((deal) => {
      if (map[deal.stage]) {
        map[deal.stage].push(deal);
      } else {
        map.LEAD.push(deal);
      }
    });
    return map;
  }, [deals]);

  const stats = useMemo(() => {
    const total = deals.length;
    const confirmed = deals.filter((d) => d.stage === 'CONFIRMED').length;
    const open = deals.filter((d) => ['LEAD', 'MEETING', 'ADJUSTING'].includes(d.stage)).length;
    const todo = deals.filter((d) => d.stage !== 'CANCELED').length;
    return { total, confirmed, open, todo };
  }, [deals]);

  const todoList = useMemo(() => {
    return deals
      .filter((deal) => deal.stage !== 'CANCELED' && deal.nextAction)
      .sort((a, b) => (b.stale === a.stale ? 0 : b.stale ? 1 : -1));
  }, [deals]);

  return (
    <div className="h-full overflow-auto bg-gradient-to-b from-[#F6F8FC] to-[#EEF2F9] p-6">
      <div className="max-w-[1400px] mx-auto space-y-5">
        {/* Header */}
        <div className="relative overflow-hidden rounded-2xl border border-white/60 bg-white/80 shadow-[0_10px_40px_-18px_rgba(37,99,235,0.35)] backdrop-blur p-6">
          <div className="absolute -right-16 -top-16 h-56 w-56 rounded-full bg-gradient-to-br from-blue-400/20 to-violet-400/10 blur-2xl" />
          <div className="relative flex flex-wrap items-start justify-between gap-4">
            <div className="text-left">
              <p className="inline-flex items-center gap-1.5 text-xs font-semibold text-blue-600">
                <Sparkles className="w-3.5 h-3.5" /> AI 딜 파이프라인
              </p>
              <h2
                className="mt-1 text-[26px] leading-tight tracking-tight text-slate-900"
                style={{ fontFamily: 'var(--display)', fontWeight: 700 }}
              >
                고객사 딜 진행 현황
              </h2>
              <p className="mt-2 max-w-2xl text-sm text-slate-500">
                수집된 메일의 AI 분석 결과를 고객사 기준으로 묶어, 각 딜이 지금 어느 단계에 있는지와 다음 할 일을 자동으로 정리합니다.
              </p>
            </div>
            <button
              type="button"
              onClick={loadPipeline}
              disabled={isLoading}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:border-blue-300 hover:text-blue-600 disabled:opacity-60"
            >
              <RefreshCcw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
              새로고침
            </button>
          </div>

          <div className="relative mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <StatChip icon={Building2} label="전체 딜" value={stats.total} tint="text-slate-700" />
            <StatChip icon={TrendingUp} label="진행 중" value={stats.open} tint="text-blue-600" />
            <StatChip icon={Sparkles} label="확정" value={stats.confirmed} tint="text-emerald-600" />
            <StatChip icon={ListChecks} label="다음 할 일" value={stats.todo} tint="text-violet-600" />
          </div>
        </div>

        {errorMessage && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-left text-sm text-rose-700">
            파이프라인을 불러오지 못했습니다. {errorMessage}
          </div>
        )}

        {!errorMessage && deals.length === 0 && !isLoading && (
          <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center text-sm text-slate-500">
            아직 분석된 딜이 없습니다. 메일을 수집하고 AI 분석을 실행하면 여기에 파이프라인이 채워집니다.
          </div>
        )}

        {/* 다음 할 일 */}
        {todoList.length > 0 && (
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-3 flex items-center gap-2 text-left">
              <ListChecks className="h-4 w-4 text-violet-500" />
              <h3 className="text-sm font-bold text-slate-800" style={{ fontFamily: 'var(--display)' }}>
                다음 할 일 추천
              </h3>
              <span className="rounded-full bg-violet-50 px-2 py-0.5 text-xs font-semibold text-violet-600">
                {todoList.length}건
              </span>
            </div>
            <div className="grid gap-2.5 sm:grid-cols-2">
              {todoList.slice(0, 6).map((deal) => {
                const stage = STAGE_MAP[deal.stage] || STAGES[0];
                return (
                  <div
                    key={`todo-${deal.customerContactId}`}
                    className="flex items-start gap-3 rounded-xl border border-slate-100 bg-gradient-to-br from-slate-50/80 to-white p-3.5 text-left"
                  >
                    <span
                      className="mt-1 h-2 w-2 shrink-0 rounded-full"
                      style={{ background: stage.dot }}
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <p className="truncate text-sm font-semibold text-slate-800">{deal.customerName}</p>
                        <span className={`shrink-0 text-[11px] font-medium ${stage.text}`}>{stage.label}</span>
                        {deal.stale && (
                          <span className="shrink-0 rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700">
                            응답 지연
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-xs leading-relaxed text-slate-600">{deal.nextAction}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Kanban */}
        {deals.length > 0 && (
          <div className="flex gap-4 overflow-x-auto pb-4">
            {STAGES.map((stage) => {
              const items = grouped[stage.key] || [];
              return (
                <div key={stage.key} className="flex w-[280px] shrink-0 flex-col">
                  <div className={`rounded-t-2xl border border-b-0 border-slate-200 bg-gradient-to-b ${stage.head} px-4 py-3`}>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="h-2.5 w-2.5 rounded-full" style={{ background: stage.dot }} />
                        <span className="text-sm font-bold text-slate-800" style={{ fontFamily: 'var(--display)' }}>
                          {stage.label}
                        </span>
                      </div>
                      <span className="rounded-full bg-white/70 px-2 py-0.5 text-xs font-semibold text-slate-500">
                        {items.length}
                      </span>
                    </div>
                    <p className="mt-0.5 text-left text-[11px] text-slate-400">{stage.hint}</p>
                  </div>

                  <div className="flex flex-1 flex-col gap-2.5 rounded-b-2xl border border-t-0 border-slate-200 bg-slate-50/60 p-2.5">
                    {items.length === 0 ? (
                      <div className="py-8 text-center text-xs text-slate-300">비어 있음</div>
                    ) : (
                      items.map((deal) => (
                        <DealCard
                          key={deal.customerContactId}
                          deal={deal}
                          stage={stage}
                          onOpen={() => navigate('/customers')}
                        />
                      ))
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function StatChip({ icon: Icon, label, value, tint }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-slate-100 bg-white/70 px-3.5 py-2.5">
      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-50">
        <Icon className={`h-4 w-4 ${tint}`} />
      </span>
      <div className="text-left">
        <p className="text-[11px] font-medium text-slate-400">{label}</p>
        <p className={`text-lg font-bold leading-none ${tint}`} style={{ fontFamily: 'var(--display)' }}>
          {value}
        </p>
      </div>
    </div>
  );
}

function DealCard({ deal, stage, onOpen }) {
  const amount = formatAmount(deal.amount);
  return (
    <button
      type="button"
      onClick={onOpen}
      className={`group relative w-full overflow-hidden rounded-xl border border-slate-200 bg-white p-3.5 text-left shadow-sm ring-1 ${stage.ring} transition hover:-translate-y-0.5 hover:shadow-md`}
    >
      <span className="absolute inset-x-0 top-0 h-1" style={{ background: stage.dot }} />
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>
            {deal.customerName || '고객사 미상'}
          </p>
          <p className="mt-0.5 truncate text-xs text-slate-500">
            {deal.contactName ? `${deal.contactName} 님` : deal.email}
          </p>
        </div>
        {deal.stale && (
          <span className="shrink-0 rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700">
            지연
          </span>
        )}
      </div>

      {deal.productName && (
        <p className="mt-2 truncate text-xs font-medium text-slate-600">📦 {deal.productName}</p>
      )}

      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        {amount && (
          <span className="inline-flex items-center gap-1 rounded-md bg-emerald-50 px-1.5 py-0.5 text-[11px] font-semibold text-emerald-700">
            <CircleDollarSign className="h-3 w-3" /> {amount}
          </span>
        )}
        <span className="inline-flex items-center gap-1 rounded-md bg-slate-50 px-1.5 py-0.5 text-[11px] text-slate-500">
          <Clock className="h-3 w-3" /> {formatRelative(deal.lastActivityAt)}
        </span>
        {deal.dealCount > 1 && (
          <span className="rounded-md bg-slate-50 px-1.5 py-0.5 text-[11px] text-slate-500">
            메일 {deal.dealCount}건
          </span>
        )}
      </div>

      {deal.nextAction && (
        <div className="mt-2.5 rounded-lg bg-gradient-to-br from-blue-50/80 to-violet-50/50 px-2.5 py-2">
          <p className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-blue-500">
            <ArrowRight className="h-3 w-3" /> 다음 할 일
          </p>
          <p className="mt-0.5 text-xs leading-relaxed text-slate-600">{deal.nextAction}</p>
        </div>
      )}
    </button>
  );
}
