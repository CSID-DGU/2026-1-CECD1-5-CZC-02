import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createAnalysis, createGroupAnalysis, getAnalysesBySource } from '../api/analyses';
import { getApiErrorMessage } from '../api/errors';
import { getSalesmapRecordsByAnalysis, registerSalesmapRecord } from '../api/salesmapRecords';
import { getSourceById, getSources } from '../api/sources';

const EMPTY_TEXT = '해당 없음';

function formatEmpty(value) {
  if (value === null || value === undefined || value === '' || value === '-') {
    return EMPTY_TEXT;
  }

  return value;
}

function formatSourceType(type) {
  switch (type) {
    case 'EMAIL':
      return 'Gmail';
    case 'JANDI_MESSAGE':
      return 'JANDI';
    case 'MEETING_NOTE':
      return '회의록';
    case 'MANUAL_INPUT':
      return '직접 입력';
    default:
      return formatEmpty(type);
  }
}

function formatSourceStatus(status) {
  switch (status) {
    case 'CREATED':
      return '수집 완료';
    case 'COLLECTED':
      return '수집 완료';
    case 'ANALYSIS_REQUESTED':
      return '분석 중';
    case 'ANALYZED':
      return '분석 완료';
    case 'PENDING':
      return '분석 대기';
    case 'FAILED':
      return '분석 실패';
    default:
      return formatEmpty(status);
  }
}

function formatAnalysisStatus(status) {
  switch (status) {
    case 'REQUESTED':
      return '분석 요청';
    case 'ANALYZING':
      return '분석 중';
    case 'ANALYZED':
      return '분석 완료';
    case 'APPROVED':
      return '승인 완료';
    case 'REJECTED':
      return '반려';
    case 'FAILED':
      return '분석 실패';
    default:
      return formatEmpty(status);
  }
}

function formatActionType(actionType) {
  switch (actionType) {
    case 'CREATE':
      return '일정 생성';
    case 'UPDATE':
      return '일정 변경';
    case 'CANCEL':
      return '일정 취소';
    case 'CONFIRM':
      return '일정 확인';
    case 'UNKNOWN':
      return '확인 필요';
    default:
      return '확인 필요';
  }
}

function getActionTypeClass(actionType) {
  switch (actionType) {
    case 'CREATE':
      return 'bg-blue-100 text-blue-700 border-blue-200';
    case 'UPDATE':
      return 'bg-amber-100 text-amber-700 border-amber-200';
    case 'CANCEL':
      return 'bg-red-100 text-red-700 border-red-200';
    case 'CONFIRM':
      return 'bg-green-100 text-green-700 border-green-200';
    default:
      return 'bg-gray-100 text-gray-700 border-gray-200';
  }
}

function formatSalesmapStatus(status) {
  switch (status) {
    case 'REGISTERED':
      return '등록 완료';
    case 'REQUESTED':
      return '등록 요청';
    case 'FAILED':
      return '등록 실패';
    case 'CANCELED':
      return '취소';
    default:
      return formatEmpty(status);
  }
}

function formatMoney(value) {
  if (value === null || value === undefined || value === '' || value === '-') {
    return EMPTY_TEXT;
  }

  return `${Number(value).toLocaleString('ko-KR')}원`;
}

