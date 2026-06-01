import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createAnalysis, createGroupAnalysis, getAnalysesBySource } from '../api/analyses';
import { getApiErrorMessage } from '../api/errors';
import { getSalesmapRecordsByAnalysis, registerSalesmapRecord } from '../api/salesmapRecords';
import { getSourceById, getSources } from '../api/sources';

export function MessageView() {
  const isDev = import.meta.env.DEV;
  const { source } = useParams();
  const navigate = useNavigate();
  const [backendSources, setBackendSources] = useState([]);
  const [sourceListMessage, setSourceListMessage] = useState('Source 조회 중');
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
    const detail = backendData ? ` / data=${JSON.stringify(backendData)}` : '';
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
        setSourceDetailMessage('Source 상세 조회 중');
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
      setAnalysisActionMessage(`AI 분석 완료: ${isGroupAnalysis ? '그룹 분석' : '단일 Source 분석'} / analysisId=${createdAnalysis.analysisId}`);
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
      setAnalysisActionMessage(`AI 분석 테스트 실패: ${formatDetailedError(error)}`);
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
        [analysisId]: 'SALESMAP 등록 테스트 완료',
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
        [analysisId]: `SALESMAP 등록 테스트 실패: ${formatDetailedError(error)}`,
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
            <h3 className="text-sm text-gray-700">백엔드 Source 목록</h3>
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
                    <span className="text-xs text-gray-500 shrink-0">{item.sourceType}</span>
                  </div>
                  <p className="text-xs text-gray-500 mt-1 line-clamp-2 break-words">
                    {item.senderEmail ? `${item.senderEmail} - ` : ''}
                    {item.content}
                  </p>
                </button>
              ))}
            </div>
          ) : (
            <p className="text-xs text-gray-500">생성되거나 수집된 Source가 없습니다.</p>
          )}
        </div>

        {!selectedSourceId && (
          <div className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm">
            <h3 className="text-sm text-gray-700 mb-2">메시지 선택</h3>
            <p className="text-sm text-gray-500">
              Gmail 또는 Jandi 메뉴에서 백엔드 Source를 선택하면 오른쪽에서 상세와 분석 결과를 확인할 수 있습니다.
            </p>
          </div>
        )}
      </div>

      <div className="w-full lg:w-96 max-w-full bg-white border border-gray-200 rounded-lg shadow-sm flex flex-col min-w-0">
        <div className="border-b border-gray-200 px-5 py-4">
          <h3 className="text-sm text-gray-800">Source 상세 / AI 분석</h3>
        </div>

        <div className="flex-1 overflow-auto p-5 space-y-4">
          {sourceDetailMessage && (
            <p className="text-sm text-gray-500">{sourceDetailMessage}</p>
          )}

          {!selectedSourceId && (
            <div className="flex min-h-80 items-center justify-center text-gray-400 text-sm text-center">
              Source를 선택해 상세 정보와 분석 결과를 확인하세요.
            </div>
          )}

          {selectedSourceDetail && (
            <>
              <div className="bg-gray-50 border border-gray-200 rounded-md p-3 min-w-0">
                <div className="flex items-center justify-between mb-2 gap-2">
                  <span className="text-xs text-gray-500">{selectedSourceDetail.sourceType}</span>
                  <span className="text-xs text-gray-500">{selectedSourceDetail.status}</span>
                </div>
                <div className="mb-2 text-left text-xs text-gray-500">
                  sourceId: {selectedSourceDetail.sourceId}
                  {selectedSourceDetail.sourceGroupId ? ` / sourceGroupId: ${selectedSourceDetail.sourceGroupId}` : ''}
                </div>
                <h4 className="text-sm text-gray-800 text-left mb-2 break-words">
                  {selectedSourceDetail.title}
                </h4>
                <p className="text-xs text-gray-500 mb-2 text-left break-words">
                  {selectedSourceDetail.senderEmail || '-'} → {selectedSourceDetail.receiverEmails || '-'}
                </p>
                <p
                  className="text-sm text-gray-700 text-left max-h-80 overflow-y-auto"
                  style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', overflowWrap: 'anywhere' }}
                >
                  {selectedSourceDetail.content}
                </p>
              </div>

              <div className="bg-blue-50 border border-blue-200 rounded-md p-3 min-w-0">
                <div className="flex items-center justify-between gap-3 mb-2">
                  <h4 className="text-sm text-blue-700">Analysis 결과</h4>
                  {isDev && (
                    <button
                      onClick={handleCreateAnalysis}
                      disabled={isAnalyzing}
                      className="px-3 py-1 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded shrink-0"
                    >
                      {isAnalyzing ? '분석 중...' : 'AI 분석 테스트'}
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
                      <div key={analysis.analysisId} className="bg-white border border-blue-100 rounded p-3 min-w-0">
                        <div className="flex items-center justify-between mb-2">
                          <span className="text-xs text-gray-500">analysisId: {analysis.analysisId}</span>
                          <span className="text-xs text-gray-500">{analysis.status}</span>
                        </div>

                        <AnalysisField label="summary" value={analysis.summary} />
                        <AnalysisField label="nextAction" value={analysis.followUpAction} />
                        <AnalysisField label="scheduleInfo" value={analysis.scheduleText} />
                        <AnalysisField
                          label="customer / product / amount"
                          value={`${analysis.customerName || '-'} / ${analysis.productName || '-'} / ${analysis.amount ?? '-'}`}
                        />
                        <AnalysisField label="actionType" value={analysis.actionType} />
                        <AnalysisField label="actionReason" value={analysis.actionReason} />
                        <AnalysisField label="targetScheduleId" value={analysis.targetScheduleId ?? '-'} />
                        <AnalysisField label="targetScheduleTitle" value={analysis.targetScheduleTitle} />

                        <div className="mt-3 pt-3 border-t border-blue-50">
                          {isDev && (
                            <button
                              onClick={() => handleRegisterSalesmap(analysis.analysisId)}
                              disabled={registeringAnalysisId === analysis.analysisId}
                              className="w-full px-3 py-1.5 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded"
                            >
                              {registeringAnalysisId === analysis.analysisId ? '등록 중...' : 'SALESMAP 등록 테스트'}
                            </button>
                          )}

                          {salesmapActionByAnalysisId[analysis.analysisId] && (
                            <p className="text-xs text-gray-600 mt-2 text-left">
                              {salesmapActionByAnalysisId[analysis.analysisId]}
                            </p>
                          )}

                          {salesmapRecordsByAnalysisId[analysis.analysisId]?.length > 0 && (
                            <div className="mt-2 space-y-2">
                              {salesmapRecordsByAnalysisId[analysis.analysisId].map((record) => (
                                <div key={record.salesmapRecordId} className="bg-gray-50 border border-gray-200 rounded p-2">
                                  <div className="flex items-center justify-between">
                                    <span className="text-xs text-gray-500">Record #{record.salesmapRecordId}</span>
                                    <span className="text-xs text-gray-700">{record.status}</span>
                                  </div>
                                  <p className="text-xs text-gray-700 mt-1 text-left break-words">
                                    externalRecordId: {record.externalRecordId || '-'}
                                  </p>
                                  <p className="text-xs text-gray-500 mt-1 text-left">
                                    registeredAt: {record.registeredAt || '-'}
                                  </p>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
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

function AnalysisField({ label, value }) {
  return (
    <div className="mb-2">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <p
        className="text-sm text-gray-700 text-left"
        style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', overflowWrap: 'anywhere' }}
      >
        {value || '-'}
      </p>
    </div>
  );
}
