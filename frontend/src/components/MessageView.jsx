import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { createAnalysis, createGroupAnalysis, generateReplyDraft, getAnalysesBySource, getAnalysisById, updateAnalysis } from '../api/analyses';
import { getApiErrorMessage } from '../api/errors';
import { getSalesmapRecordsByAnalysis, registerSalesmapRecord } from '../api/salesmapRecords';
import { deleteSource, getSourceById, getSources } from '../api/sources';
import { collectGmailMessages } from '../api/integrations';

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
      return '승인 대기';
    case 'APPROVED':
      return '등록됨';
    case 'DELETED':
      return '삭제됨';
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

function getDisplayActionType(analysis) {
  if (analysis?.businessType === 'NON_BUSINESS') {
    return 'UNKNOWN';
  }

  return analysis?.actionType || 'UNKNOWN';
}

function getDisplayAnalysisValue(analysis, field) {
  if (analysis?.businessType === 'NON_BUSINESS') {
    return EMPTY_TEXT;
  }

  return analysis?.[field];
}

function getDisplayAmount(analysis) {
  if (analysis?.businessType === 'NON_BUSINESS') {
    return EMPTY_TEXT;
  }

  return formatMoney(analysis?.amount);
}

function formatBusinessType(type) {
  switch (type) {
    case 'SALES_ACTIVITY':
      return '영업 메일';
    case 'NON_BUSINESS':
      return '업무 외 메일';
    case 'UNKNOWN':
      return '확인 필요';
    default:
      return '확인 필요';
  }
}

function getBusinessTypeClass(type) {
  switch (type) {
    case 'SALES_ACTIVITY':
      return 'bg-emerald-100 text-emerald-700 border-emerald-200';
    case 'NON_BUSINESS':
      return 'bg-gray-100 text-gray-600 border-gray-200';
    default:
      return 'bg-yellow-100 text-yellow-700 border-yellow-200';
  }
}

function formatBusinessScore(score) {
  if (score === null || score === undefined || score === '') {
    return EMPTY_TEXT;
  }

  return `${Math.round(Number(score) * 100)}%`;
}

