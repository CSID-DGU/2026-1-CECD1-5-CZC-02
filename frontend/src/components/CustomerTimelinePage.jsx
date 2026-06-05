import { useCallback, useEffect, useMemo, useState } from 'react';
import { Building2, Mail, RefreshCcw, UserRound } from 'lucide-react';
import { getCustomers, getCustomerTimeline } from '../api/customers';
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
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatActivityType(type) {
  const labels = {
    AI_ANALYZED: 'AI 분석 완료',
    SCHEDULE_CREATED: '일정 생성',
    SCHEDULE_UPDATED: '일정 변경',
    SCHEDULE_CANCELED: '일정 삭제',
    SALESMAP_REGISTERED: 'Salesmap 반영',
  };

  return labels[type] || '활동 기록';
}

function getActivityClass(type) {
  const classes = {
    AI_ANALYZED: 'bg-blue-50 text-blue-700 border-blue-200',
    SCHEDULE_CREATED: 'bg-green-50 text-green-700 border-green-200',
    SCHEDULE_UPDATED: 'bg-purple-50 text-purple-700 border-purple-200',
    SCHEDULE_CANCELED: 'bg-red-50 text-red-700 border-red-200',
    SALESMAP_REGISTERED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  };

  return classes[type] || 'bg-gray-50 text-gray-700 border-gray-200';
}

function displayContactName(customer) {
  if (!customer) {
    return '해당 없음';
  }

  if (customer.customerName === 'GreenSoft') {
    return '박서준';
  }

  if (customer.customerName === 'Delta Systems') {
    return '최유진';
  }

  return formatEmpty(customer.contactName);
}

