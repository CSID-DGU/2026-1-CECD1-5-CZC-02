import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { RefreshCcw } from 'lucide-react';
import { getSources } from '../api/sources';
import { getAnalysesBySource } from '../api/analyses';
import { getSalesmapRecordsByAnalysis } from '../api/salesmapRecords';
import { getApiErrorMessage } from '../api/errors';

function formatEmpty(value) {
  return value === null || value === undefined || value === '' || value === '-' ? '해당 없음' : value;
}

function formatDateTime(value) {
  if (!value) {
    return '해당 없음';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatActionType(actionType) {
  const labels = {
    CREATE: '일정 생성',
    UPDATE: '일정 변경',
    CANCEL: '일정 취소',
    CONFIRM: '일정 확인',
    UNKNOWN: '확인 필요',
  };

  return labels[actionType] || '확인 필요';
}

function getActionClass(actionType) {
  const classes = {
    CREATE: 'bg-blue-50 text-blue-700 border-blue-200',
    UPDATE: 'bg-purple-50 text-purple-700 border-purple-200',
    CANCEL: 'bg-red-50 text-red-700 border-red-200',
    CONFIRM: 'bg-green-50 text-green-700 border-green-200',
    UNKNOWN: 'bg-gray-50 text-gray-700 border-gray-200',
  };

  return classes[actionType] || classes.UNKNOWN;
}

function formatProcessingStatus(item) {
  if (item.latestRecord?.status === 'REGISTERED') {
    if (item.actionType === 'CANCEL') {
      return '삭제됨';
    }
    return '등록됨';
  }

  if (item.analysisId) {
    return '승인 대기';
  }

  return '분석 전';
}

function getStatusClass(status) {
  const classes = {
    등록됨: 'bg-green-50 text-green-700 border-green-200',
    삭제됨: 'bg-red-50 text-red-700 border-red-200',
    '승인 대기': 'bg-yellow-50 text-yellow-700 border-yellow-200',
    '분석 전': 'bg-gray-50 text-gray-600 border-gray-200',
  };

  return classes[status] || classes['분석 전'];
}

function isPriorityItem(item) {
  return (
    item.analysis?.status === 'ANALYZED'
    && ['UPDATE', 'CANCEL'].includes(item.actionType)
  );
}

export function ProcessingHistoryPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const isPriorityFilter = new URLSearchParams(location.search).get('filter') === 'priority';
  const [items, setItems] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const loadHistory = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');

    try {
      const sources = await getSources({ size: 30 });
      const rows = await Promise.all(
        sources.map(async (source) => {
          try {
            const analyses = await getAnalysesBySource(source.sourceId);
            const latestAnalysis = Array.isArray(analyses) && analyses.length > 0
              ? [...analyses].sort((left, right) => (right.analysisId || 0) - (left.analysisId || 0))[0]
              : null;

            const records = latestAnalysis
              ? await getSalesmapRecordsByAnalysis(latestAnalysis.analysisId)
              : [];
            const latestRecord = Array.isArray(records) && records.length > 0
              ? [...records].sort((left, right) => (right.salesmapRecordId || 0) - (left.salesmapRecordId || 0))[0]
              : null;

            return {
              source,
              analysis: latestAnalysis,
              latestRecord,
              analysisId: latestAnalysis?.analysisId,
              actionType: latestAnalysis?.actionType || 'UNKNOWN',
              processedAt: latestRecord?.registeredAt || latestAnalysis?.updatedAt || latestAnalysis?.createdAt || source.updatedAt || source.createdAt,
            };
          } catch (error) {
            console.error('Failed to build history row:', { sourceId: source.sourceId, error });
            return {
              source,
              analysis: null,
              latestRecord: null,
              analysisId: null,
              actionType: 'UNKNOWN',
              processedAt: source.updatedAt || source.createdAt,
              rowError: getApiErrorMessage(error),
            };
          }
        })
      );

      setItems(rows);
    } catch (error) {
      console.error('Failed to load processing history:', error);
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  const counts = useMemo(() => {
    return items.reduce((acc, item) => {
      const status = formatProcessingStatus(item);
      acc[status] = (acc[status] || 0) + 1;
      return acc;
    }, {});
  }, [items]);
  const priorityItems = useMemo(() => items.filter(isPriorityItem), [items]);
  const visibleItems = isPriorityFilter ? priorityItems : items;

  return (
    <div className="h-full overflow-auto p-6 bg-[#F8F9FA]">
      <div className="max-w-6xl mx-auto space-y-5">
        <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-5">
          <div className="flex items-start justify-between gap-4">
            <div className="text-left">
              <h2
                className="text-2xl font-bold"
                style={{ color: '#0f172a', fontWeight: 800 }}
              >
                처리 이력
              </h2>
              <p
                className="text-sm font-medium mt-1"
                style={{ color: '#334155' }}
              >
                {isPriorityFilter
                  ? '일정 변경·취소처럼 먼저 확인해야 하는 메일만 모아 보여줍니다.'
                  : '수집된 메일이 AI 분석과 Salesmap 반영까지 어떤 상태인지 확인합니다.'}
              </p>
            </div>
            <button
              type="button"
              onClick={loadHistory}
              disabled={isLoading}
              className="inline-flex items-center gap-2 px-3 py-2 text-sm border border-gray-300 rounded hover:bg-gray-50 disabled:bg-gray-100"
            >
              <RefreshCcw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
              새로고침
            </button>
          </div>

          <div className="grid grid-cols-4 gap-3 mt-5">
            {['승인 대기', '등록됨', '삭제됨', '분석 전'].map((status) => (
              <div key={status} className="border border-gray-100 rounded-lg bg-gray-50 px-4 py-3 text-left">
                <p className="text-xs text-gray-500">{status}</p>
                <p className="text-xl font-semibold text-gray-900 mt-1">{counts[status] || 0}</p>
              </div>
            ))}
          </div>

          {isPriorityFilter && (
            <div className="mt-4 flex items-center justify-between rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-left">
              <div>
                <p className="text-sm font-semibold text-red-700">우선 확인 대상 {priorityItems.length}건</p>
                <p className="text-xs text-red-600 mt-0.5">승인 대기 중인 일정 변경·취소 요청입니다.</p>
              </div>
              <button
                type="button"
                onClick={() => navigate('/history')}
                className="text-xs font-medium text-red-700 hover:text-red-800"
              >
                전체 보기
              </button>
            </div>
          )}
        </div>

        {errorMessage && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg p-4 text-left">
            처리 이력을 불러오지 못했습니다: {errorMessage}
          </div>
        )}

        <div className="bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden">
          <div className="grid grid-cols-[1.6fr_0.8fr_0.8fr_1fr_0.9fr] gap-3 px-5 py-3 text-xs text-gray-500 border-b border-gray-100 bg-gray-50 text-left">
            <span>메일</span>
            <span>처리 유형</span>
            <span>상태</span>
            <span>고객사 / 제품</span>
            <span>처리 시간</span>
          </div>

          {isLoading ? (
            <div className="px-5 py-10 text-sm text-gray-500">처리 이력을 불러오는 중입니다.</div>
          ) : visibleItems.length === 0 ? (
            <div className="px-5 py-10 text-sm text-gray-500">
              {isPriorityFilter ? '우선 확인이 필요한 메일이 없습니다.' : '표시할 처리 이력이 없습니다.'}
            </div>
          ) : (
            <div className="divide-y divide-gray-100">
              {visibleItems.map((item) => {
                const status = formatProcessingStatus(item);
                const isPriority = isPriorityItem(item);
                return (
                  <button
                    type="button"
                    key={`${item.source.sourceId}-${item.analysisId || 'none'}`}
                    onClick={() => navigate(`/messages/source-${item.source.sourceId}`)}
                    className={`w-full grid grid-cols-[1.6fr_0.8fr_0.8fr_1fr_0.9fr] gap-3 px-5 py-4 text-left transition-colors ${
                      isPriority ? 'bg-red-50/60 hover:bg-red-50' : 'hover:bg-blue-50/40'
                    }`}
                  >
                    <div className="min-w-0">
                      <p className="text-sm text-gray-900 font-medium truncate">{formatEmpty(item.source.title)}</p>
                      <p className="text-xs text-gray-500 mt-1 truncate">
                        {formatEmpty(item.source.senderEmail)} → {formatEmpty(item.source.receiverEmails)}
                      </p>
                      {item.rowError && (
                        <p className="text-xs text-red-500 mt-1 truncate">{item.rowError}</p>
                      )}
                    </div>

                    <div>
                      {item.analysisId ? (
                        <span className={`inline-flex px-2.5 py-1 text-xs rounded-full border ${getActionClass(item.actionType)}`}>
                          {formatActionType(item.actionType)}
                        </span>
                      ) : (
                        <span className="text-xs text-gray-400">분석 전</span>
                      )}
                    </div>

                    <div>
                      <span className={`inline-flex px-2.5 py-1 text-xs rounded-full border ${getStatusClass(status)}`}>
                        {status}
                      </span>
                    </div>

                    <div className="min-w-0">
                      <p className="text-sm text-gray-700 truncate">{formatEmpty(item.analysis?.customerName)}</p>
                      <p className="text-xs text-gray-500 mt-1 truncate">{formatEmpty(item.analysis?.productName)}</p>
                    </div>

                    <div className="text-sm text-gray-600">
                      {formatDateTime(item.processedAt)}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