function getAnalysisStatusClass(status) {
  switch (status) {
    case 'ANALYZED':
      return 'bg-yellow-100 text-yellow-700 border-yellow-200';
    case 'APPROVED':
      return 'bg-green-100 text-green-700 border-green-200';
    case 'DELETED':
      return 'bg-gray-200 text-gray-700 border-gray-300';
    case 'FAILED':
      return 'bg-red-100 text-red-700 border-red-200';
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

function formatRegisterButtonLabel(actionType) {
  switch (actionType) {
    case 'CANCEL':
      return '등록된 일정 삭제';
    case 'UPDATE':
      return '등록된 일정 변경';
    case 'CREATE':
      return '일정 등록';
    default:
      return 'Salesmap 등록';
  }
}

function formatRegisteringLabel(actionType) {
  switch (actionType) {
    case 'CANCEL':
      return '삭제 중...';
    case 'UPDATE':
      return '변경 중...';
    case 'CREATE':
      return '등록 중...';
    default:
      return '처리 중...';
  }
}

function formatRegisterSuccessMessage(actionType) {
  switch (actionType) {
    case 'CANCEL':
      return '등록된 일정이 삭제되었습니다.';
    case 'UPDATE':
      return '등록된 일정이 변경되었습니다.';
    case 'CREATE':
      return '일정 등록이 완료되었습니다.';
    default:
      return 'Salesmap 반영이 완료되었습니다.';
  }
}

function formatSalesmapResultTitle(actionType) {
  switch (actionType) {
    case 'CANCEL':
      return '일정 삭제 결과';
    case 'UPDATE':
      return '일정 변경 결과';
    case 'CREATE':
      return '일정 등록 결과';
    default:
      return 'Salesmap 반영 결과';
  }
}

function formatScheduleConflictType(type) {
  switch (type) {
    case 'DUPLICATE_SCHEDULE':
      return '이미 같은 일정이 등록되어 있습니다.';
    case 'SAME_TIME_SCHEDULE':
      return '같은 시간대에 다른 일정이 있습니다.';
    case 'NEARBY_SCHEDULE':
      return '등록하려는 일정 전후 3시간 이내에 다른 일정이 있습니다.';
    default:
      return '일정 충돌이 감지되었습니다.';
  }
}

function formatConflictDateTime(value) {
  if (!value) {
    return EMPTY_TEXT;
  }

  return String(value).replace('T', ' ');
}

function formatScheduleConflictMessage(conflict) {
  if (!conflict) {
    return '등록 전 일정 충돌을 확인해야 합니다.';
  }

  const candidate = conflict.newSchedule;
  const conflictItems = Array.isArray(conflict.conflicts) ? conflict.conflicts : [];
  const candidateText = candidate
    ? `등록 예정: ${formatEmpty(candidate.title)} / ${formatConflictDateTime(candidate.scheduleDateTime)}`
    : '';
  const existingText = conflictItems
    .slice(0, 3)
    .map((item) => `기존 일정: ${formatEmpty(item.title)} / ${formatConflictDateTime(item.scheduleDateTime)}`)
    .join('\n');

  return [formatScheduleConflictType(conflict.type), candidateText, existingText]
    .filter(Boolean)
    .join('\n');
}

function getSalesmapResultClass(actionType) {
  switch (actionType) {
    case 'CANCEL':
      return 'border-red-100 bg-red-50';
    case 'UPDATE':
      return 'border-amber-100 bg-amber-50';
    case 'CREATE':
      return 'border-green-100 bg-green-50';
    default:
      return 'border-gray-200 bg-gray-50';
  }
}

function formatMoney(value) {
  if (value === null || value === undefined || value === '' || value === '-') {
    return EMPTY_TEXT;
  }

  return `${Number(value).toLocaleString('ko-KR')}원`;
}
function latestAnalysisOnly(analyses) {
  if (!Array.isArray(analyses) || analyses.length === 0) {
    return [];
  }

  const latest = [...analyses].sort((left, right) => {
    const leftId = Number(left.analysisId || 0);
    const rightId = Number(right.analysisId || 0);
    return rightId - leftId;
  })[0];

  return latest ? [latest] : [];
}

function toAnalysisForm(analysis) {
  return {
    summary: analysis.summary || '',
    followUpAction: analysis.followUpAction || '',
    scheduleText: analysis.scheduleText || '',
    attendees: analysis.attendees || '',
    customerName: analysis.customerName || '',
    contactName: analysis.contactName || '',
    productName: analysis.productName || '',
    amount: analysis.amount ?? '',
    actionType: analysis.actionType || 'UNKNOWN',
    actionReason: analysis.actionReason || '',
    targetScheduleTitle: analysis.targetScheduleTitle || '',
  };
}

function shouldHideSourceFromList(source) {
  const title = source.title || '';

  return (
    title.includes('David Shin님이 새로운 릴스를 추가했습니다')
    || title.includes("추천: '26년 5월' 이경준님의 상태 업데이트")
  );
}

export function MessageView() {
  const isDev = import.meta.env.DEV;
  const { source } = useParams();
  const navigate = useNavigate();
  const [backendSources, setBackendSources] = useState([]);
  const [sourceListMessage, setSourceListMessage] = useState('메일 조회 중');
  const [isSyncingGmail, setIsSyncingGmail] = useState(false);
  const [selectedSourceDetail, setSelectedSourceDetail] = useState(null);
  const [sourceAnalyses, setSourceAnalyses] = useState([]);
  const [sourceDetailMessage, setSourceDetailMessage] = useState('');
  const [analysisActionMessage, setAnalysisActionMessage] = useState('');
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isBulkAnalyzing, setIsBulkAnalyzing] = useState(false);
  const [isDeletingSource, setIsDeletingSource] = useState(false);
  const [salesmapRecordsByAnalysisId, setSalesmapRecordsByAnalysisId] = useState({});
  const [salesmapActionByAnalysisId, setSalesmapActionByAnalysisId] = useState({});
  const [registeringAnalysisId, setRegisteringAnalysisId] = useState(null);
  const sourceItemRefs = useRef({});

  const selectedSourceId = source?.startsWith('source-')
    ? Number(source.replace('source-', ''))
    : null;
  const senderHistorySources = selectedSourceDetail?.senderEmail
    ? backendSources
        .filter((item) => (
          item.senderEmail === selectedSourceDetail.senderEmail
          && item.sourceId !== selectedSourceDetail.sourceId
        ))
        .slice(0, 5)
    : [];

  const formatDetailedError = (error) => {
    const backendData = error.response?.data?.data;
    const detail = backendData ? ` / ${JSON.stringify(backendData)}` : '';
    return `${getApiErrorMessage(error)}${detail}`;
  };

  const fetchSources = useCallback(async () => {
    try {
      const sources = await getSources();
      const visibleSources = sources.filter((item) => !shouldHideSourceFromList(item));
      setBackendSources(visibleSources);
      setSourceListMessage(`${visibleSources.length}건 확인됨`);
      return visibleSources;
    } catch (error) {
      console.error('Failed to fetch sources:', {
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        responseBody: error.response?.data,
        rawError: error,
      });
      setSourceListMessage(getApiErrorMessage(error));
      return [];
    }
  }, []);

  useEffect(() => {
    fetchSources();
  }, [fetchSources]);

  useEffect(() => {
    if (!selectedSourceId || backendSources.length === 0) {
      return;
    }

    const selectedItem = sourceItemRefs.current[selectedSourceId];
    if (selectedItem) {
      selectedItem.scrollIntoView({
        block: 'center',
        behavior: 'smooth',
      });
    }
  }, [backendSources, selectedSourceId]);

  const handleSyncGmail = async () => {
    try {
      setIsSyncingGmail(true);
      setSourceListMessage('Gmail 동기화 중...');

      const result = await collectGmailMessages({
        mode: 'manual',
        recentDays: 30,
        debug: true,
      });

      console.info('Gmail manual sync result:', result);
      await fetchSources();

      const savedCount = result?.savedCount ?? 0;
      const skippedCount = result?.skippedDuplicateCount ?? 0;
      setSourceListMessage(`Gmail 동기화 완료: 새 메일 ${savedCount}건, 중복 ${skippedCount}건`);
    } catch (error) {
      console.error('Failed to sync Gmail messages:', {
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        responseBody: error.response?.data,
        rawError: error,
      });
      setSourceListMessage(`Gmail 동기화 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setIsSyncingGmail(false);
    }
  };

  const handleAnalyzeCollectedSources = async () => {
    if (backendSources.length === 0) {
      setSourceListMessage('분석할 수집 메일이 없습니다.');
      return;
    }

    const uniqueTargets = [];
    const seenKeys = new Set();

    backendSources.slice(0, 30).forEach((item) => {
      const key = item.sourceGroupId ? `group-${item.sourceGroupId}` : `source-${item.sourceId}`;
      if (!seenKeys.has(key)) {
        seenKeys.add(key);
        uniqueTargets.push(item);
      }
    });

    let analyzedCount = 0;
    let skippedCount = 0;
    let failedCount = 0;

    try {
      setIsBulkAnalyzing(true);
      setSourceListMessage(`수집 메일 AI 분석 준비 중... 0/${uniqueTargets.length}`);

      for (let index = 0; index < uniqueTargets.length; index += 1) {
        const item = uniqueTargets[index];
        setSourceListMessage(`수집 메일 AI 분석 중... ${index + 1}/${uniqueTargets.length}`);

        try {
          const existingAnalyses = await getAnalysesBySource(item.sourceId);
          if (Array.isArray(existingAnalyses) && existingAnalyses.length > 0) {
            skippedCount += 1;
            continue;
          }

          if (item.sourceGroupId) {
            await createGroupAnalysis({ sourceGroupId: item.sourceGroupId, analysisMode: 'rule' });
          } else {
            await createAnalysis({ sourceId: item.sourceId, analysisMode: 'rule' });
          }

          analyzedCount += 1;
        } catch (error) {
          failedCount += 1;
          console.error('Failed to analyze collected source:', {
            sourceId: item.sourceId,
            sourceGroupId: item.sourceGroupId,
            status: error.response?.status,
            message: error.response?.data?.message,
            data: error.response?.data?.data,
            responseBody: error.response?.data,
            rawError: error,
          });
        }
      }

      await fetchSources();

      if (selectedSourceId) {
        try {
          const analyses = await getAnalysesBySource(selectedSourceId);
          setSourceAnalyses(latestAnalysisOnly(analyses));
        } catch (error) {
          console.error('Failed to refresh selected source analyses after bulk analysis:', {
            sourceId: selectedSourceId,
            status: error.response?.status,
            message: error.response?.data?.message,
            data: error.response?.data?.data,
            rawError: error,
          });
        }
      }

      setSourceListMessage(`AI 분석 완료: ${analyzedCount}건 분석, ${skippedCount}건 이미 분석됨, ${failedCount}건 실패`);
    } finally {
      setIsBulkAnalyzing(false);
    }
  };

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
        setSourceAnalyses(latestAnalysisOnly(analyses));
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

  const handleDeleteSource = async (sourceId = selectedSourceId) => {
    if (!sourceId) {
      return;
    }

    const confirmed = window.confirm('선택한 수집 메일을 목록에서 삭제할까요?');
    if (!confirmed) {
      return;
    }

    try {
      setIsDeletingSource(true);
      await deleteSource(sourceId);
      setBackendSources((prev) => prev.filter((item) => item.sourceId !== sourceId));
      setSelectedSourceDetail(null);
      setSourceAnalyses([]);
      setSalesmapRecordsByAnalysisId({});
      setSalesmapActionByAnalysisId({});
      setAnalysisActionMessage('');
      setSourceDetailMessage('');
      await fetchSources();
      setSourceListMessage('수집된 메일이 삭제되었습니다.');
      navigate('/messages/gmail');
    } catch (error) {
      console.error('Failed to delete source:', {
        sourceId,
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        responseBody: error.response?.data,
        rawError: error,
      });
      setAnalysisActionMessage(`메일 삭제 실패: ${formatDetailedError(error)}`);
    } finally {
      setIsDeletingSource(false);
    }
  };

  const handleCreateAnalysis = async () => {
    if (!selectedSourceId || !selectedSourceDetail) {
      return;
    }

    const isGroupAnalysis = Boolean(selectedSourceDetail.sourceGroupId);
    const payload = isGroupAnalysis
      ? { sourceGroupId: selectedSourceDetail.sourceGroupId, analysisMode: 'rule' }
      : { sourceId: selectedSourceId, analysisMode: 'rule' };

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

      setSourceAnalyses([createdAnalysis]);
      setSalesmapRecordsByAnalysisId({});
      setSalesmapActionByAnalysisId({});
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
    const currentAnalysis = sourceAnalyses.find((analysis) => analysis.analysisId === analysisId);
    const actionType = currentAnalysis?.actionType;

    const completeRegistration = async (force = false) => {
      await registerSalesmapRecord({ analysisId, force });
      const records = await getSalesmapRecordsByAnalysis(analysisId);
      const updatedAnalysis = await getAnalysisById(analysisId);

      setSourceAnalyses([updatedAnalysis]);
      setSalesmapRecordsByAnalysisId((prev) => ({ ...prev, [analysisId]: records }));
      setSalesmapActionByAnalysisId((prev) => ({
        ...prev,
        [analysisId]: formatRegisterSuccessMessage(actionType),
      }));
    };

    try {
      setRegisteringAnalysisId(analysisId);
      setSalesmapActionByAnalysisId((prev) => ({ ...prev, [analysisId]: '' }));

      await completeRegistration(false);
    } catch (error) {
      console.error('Failed to register salesmap record:', {
        analysisId,
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        responseBody: error.response?.data,
        rawError: error,
      });

      if (error.response?.status === 409) {
        const conflict = error.response?.data?.data;
        const conflictMessage = formatScheduleConflictMessage(conflict);

        if (conflict?.type !== 'DUPLICATE_SCHEDULE') {
          const confirmed = window.confirm(`${conflictMessage}\n\n그래도 등록할까요?`);
          if (confirmed) {
            try {
              await completeRegistration(true);
              return;
            } catch (retryError) {
              console.error('Failed to force register salesmap record:', {
                analysisId,
                status: retryError.response?.status,
                message: retryError.response?.data?.message,
                data: retryError.response?.data?.data,
                responseBody: retryError.response?.data,
                rawError: retryError,
              });
              setSalesmapActionByAnalysisId((prev) => ({
                ...prev,
                [analysisId]: `${formatRegisterButtonLabel(actionType)} 실패: ${formatDetailedError(retryError)}`,
              }));
              return;
            }
          }

          setSalesmapActionByAnalysisId((prev) => ({
            ...prev,
            [analysisId]: '일정 충돌 확인 후 등록을 취소했습니다.',
          }));
          return;
        }

        setSalesmapActionByAnalysisId((prev) => ({
          ...prev,
          [analysisId]: `${formatRegisterButtonLabel(actionType)} 실패: ${conflictMessage}`,
        }));
        return;
      }

      setSalesmapActionByAnalysisId((prev) => ({
        ...prev,
        [analysisId]: `${formatRegisterButtonLabel(actionType)} 실패: ${formatDetailedError(error)}`,
      }));
    } finally {
      setRegisteringAnalysisId(null);
    }
  };

  const handleUpdateAnalysis = async (analysisId, payload) => {
    try {
      const updatedAnalysis = await updateAnalysis(analysisId, payload);
      setSourceAnalyses([updatedAnalysis]);
      setAnalysisActionMessage('수정한 AI 분석 결과가 저장되었습니다.');
    } catch (error) {
      console.error('Failed to update analysis:', {
        analysisId,
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        responseBody: error.response?.data,
        rawError: error,
      });
      setAnalysisActionMessage(`AI 분석 결과 수정 실패: ${formatDetailedError(error)}`);
      throw error;
    }
  };

  return (
    <div className="p-6 h-full w-full min-w-0 overflow-hidden grid grid-cols-1 lg:grid-cols-[minmax(0,3fr)_minmax(380px,2fr)] gap-5">
      <div className="min-w-0 space-y-4 overflow-auto">
        <div className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm text-gray-700">수집된 메일</h3>
            <div className="flex items-center gap-2">
              <span className="text-xs text-gray-500">{sourceListMessage}</span>
              <button
                onClick={handleSyncGmail}
                disabled={isSyncingGmail || isBulkAnalyzing}
                className="px-2.5 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:bg-gray-100 disabled:text-gray-400"
              >
                {isSyncingGmail ? '동기화 중...' : 'Gmail 새로고침'}
              </button>
              <button
                onClick={handleAnalyzeCollectedSources}
                disabled={isBulkAnalyzing || isSyncingGmail || backendSources.length === 0}
                className="px-2.5 py-1 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded"
              >
                {isBulkAnalyzing ? '분석 중...' : '수집 메일 AI 분석'}
              </button>
            </div>
          </div>

          {backendSources.length > 0 ? (
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {backendSources.map((item) => (
                <button
                  key={item.sourceId}
                  ref={(element) => {
                    if (element) {
                      sourceItemRefs.current[item.sourceId] = element;
                    }
                  }}
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

          {selectedSourceDetail?.senderEmail && senderHistorySources.length > 0 && (
            <div className="mt-4 pt-3 border-t border-gray-100">
              <h4 className="text-xs text-gray-600 mb-2">이 발신자의 최근 메일</h4>
              <div className="space-y-1.5">
                {senderHistorySources.map((item) => (
                  <button
                    key={item.sourceId}
                    onClick={() => handleSourceClick(item.sourceId)}
                    className="w-full text-left text-xs text-gray-500 hover:text-blue-600 truncate"
                    title={item.title}
                  >
                    {item.title}
                  </button>
                ))}
              </div>
            </div>
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

      <div className="w-full max-w-full bg-white border border-gray-200 rounded-lg shadow-sm flex flex-col min-w-0 overflow-hidden">
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
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-gray-500">{formatSourceStatus(selectedSourceDetail.status)}</span>
                    <button
                      onClick={() => handleDeleteSource(selectedSourceDetail.sourceId)}
                      disabled={isDeletingSource}
                      className="px-2 py-0.5 text-xs border border-red-200 text-red-600 rounded hover:bg-red-50 disabled:bg-gray-100 disabled:text-gray-400"
                    >
                      {isDeletingSource ? '삭제 중...' : '삭제'}
                    </button>
                  </div>
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
                        onSave={(payload) => handleUpdateAnalysis(analysis.analysisId, payload)}
                        onDeleteSource={() => handleDeleteSource(selectedSourceId)}
                        isDeletingSource={isDeletingSource}
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

function AnalysisCard({ analysis, records, actionMessage, isRegistering, onRegister, onSave, onDeleteSource, isDeletingSource, isDev }) {
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isDrafting, setIsDrafting] = useState(false);
  const [replyDraft, setReplyDraft] = useState(null);
  const [draftMessage, setDraftMessage] = useState('');
  const [form, setForm] = useState(() => toAnalysisForm(analysis));

  useEffect(() => {
    setForm(toAnalysisForm(analysis));
    setIsEditing(false);
    setReplyDraft(null);
    setDraftMessage('');
  }, [analysis]);

  const handleChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const handleSave = async () => {
    try {
      setIsSaving(true);
      await onSave({
        ...form,
        amount: form.amount === '' ? null : Number(form.amount),
      });
      setIsEditing(false);
    } finally {
      setIsSaving(false);
    }
  };

  const handleGenerateReplyDraft = async () => {
    try {
      setIsDrafting(true);
      setDraftMessage('');
      const draft = await generateReplyDraft(analysis.analysisId);
      setReplyDraft(draft);
      setDraftMessage('답장 초안이 생성되었습니다.');
    } catch (error) {
      console.error('Failed to generate reply draft:', error);
      setDraftMessage(`답장 초안 생성 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setIsDrafting(false);
    }
  };

  const handleCopyReplyDraft = async () => {
    if (!replyDraft) {
      return;
    }

    const text = `${replyDraft.subject}\n\n${replyDraft.body}`;
    try {
      await navigator.clipboard.writeText(text);
      setDraftMessage('답장 초안이 복사되었습니다.');
    } catch (error) {
      console.error('Failed to copy reply draft:', error);
      setDraftMessage('복사에 실패했습니다. 초안 내용을 직접 선택해 복사해주세요.');
    }
  };

  return (
    <div className="bg-white border border-blue-100 rounded p-3 min-w-0">
      <div className="flex items-center justify-between mb-3 gap-2">
        <span className="text-xs text-gray-500">분석 번호 #{analysis.analysisId}</span>
        <div className="flex items-center gap-2">
          <span className={`border rounded-full px-2 py-0.5 text-xs ${getBusinessTypeClass(analysis.businessType)}`}>
            {formatBusinessType(analysis.businessType)}
          </span>
          <span className={`border rounded-full px-2 py-0.5 text-xs ${getActionTypeClass(getDisplayActionType(analysis))}`}>
            {formatActionType(getDisplayActionType(analysis))}
          </span>
          <span className={`border rounded-full px-2 py-0.5 text-xs ${getAnalysisStatusClass(analysis.status)}`}>
            {formatAnalysisStatus(analysis.status)}
          </span>
        </div>
      </div>

      {isEditing ? (
        <div className="space-y-2">
          <AnalysisEditField label="분석 요약" value={form.summary} onChange={(value) => handleChange('summary', value)} textarea />
          <AnalysisEditField label="다음 행동" value={form.followUpAction} onChange={(value) => handleChange('followUpAction', value)} />
          <AnalysisEditField label="일정 정보" value={form.scheduleText} onChange={(value) => handleChange('scheduleText', value)} />
          <AnalysisEditField label="참석자" value={form.attendees} onChange={(value) => handleChange('attendees', value)} />
          <AnalysisEditField label="고객사" value={form.customerName} onChange={(value) => handleChange('customerName', value)} />
          <AnalysisEditField label="제품" value={form.productName} onChange={(value) => handleChange('productName', value)} />
          <AnalysisEditField label="금액" value={form.amount} onChange={(value) => handleChange('amount', value)} type="number" />
          <div className="mb-2 grid grid-cols-[88px_1fr] gap-2">
            <p className="text-xs text-gray-500">처리 유형</p>
            <select
              value={form.actionType}
              onChange={(event) => handleChange('actionType', event.target.value)}
              className="text-sm border border-gray-300 rounded px-2 py-1 outline-none focus:border-blue-500"
            >
              <option value="CREATE">일정 생성</option>
              <option value="UPDATE">일정 변경</option>
              <option value="CANCEL">일정 취소</option>
              <option value="CONFIRM">일정 확인</option>
              <option value="UNKNOWN">확인 필요</option>
            </select>
          </div>
          <AnalysisEditField label="판단 근거" value={form.actionReason} onChange={(value) => handleChange('actionReason', value)} textarea />
          <AnalysisEditField label="대상 일정명" value={form.targetScheduleTitle} onChange={(value) => handleChange('targetScheduleTitle', value)} />
        </div>
      ) : (
        <>
          <AnalysisField label="분석 요약" value={getDisplayAnalysisValue(analysis, 'summary')} strong />
          <AnalysisField label="다음 행동" value={getDisplayAnalysisValue(analysis, 'followUpAction')} />
          <AnalysisField label="일정 정보" value={getDisplayAnalysisValue(analysis, 'scheduleText')} />
          <AnalysisField label="참석자" value={getDisplayAnalysisValue(analysis, 'attendees')} />
          <AnalysisField label="고객사" value={getDisplayAnalysisValue(analysis, 'customerName')} />
          <AnalysisField label="제품" value={getDisplayAnalysisValue(analysis, 'productName')} />
          <AnalysisField label="금액" value={getDisplayAmount(analysis)} alreadyFormatted />
          <AnalysisField label="메일 분류" value={`${formatBusinessType(analysis.businessType)} (${formatBusinessScore(analysis.businessRelevanceScore)})`} alreadyFormatted />
          <AnalysisField label="분류 근거" value={analysis.businessReason} />
          <AnalysisField label="처리 유형" value={formatActionType(getDisplayActionType(analysis))} alreadyFormatted />
          <AnalysisField label="판단 근거" value={getDisplayAnalysisValue(analysis, 'actionReason')} />
          {isDev && <AnalysisField label="대상 일정 ID" value={analysis.targetScheduleId} />}
          <AnalysisField label="대상 일정명" value={getDisplayAnalysisValue(analysis, 'targetScheduleTitle')} />
        </>
      )}

      <div className="mt-3 pt-3 border-t border-blue-50">
        {isEditing ? (
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={handleSave}
              disabled={isSaving}
              className="px-3 py-1.5 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded"
            >
              {isSaving ? '저장 중...' : '수정 저장'}
            </button>
            <button
              onClick={() => {
                setForm(toAnalysisForm(analysis));
                setIsEditing(false);
              }}
              disabled={isSaving}
              className="px-3 py-1.5 text-xs border border-gray-300 text-gray-700 rounded hover:bg-gray-50"
            >
              취소
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => setIsEditing(true)}
              disabled={analysis.status === 'APPROVED' || analysis.status === 'DELETED'}
              className="px-3 py-1.5 text-xs border border-gray-300 text-gray-700 rounded hover:bg-gray-50 disabled:bg-gray-100 disabled:text-gray-400"
            >
              수정
            </button>
            {analysis.businessType === 'NON_BUSINESS' ? (
              <button
                onClick={onDeleteSource}
                disabled={isDeletingSource}
                className="px-3 py-1.5 text-xs bg-red-500 hover:bg-red-600 disabled:bg-red-300 text-white rounded"
              >
                {isDeletingSource ? '삭제 중...' : '메일 삭제'}
              </button>
            ) : (
              <button
                onClick={onRegister}
                disabled={isRegistering || analysis.status === 'APPROVED' || analysis.status === 'DELETED'}
                className="px-3 py-1.5 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded"
              >
                {isRegistering ? formatRegisteringLabel(analysis.actionType) : formatRegisterButtonLabel(analysis.actionType)}
              </button>
            )}
          </div>
        )}

        {!isEditing && analysis.businessType !== 'NON_BUSINESS' && (
          <div className="mt-2">
            <button
              onClick={handleGenerateReplyDraft}
              disabled={isDrafting}
              className="w-full px-3 py-1.5 text-xs border border-blue-200 text-blue-700 rounded hover:bg-blue-50 disabled:bg-gray-100 disabled:text-gray-400"
            >
              {isDrafting ? '답장 초안 생성 중...' : '답장 초안 생성'}
            </button>
          </div>
        )}

        {draftMessage && (
          <p className="text-xs text-blue-700 mt-2 text-left">{draftMessage}</p>
        )}

        {replyDraft && (
          <div className="mt-3 rounded-lg border border-sky-100 bg-sky-50 p-3 text-left">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="text-xs font-semibold text-sky-800">답장 초안</p>
                <p className="mt-1 text-sm font-medium text-gray-900 break-words">{replyDraft.subject}</p>
              </div>
              <button
                type="button"
                onClick={handleCopyReplyDraft}
                className="shrink-0 rounded border border-sky-200 bg-white px-2 py-1 text-xs text-sky-700 hover:bg-sky-50"
              >
                복사
              </button>
            </div>
            <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-6 text-gray-700">
              {replyDraft.body}
            </p>
          </div>
        )}

        {actionMessage && (
          <p className="text-xs text-gray-600 mt-2 text-left">{actionMessage}</p>
        )}

        {records.length > 0 && (
          <div className="mt-2 space-y-2">
            {records.map((record) => (
              <div key={record.salesmapRecordId} className={`rounded-lg border p-3 ${getSalesmapResultClass(analysis.actionType)}`}>
                <div className="flex items-center justify-between gap-2">
                  <div className="text-left">
                    <p className="text-xs font-semibold text-gray-900">{formatSalesmapResultTitle(analysis.actionType)}</p>
                    <p className="mt-0.5 text-[11px] text-gray-500">처리 번호 #{record.salesmapRecordId}</p>
                  </div>
                  <span className="rounded-full border border-white/70 bg-white px-2 py-0.5 text-xs font-medium text-gray-700">
                    {formatSalesmapStatus(record.status)}
                  </span>
                </div>
                <div className="mt-2 grid gap-1 text-left text-xs text-gray-700">
                  <p>
                    <span className="text-gray-500">연동 ID</span>
                    <span className="ml-2 font-medium break-words">{formatEmpty(record.externalRecordId)}</span>
                  </p>
                  <p>
                    <span className="text-gray-500">처리 시간</span>
                    <span className="ml-2 font-medium">{formatEmpty(record.registeredAt)}</span>
                  </p>
                </div>
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

function AnalysisEditField({ label, value, onChange, textarea = false, type = 'text' }) {
  return (
    <div className="mb-2 grid grid-cols-[88px_1fr] gap-2">
      <p className="text-xs text-gray-500 pt-1.5">{label}</p>
      {textarea ? (
        <textarea
          value={value}
          onChange={(event) => onChange(event.target.value)}
          rows={3}
          className="text-sm border border-gray-300 rounded px-2 py-1 outline-none focus:border-blue-500 resize-y"
        />
      ) : (
        <input
          type={type}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="text-sm border border-gray-300 rounded px-2 py-1 outline-none focus:border-blue-500"
        />
      )}
    </div>
  );
}