export function CustomerTimelinePage() {
  const [customers, setCustomers] = useState([]);
  const [selectedCustomerId, setSelectedCustomerId] = useState(null);
  const [timeline, setTimeline] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const selectedCustomer = useMemo(() => {
    return customers.find((customer) => customer.customerContactId === selectedCustomerId) || null;
  }, [customers, selectedCustomerId]);

  const loadCustomers = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');

    try {
      const result = await getCustomers();
      setCustomers(result);
      if (result.length > 0) {
        setSelectedCustomerId((currentId) => currentId || result[0].customerContactId);
      }
    } catch (error) {
      console.error('Failed to load customers:', error);
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCustomers();
  }, [loadCustomers]);

  useEffect(() => {
    if (!selectedCustomerId) {
      setTimeline(null);
      return;
    }

    let ignore = false;

    const loadTimeline = async () => {
      try {
        const result = await getCustomerTimeline(selectedCustomerId);
        if (!ignore) {
          setTimeline(result);
        }
      } catch (error) {
        console.error('Failed to load customer timeline:', error);
        if (!ignore) {
          setErrorMessage(getApiErrorMessage(error));
          setTimeline(null);
        }
      }
    };

    loadTimeline();

    return () => {
      ignore = true;
    };
  }, [selectedCustomerId]);

  return (
    <div className="h-full overflow-auto bg-[#F8F9FA] p-6">
      <div className="max-w-7xl mx-auto space-y-5">
        <div className="bg-white border border-gray-200 rounded-lg shadow-sm p-6">
          <div className="flex items-start justify-between gap-4">
            <div className="text-left">
              <p className="text-sm font-semibold text-blue-600">고객 활동 관리</p>
              <h2
                className="text-2xl mt-1 leading-tight"
                style={{ color: '#0f172a', fontWeight: 800, opacity: 1 }}
              >
                고객사별 타임라인
              </h2>
              <p className="text-sm text-gray-700 mt-2">
                수집된 메일, AI 분석 결과, 일정 반영, Salesmap 등록 이력을 고객사 기준으로 정리합니다.
              </p>
            </div>
            <button
              type="button"
              onClick={loadCustomers}
              disabled={isLoading}
              className="inline-flex items-center gap-2 px-3 py-2 text-sm border border-gray-300 rounded hover:bg-gray-50 disabled:bg-gray-100"
            >
              <RefreshCcw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
              새로고침
            </button>
          </div>
        </div>

        {errorMessage && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg p-4 text-left">
            고객 타임라인을 불러오지 못했습니다. {errorMessage}
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-[360px_minmax(0,1fr)] gap-5">
          <div className="bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100 text-left">
              <h3 className="text-sm font-semibold text-gray-900">고객사 목록</h3>
              <p className="text-xs text-gray-500 mt-1">{customers.length}개 고객사 확인됨</p>
            </div>

            <div className="max-h-[680px] overflow-auto divide-y divide-gray-100">
              {customers.length === 0 ? (
                <div className="px-5 py-10 text-sm text-gray-500 text-center">
                  아직 표시할 고객사가 없습니다.
                </div>
              ) : (
                customers.map((customer) => {
                  const selected = customer.customerContactId === selectedCustomerId;
                  return (
                    <button
                      type="button"
                      key={customer.customerContactId}
                      onClick={() => setSelectedCustomerId(customer.customerContactId)}
                      className={`w-full px-5 py-4 text-left transition-colors ${
                        selected ? 'bg-blue-50 border-l-2 border-blue-500' : 'hover:bg-gray-50 border-l-2 border-transparent'
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        <div className={`w-9 h-9 rounded-full flex items-center justify-center ${
                          selected ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600'
                        }`}>
                          <Building2 className="w-4 h-4" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-sm font-semibold text-gray-950 truncate">{formatEmpty(customer.customerName)}</p>
                          <p className="text-xs text-gray-500 truncate mt-1">{formatEmpty(customer.email)}</p>
                          <div className="flex items-center gap-2 mt-2 text-xs text-gray-500">
                            <span>{customer.activityCount}개 활동</span>
                            <span>·</span>
                            <span>{formatDateTime(customer.lastSeenAt)}</span>
                          </div>
                        </div>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>

          <div className="bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden">
            <div className="px-6 py-5 border-b border-gray-100 text-left">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="text-lg font-semibold text-gray-950">
                    {selectedCustomer ? formatEmpty(selectedCustomer.customerName) : '고객사를 선택하세요'}
                  </h3>
                  {selectedCustomer && (
                    <div className="flex flex-wrap items-center gap-3 mt-2 text-sm text-gray-600">
                      <span className="inline-flex items-center gap-1">
                        <Mail className="w-3.5 h-3.5" />
                        {formatEmpty(selectedCustomer.email)}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <UserRound className="w-3.5 h-3.5" />
                        담당자 {displayContactName(selectedCustomer)}
                      </span>
                    </div>
                  )}
                </div>
                {selectedCustomer && (
                  <span className="px-3 py-1 text-xs rounded-full border border-blue-200 bg-blue-50 text-blue-700">
                    {selectedCustomer.activityCount}개 활동
                  </span>
                )}
              </div>
            </div>

            <div className="p-6">
              {!selectedCustomer ? (
                <div className="py-20 text-center text-sm text-gray-500">
                  왼쪽에서 고객사를 선택하면 활동 타임라인이 표시됩니다.
                </div>
              ) : !timeline || timeline.activities.length === 0 ? (
                <div className="py-20 text-center text-sm text-gray-500">
                  아직 기록된 활동이 없습니다.
                </div>
              ) : (
                <div className="relative pl-6">
                  <div className="absolute left-2 top-2 bottom-2 w-px bg-gray-200" />
                  <div className="space-y-5">
                    {timeline.activities.map((activity) => (
                      <div key={activity.activityId} className="relative">
                        <div className="absolute -left-[23px] top-2 w-3 h-3 rounded-full bg-blue-500 ring-4 ring-blue-50" />
                        <div className="border border-gray-200 rounded-lg p-4 text-left bg-white">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <span className={`inline-flex px-2.5 py-1 text-xs rounded-full border ${getActivityClass(activity.activityType)}`}>
                                {formatActivityType(activity.activityType)}
                              </span>
                              <h4 className="text-sm font-semibold text-gray-950 mt-2 break-words">{formatEmpty(activity.title)}</h4>
                            </div>
                            <span className="text-xs text-gray-500 shrink-0">{formatDateTime(activity.occurredAt)}</span>
                          </div>
                          <p className="text-sm text-gray-600 whitespace-pre-wrap break-words mt-3">
                            {formatEmpty(activity.description)}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