export function MessageView() {
  const isDev = import.meta.env.DEV;
  const { source } = useParams();
  const navigate = useNavigate();
  const [backendSources, setBackendSources] = useState([]);
  const [sourceListMessage, setSourceListMessage] = useState('메일 조회 중');
  const [selectedSourceDetail, setSelectedSourceDetail] = useState(null);
  const [sourceAnalyses, setSourceAnalyses] = useState([]);
  const [sourceDetailMessage, setSourceDetailMessage] = useState('');
  const [analysisActionMessage, setAnalysisActionMessage] = useState('');
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [salesmapRecordsByAnalysisId, setSalesmapRecordsByAnalysisId] = useState({});
  const [salesmapActionByAnalysisId, setSalesmapActionByAnalysisId] = useState({});
  const [registeringAnalysisId, setRegisteringAnalysisId] = useState(null);

  const selectedSourceId = source?.startsWith('source-')
    ? Number(source.replace('source-', ''))
    : null;

  const formatDetailedError = (error) => {
    const backendData = error.response?.data?.data;
    const detail = backendData ? ` / ${JSON.stringify(backendData)}` : '';
    return `${getApiErrorMessage(error)}${detail}`;
  };

  useEffect(() => {
    const fetchSources = async () => {
      try {
        const sources = await getSources();
        setBackendSources(sources);
        setSourceListMessage(`${sources.length}건 확인됨`);
      } catch (error) {
        console.error('Failed to fetch sources:', {
          status: error.response?.status,
          message: error.response?.data?.message,
          data: error.response?.data?.data,
          responseBody: error.response?.data,
          rawError: error,
        });
        setSourceListMessage(getApiErrorMessage(error));
      }
    };

    fetchSources();
  }, []);

  useEffect(() => {
    if (!selectedSourceId) {
      setSelectedSourceDetail(null);
      setSourceAnalyses([]);
      setSourceDetailMessage('');
      return;
    }

    const fetchSourceDetail = async () => {
      try {
        setSourceDetailMessage('메일 상세 조회 중');
        const [sourceDetail, analyses] = await Promise.all([
          getSourceById(selectedSourceId),
          getAnalysesBySource(selectedSourceId),
        ]);

        setSelectedSourceDetail(sourceDetail);
        setSourceAnalyses(analyses);
        setSalesmapRecordsByAnalysisId({});
        setSalesmapActionByAnalysisId({});
        setAnalysisActionMessage('');
        setSourceDetailMessage('');
      } catch (error) {
        console.error('Failed to fetch source detail or analyses:', {
          sourceId: selectedSourceId,
          status: error.response?.status,
          message: error.response?.data?.message,
          data: error.response?.data?.data,
          responseBody: error.response?.data,
          rawError: error,
        });
        setSelectedSourceDetail(null);
        setSourceAnalyses([]);
        setSourceDetailMessage(getApiErrorMessage(error));
      }
    };

    fetchSourceDetail();
  }, [selectedSourceId]);

  const handleSourceClick = (sourceId) => {
    navigate(`/messages/source-${sourceId}`);
  };

  const handleCreateAnalysis = async () => {
    if (!selectedSourceId || !selectedSourceDetail) {
      return;
    }

    const isGroupAnalysis = Boolean(selectedSourceDetail.sourceGroupId);
    const payload = isGroupAnalysis
      ? { sourceGroupId: selectedSourceDetail.sourceGroupId }
      : { sourceId: selectedSourceId };

    try {
      setIsAnalyzing(true);
      setAnalysisActionMessage('');

      console.info('Creating analysis:', {
        endpoint: isGroupAnalysis ? 'POST /api/analysis/group' : 'POST /api/analysis',
        payload,
        selectedSourceId,
        sourceGroupId: selectedSourceDetail.sourceGroupId || null,
        externalGroupId: selectedSourceDetail.externalGroupId || null,
      });

      const createdAnalysis = isGroupAnalysis
        ? await createGroupAnalysis(payload)
        : await createAnalysis(payload);
      const analyses = await getAnalysesBySource(selectedSourceId);
      const nextAnalyses = analyses.some((analysis) => analysis.analysisId === createdAnalysis.analysisId)
        ? analyses
        : [createdAnalysis, ...analyses];

      setSourceAnalyses(nextAnalyses);
      setAnalysisActionMessage('AI 분석이 완료되었습니다.');
    } catch (error) {
      console.error('Failed to create analysis:', {
        sourceId: selectedSourceId,
        sourceGroupId: selectedSourceDetail.sourceGroupId || null,
        endpoint: selectedSourceDetail.sourceGroupId ? 'POST /api/analysis/group' : 'POST /api/analysis',
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        responseBody: error.response?.data,
        rawError: error,
      });
      setAnalysisActionMessage(`AI 분석 실패: ${formatDetailedError(error)}`);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleRegisterSalesmap = async (analysisId) => {
    try {
      setRegisteringAnalysisId(analysisId);
      setSalesmapActionByAnalysisId((prev) => ({ ...prev, [analysisId]: '' }));

      await registerSalesmapRecord({ analysisId });
      const records = await getSalesmapRecordsByAnalysis(analysisId);

      setSalesmapRecordsByAnalysisId((prev) => ({ ...prev, [analysisId]: records }));
      setSalesmapActionByAnalysisId((prev) => ({
        ...prev,
        [analysisId]: 'Salesmap 등록이 완료되었습니다.',
      }));
    } catch (error) {
      console.error('Failed to register salesmap record:', {
        analysisId,
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        responseBody: error.response?.data,
        rawError: error,
      });
      setSalesmapActionByAnalysisId((prev) => ({
        ...prev,
        [analysisId]: `Salesmap 등록 실패: ${formatDetailedError(error)}`,
      }));
    } finally {
      setRegisteringAnalysisId(null);
    }
  };

  return (
    <div className="p-6 h-full flex flex-col lg:flex-row gap-5">
      <div className="flex-1 min-w-0 space-y-4 overflow-auto">
        <div className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm text-gray-700">수집된 메일</h3>
            <span className="text-xs text-gray-500">{sourceListMessage}</span>
          </div>

          {backendSources.length > 0 ? (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {backendSources.map((item) => (
                <button
                  key={item.sourceId}
                  onClick={() => handleSourceClick(item.sourceId)}
                  className={`w-full text-left border rounded-md p-3 transition-colors ${
                    selectedSourceId === item.sourceId
                      ? 'border-blue-400 bg-blue-50'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm text-gray-800 truncate">{item.title}</p>
                    <span className="text-xs text-gray-500 shrink-0">{formatSourceType(item.sourceType)}</span>
                  </div>
                  <p className="text-xs text-gray-500 mt-1 line-clamp-2 break-words">
                    {item.senderEmail ? `${item.senderEmail} - ` : ''}
                    {item.content}
                  </p>
                </button>
              ))}
            </div>
          ) : (
            <p className="text-xs text-gray-500">아직 수집된 메일이 없습니다.</p>
          )}
        </div>

        {!selectedSourceId && (
          <div className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm">
            <h3 className="text-sm text-gray-700 mb-2">메일 선택</h3>
            <p className="text-sm text-gray-500">
              Gmail 또는 JANDI 메뉴에서 수집된 메일을 선택하면 오른쪽에서 상세 내용과 AI 분석 결과를 확인할 수 있습니다.
            </p>
          </div>
        )}
      </div>

      <div className="w-full lg:w-96 max-w-full bg-white border border-gray-200 rounded-lg shadow-sm flex flex-col min-w-0">
        <div className="border-b border-gray-200 px-5 py-4">
          <h3 className="text-sm text-gray-800">메일 상세 및 AI 분석</h3>
        </div>

        <div className="flex-1 overflow-auto p-5 space-y-4">
          {sourceDetailMessage && (
            <p className="text-sm text-gray-500">{sourceDetailMessage}</p>
          )}

          {!selectedSourceId && (
            <div className="flex min-h-80 items-center justify-center text-gray-400 text-sm text-center">
              메일을 선택하면 상세 정보와 분석 결과가 표시됩니다.
            </div>
          )}

          {selectedSourceDetail && (
            <>
              <div className="bg-gray-50 border border-gray-200 rounded-md p-3 min-w-0">
                <div className="flex items-center justify-between mb-2 gap-2">
                  <span className="text-xs text-gray-500">{formatSourceType(selectedSourceDetail.sourceType)}</span>
                  <span className="text-xs text-gray-500">{formatSourceStatus(selectedSourceDetail.status)}</span>
                </div>
                {isDev && (
                  <div className="mb-2 text-left text-xs text-gray-400">
                    메일 번호: {selectedSourceDetail.sourceId}
                    {selectedSourceDetail.sourceGroupId ? ` / 묶음: ${selectedSourceDetail.sourceGroupId}` : ''}
                  </div>
                )}
                <h4 className="text-sm text-gray-800 text-left mb-2 break-words">
                  {formatEmpty(selectedSourceDetail.title)}
                </h4>
                <p className="text-xs text-gray-500 mb-2 text-left break-words">
                  {formatEmpty(selectedSourceDetail.senderEmail)} → {formatEmpty(selectedSourceDetail.receiverEmails)}
                </p>
                <p
                  className="text-sm text-gray-700 text-left max-h-80 overflow-y-auto"
                  style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', overflowWrap: 'anywhere' }}
                >
                  {formatEmpty(selectedSourceDetail.content)}
                </p>
              </div>

              <div className="bg-blue-50 border border-blue-200 rounded-md p-3 min-w-0">
                <div className="flex items-center justify-between gap-3 mb-3">
                  <h4 className="text-sm text-blue-700">AI 분석 결과</h4>
                  {isDev && (
                    <button
                      onClick={handleCreateAnalysis}
                      disabled={isAnalyzing}
                      className="px-3 py-1 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded shrink-0"
                    >
                      {isAnalyzing ? '분석 중...' : 'AI 분석 실행'}
                    </button>
                  )}
                </div>

                {analysisActionMessage && (
                  <p
                    className="text-xs text-gray-700 mb-2 text-left"
                    style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', overflowWrap: 'anywhere' }}
                  >
                    {analysisActionMessage}
                  </p>
                )}

                {sourceAnalyses.length === 0 ? (
                  <p className="text-sm text-gray-600 text-left">아직 분석 결과가 없습니다.</p>
                ) : (
                  <div className="space-y-3">
                    {sourceAnalyses.map((analysis) => (
                      <AnalysisCard
                        key={analysis.analysisId}
                        analysis={analysis}
                        records={salesmapRecordsByAnalysisId[analysis.analysisId] || []}
                        actionMessage={salesmapActionByAnalysisId[analysis.analysisId]}
                        isRegistering={registeringAnalysisId === analysis.analysisId}
                        onRegister={() => handleRegisterSalesmap(analysis.analysisId)}
                        isDev={isDev}
                      />
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function AnalysisCard({ analysis, records, actionMessage, isRegistering, onRegister, isDev }) {
  return (
    <div className="bg-white border border-blue-100 rounded p-3 min-w-0">
      <div className="flex items-center justify-between mb-3 gap-2">
        <span className="text-xs text-gray-500">분석 번호 #{analysis.analysisId}</span>
        <div className="flex items-center gap-2">
          <span className={`border rounded-full px-2 py-0.5 text-xs ${getActionTypeClass(analysis.actionType)}`}>
            {formatActionType(analysis.actionType)}
          </span>
          <span className="text-xs text-gray-500">{formatAnalysisStatus(analysis.status)}</span>
        </div>
      </div>

      <AnalysisField label="분석 요약" value={analysis.summary} strong />
      <AnalysisField label="다음 행동" value={analysis.followUpAction} />
      <AnalysisField label="일정 정보" value={analysis.scheduleText} />
      <AnalysisField label="고객사" value={analysis.customerName} />
      <AnalysisField label="제품" value={analysis.productName} />
      <AnalysisField label="금액" value={formatMoney(analysis.amount)} alreadyFormatted />
      <AnalysisField label="처리 유형" value={formatActionType(analysis.actionType)} alreadyFormatted />
      <AnalysisField label="판단 근거" value={analysis.actionReason} />
      <AnalysisField label="대상 일정 ID" value={analysis.targetScheduleId} />
      <AnalysisField label="대상 일정명" value={analysis.targetScheduleTitle} />

      <div className="mt-3 pt-3 border-t border-blue-50">
        {isDev && (
          <button
            onClick={onRegister}
            disabled={isRegistering}
            className="w-full px-3 py-1.5 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded"
          >
            {isRegistering ? '등록 중...' : 'Salesmap 등록'}
          </button>
        )}

        {actionMessage && (
          <p className="text-xs text-gray-600 mt-2 text-left">{actionMessage}</p>
        )}

        {records.length > 0 && (
          <div className="mt-2 space-y-2">
            {records.map((record) => (
              <div key={record.salesmapRecordId} className="bg-gray-50 border border-gray-200 rounded p-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-gray-500">Salesmap 등록 번호 #{record.salesmapRecordId}</span>
                  <span className="text-xs text-gray-700">{formatSalesmapStatus(record.status)}</span>
                </div>
                <p className="text-xs text-gray-700 mt-1 text-left break-words">
                  Salesmap 등록 ID: {formatEmpty(record.externalRecordId)}
                </p>
                <p className="text-xs text-gray-500 mt-1 text-left">
                  등록 시간: {formatEmpty(record.registeredAt)}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function AnalysisField({ label, value, strong = false, alreadyFormatted = false }) {
  return (
    <div className="mb-2 grid grid-cols-[88px_1fr] gap-2">
      <p className="text-xs text-gray-500">{label}</p>
      <p
        className={`text-sm text-left ${strong ? 'font-medium text-gray-900' : 'text-gray-700'}`}
        style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', overflowWrap: 'anywhere' }}
      >
        {alreadyFormatted ? value : formatEmpty(value)}
      </p>
    </div>
  );
}
