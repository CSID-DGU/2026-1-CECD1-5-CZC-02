import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Edit, Trash2, Send, ChevronRight } from 'lucide-react';
import { createAnalysis, getAnalysesBySource } from '../api/analyses';
import { getApiErrorMessage } from '../api/errors';
import { getSalesmapRecordsByAnalysis, registerSalesmapRecord } from '../api/salesmapRecords';
import { getSourceById, getSources } from '../api/sources';

export function MessageView() {
  const isDev = import.meta.env.DEV;
  const { source } = useParams();
  const navigate = useNavigate();
  const [selectedMessage, setSelectedMessage] = useState(null);
  const [statusFilter, setStatusFilter] = useState('pending');
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

  const [messages, setMessages] = useState([
    {
      id: '1',
      source: 'jandi',
      status: 'pending',
      sender: '박세일',
      subject: '다음 주 고객 미팅 일정 조율',
      preview: '안녕하세요, 다음 주 화요일 오후 2시에 ABC 기업과 미팅이 잡혔습니다...',
      date: '2026-05-04 10:30',
      aiAnalysis: 'Jandi 메시지를 분석한 결과, 팀원과의 일정 조율이 필요한 상황입니다. 다음 주 화요일 오후 2시 미팅을 캘린더에 등록하는 것을 추천합니다.',
      keyPoints: ['일시: 2026-05-13 14:00', '장소: ABC 기업 본사', '참석자: 박세일, 김영업']
    },
    {
      id: '2',
      source: 'jandi',
      status: 'pending',
      sender: '이마케',
      subject: '월간 영업 보고서 공유',
      preview: '지난 달 영업 실적 보고서를 공유드립니다. 전체적으로 목표 대비...',
      date: '2026-05-03 16:20',
      aiAnalysis: '월간 보고서 공유 메시지입니다. 특별한 일정 등록은 필요하지 않으나, 검토 후 피드백이 필요할 수 있습니다.',
      keyPoints: ['보고서 첨부됨', '목표 달성률: 95%', '검토 마감: 05/10']
    },
    {
      id: '3',
      source: 'gmail',
      status: 'pending',
      sender: 'sales@abccorp.com',
      subject: '제안서에 대한 문의',
      preview: '안녕하세요, 지난주에 보내주신 제안서를 검토했습니다. 몇 가지 질문이...',
      date: '2026-05-04 09:15',
      aiAnalysis: 'Gmail 이메일을 분석한 결과, 고객이 제안서에 대해 긍정적인 반응을 보이고 있습니다. 추가 미팅을 제안하는 것이 좋겠습니다.',
      keyPoints: ['발신: ABC Corp 구매팀', '관심도: 높음', '후속 조치: 미팅 제안']
    },
    {
      id: '4',
      source: 'gmail',
      status: 'completed',
      sender: 'contact@xyzcompany.com',
      subject: '계약서 최종 검토 완료',
      preview: '계약서 최종 검토가 완료되었습니다. 첨부된 서류를 확인해주세요...',
      date: '2026-05-02 14:45',
      aiAnalysis: '계약 최종 단계입니다. 서류 확인 후 서명 일정을 잡는 것을 추천합니다.',
      keyPoints: ['계약 금액: 5,000만원', '계약 기간: 1년', '서명 예정: 05/08']
    },
    {
      id: '5',
      source: 'jandi',
      status: 'pending',
      sender: '최영업',
      subject: '신규 고객 소개',
      preview: 'GHI 기업에서 우리 서비스에 관심을 보이고 있습니다. 소개 미팅을...',
      date: '2026-05-04 11:00',
      aiAnalysis: '신규 고객 소개 메시지입니다. 빠른 시일 내에 미팅 일정을 잡는 것이 좋습니다.',
      keyPoints: ['고객: GHI 기업', '예상 규모: 중형', '관심 분야: 프리미엄 패키지']
    },
    {
      id: '6',
      source: 'gmail',
      status: 'pending',
      sender: 'support@defgroup.com',
      subject: 'Re: 제품 데모 요청',
      preview: '제품 데모에 대한 일정을 확정하고 싶습니다. 이번 주 금요일은 어떠신가요...',
      date: '2026-05-03 13:30',
      aiAnalysis: '데모 일정 조율 이메일입니다. 금요일 일정을 확인하고 회신하는 것을 추천합니다.',
      keyPoints: ['제안 일시: 금요일 오전', '데모 대상: DEF 그룹 임원진', '준비물: 제품 데모 자료']
    }
  ]);

  const selectedSourceId = source?.startsWith('source-')
    ? Number(source.replace('source-', ''))
    : null;

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
          rawError: error
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
          getAnalysesBySource(selectedSourceId)
        ]);

        setSelectedSourceDetail(sourceDetail);
        setSourceAnalyses(analyses);
        setSalesmapRecordsByAnalysisId({});
        setSalesmapActionByAnalysisId({});
        setSourceDetailMessage('');
      } catch (error) {
        console.error('Failed to fetch source detail or analyses:', {
          sourceId: selectedSourceId,
          status: error.response?.status,
          message: error.response?.data?.message,
          data: error.response?.data?.data,
          rawError: error
        });
        setSelectedSourceDetail(null);
        setSourceAnalyses([]);
        setSourceDetailMessage(getApiErrorMessage(error));
      }
    };

    fetchSourceDetail();
  }, [selectedSourceId]);

  const handleAction = (messageId, action) => {
    if (action === 'approve') {
      setMessages(messages.map(msg =>
        msg.id === messageId ? { ...msg, status: 'completed' } : msg
      ));
      alert('승인되었습니다. 일정이 캘린더에 등록되었습니다.');
    } else if (action === 'delete') {
      setMessages(messages.map(msg =>
        msg.id === messageId ? { ...msg, status: 'deleted' } : msg
      ));
    } else if (action === 'edit') {
      alert('수정 기능은 개발 중입니다.');
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'pending':
        return <span className="px-2 py-0.5 text-xs bg-yellow-100 text-yellow-700 rounded">승인 대기</span>;
      case 'completed':
        return <span className="px-2 py-0.5 text-xs bg-blue-100 text-blue-700 rounded">완료</span>;
      default:
        return null;
    }
  };

  const currentSource = source || 'jandi';
  const filteredMessages = messages.filter(
    msg => msg.source === currentSource && msg.status === statusFilter
  );

  const handleSourceClick = (sourceId) => {
    setSelectedMessage(null);
    navigate(`/messages/source-${sourceId}`);
  };

  const handleCreateAnalysis = async () => {
    if (!selectedSourceId) {
      return;
    }

    try {
      setIsAnalyzing(true);
      setAnalysisActionMessage('');

      await createAnalysis({ sourceId: selectedSourceId });
      const analyses = await getAnalysesBySource(selectedSourceId);

      setSourceAnalyses(analyses);
      setAnalysisActionMessage('AI 분석 테스트 완료');
    } catch (error) {
      console.error('Failed to create analysis:', {
        sourceId: selectedSourceId,
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        rawError: error
      });
      setAnalysisActionMessage(`AI 분석 테스트 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleRegisterSalesmap = async (analysisId) => {
    try {
      setRegisteringAnalysisId(analysisId);
      setSalesmapActionByAnalysisId((prev) => ({
        ...prev,
        [analysisId]: ''
      }));

      await registerSalesmapRecord({ analysisId });
      const records = await getSalesmapRecordsByAnalysis(analysisId);

      setSalesmapRecordsByAnalysisId((prev) => ({
        ...prev,
        [analysisId]: records
      }));
      setSalesmapActionByAnalysisId((prev) => ({
        ...prev,
        [analysisId]: 'SALESMAP 등록 테스트 완료'
      }));
    } catch (error) {
      console.error('Failed to register salesmap record:', {
        analysisId,
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        rawError: error
      });
      setSalesmapActionByAnalysisId((prev) => ({
        ...prev,
        [analysisId]: `SALESMAP 등록 테스트 실패: ${getApiErrorMessage(error)}`
      }));
    } finally {
      setRegisteringAnalysisId(null);
    }
  };

  return (
    <div className="p-6 h-full flex gap-5">
      {/* Left: Message List */}
      <div className="flex-1 space-y-4 overflow-auto">
        <div className="bg-white border border-gray-200 rounded-lg p-4 shadow-sm">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm text-gray-700">백엔드 Source 목록</h3>
            <span className="text-xs text-gray-500">{sourceListMessage}</span>
          </div>
          {backendSources.length > 0 ? (
            <div className="space-y-2 max-h-48 overflow-y-auto">
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
                    <span className="text-xs text-gray-500">{item.sourceType}</span>
                  </div>
                  <p className="text-xs text-gray-500 mt-1 line-clamp-1">{item.content}</p>
                </button>
              ))}
            </div>
          ) : (
            <p className="text-xs text-gray-500">생성된 Source가 없거나 조회 대기 중입니다.</p>
          )}
        </div>

        <div className="flex gap-2 mb-4 border-b border-gray-200">
          <button
            onClick={() => setStatusFilter('pending')}
            className={`px-4 py-2 text-sm transition-colors ${
              statusFilter === 'pending'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-800'
            }`}
          >
            승인대기
          </button>
          <button
            onClick={() => setStatusFilter('completed')}
            className={`px-4 py-2 text-sm transition-colors ${
              statusFilter === 'completed'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-800'
            }`}
          >
            완료됨
          </button>
          <button
            onClick={() => setStatusFilter('deleted')}
            className={`px-4 py-2 text-sm transition-colors ${
              statusFilter === 'deleted'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-600 hover:text-gray-800'
            }`}
          >
            삭제됨
          </button>
        </div>

        {!selectedSourceId && filteredMessages.map(message => (
          <div
            key={message.id}
            onClick={() => setSelectedMessage(message)}
            className={`bg-white border rounded-lg p-4 cursor-pointer transition-all shadow-sm ${
              selectedMessage?.id === message.id
                ? 'border-blue-400 shadow-md'
                : 'border-gray-200 hover:border-gray-300'
            }`}
          >
            <div className="flex items-start justify-between mb-2.5">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-2">
                  {getStatusBadge(message.status)}
                  <span className="text-xs text-gray-500">{message.date}</span>
                </div>
                <h4 className="text-sm text-gray-800 mb-1 text-left">{message.subject}</h4>
                <p className="text-xs text-gray-600 mb-1 text-left">발신: {message.sender}</p>
                <p className="text-xs text-gray-500 line-clamp-2 text-left">{message.preview}</p>
              </div>
              {selectedMessage?.id === message.id && (
                <ChevronRight className="w-4 h-4 text-blue-500 flex-shrink-0" />
              )}
            </div>
          </div>
        ))}
      </div>

      {/* Right: AI Analysis Panel */}
      <div className="w-96 bg-white border border-gray-200 rounded-lg shadow-sm flex flex-col">
        {selectedSourceId ? (
          <>
            <div className="border-b border-gray-200 px-5 py-4">
              <h3 className="text-sm text-gray-800">Source 상세</h3>
            </div>

            <div className="flex-1 overflow-auto p-5 space-y-4">
              {sourceDetailMessage && (
                <p className="text-sm text-gray-500">{sourceDetailMessage}</p>
              )}

              {selectedSourceDetail && (
                <>
                  <div className="bg-gray-50 border border-gray-200 rounded-md p-3">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs text-gray-500">{selectedSourceDetail.sourceType}</span>
                      <span className="text-xs text-gray-500">{selectedSourceDetail.status}</span>
                    </div>
                    <h4 className="text-sm text-gray-800 text-left mb-2">{selectedSourceDetail.title}</h4>
                    <p className="text-sm text-gray-700 whitespace-pre-line text-left">{selectedSourceDetail.content}</p>
                  </div>

                  <div className="bg-blue-50 border border-blue-200 rounded-md p-3">
                    <div className="flex items-center justify-between gap-3 mb-2">
                      <h4 className="text-sm text-blue-700">Analysis 조회 결과</h4>
                      {isDev && (
                        <button
                          onClick={handleCreateAnalysis}
                          disabled={isAnalyzing}
                          className="px-3 py-1 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded"
                        >
                          {isAnalyzing ? '분석 중...' : 'AI 분석 테스트'}
                        </button>
                      )}
                    </div>
                    {analysisActionMessage && (
                      <p className="text-xs text-gray-600 mb-2 text-left">{analysisActionMessage}</p>
                    )}
                    {sourceAnalyses.length === 0 ? (
                      <p className="text-sm text-gray-600 text-left">아직 분석 결과가 없습니다.</p>
                    ) : (
                      <div className="space-y-3">
                        {sourceAnalyses.map((analysis) => (
                          <div key={analysis.analysisId} className="bg-white border border-blue-100 rounded p-3">
                            <div className="flex items-center justify-between mb-2">
                              <span className="text-xs text-gray-500">#{analysis.analysisId}</span>
                              <span className="text-xs text-gray-500">{analysis.status}</span>
                            </div>
                            <div className="space-y-2">
                              <div>
                                <p className="text-xs text-gray-500 mb-1">요약</p>
                                <p className="text-sm text-gray-700 text-left">{analysis.summary}</p>
                              </div>
                              <div>
                                <p className="text-xs text-gray-500 mb-1">후속 조치</p>
                                <p className="text-sm text-gray-700 text-left">{analysis.followUpAction || '-'}</p>
                              </div>
                              <div>
                                <p className="text-xs text-gray-500 mb-1">일정 정보</p>
                                <p className="text-sm text-gray-700 text-left">{analysis.scheduleText || '-'}</p>
                              </div>
                              <div>
                                <p className="text-xs text-gray-500 mb-1">고객 / 제품 / 금액</p>
                                <p className="text-sm text-gray-700 text-left">
                                  {analysis.customerName || '-'} / {analysis.productName || '-'} / {analysis.amount ?? '-'}
                                </p>
                              </div>
                            </div>
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
                                      <p className="text-xs text-gray-700 mt-1 text-left">
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

                  {/* TODO: keyIssues/confidenceScore are not included in the current backend AnalysisResponse. */}
                </>
              )}
            </div>
          </>
        ) : selectedMessage ? (
          <>
            <div className="border-b border-gray-200 px-5 py-4">
              <h3 className="text-sm text-gray-800">상세 정보</h3>
            </div>

            <div className="flex-1 overflow-auto p-5 space-y-4">
              {/* AI 요약 결과 */}
              <div className="bg-red-50 border-2 border-dashed border-red-300 rounded-lg p-4">
                <div className="flex items-center justify-between mb-3">
                  <h4 className="text-sm text-red-600 font-medium">AI 요약 결과</h4>
                  <span className="text-xs text-red-500">→ Salesmap에 올라갈 내용</span>
                </div>
                <div className="space-y-3">
                  <div>
                    <p className="text-xs text-gray-600 mb-1">제목</p>
                    <p className="text-sm text-gray-800 text-left">{selectedMessage.subject}</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-600 mb-1">AI 분석</p>
                    <p className="text-sm text-gray-700 text-left">{selectedMessage.aiAnalysis}</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-600 mb-1">주요 정보</p>
                    <ul className="space-y-1">
                      {selectedMessage.keyPoints.map((point, idx) => (
                        <li key={idx} className="text-sm text-gray-700 flex items-start gap-2">
                          <span className="text-red-500 text-xs">•</span>
                          <span>{point}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              </div>

              {/* 원문 내용 */}
              <div>
                <h4 className="text-xs text-gray-600 mb-2">원문 내용</h4>
                <div className="bg-gray-50 border border-gray-200 rounded-md p-3">
                  <p className="text-sm text-gray-700 whitespace-pre-line text-left">{selectedMessage.preview}</p>
                </div>
              </div>

              {/* 메타 정보 */}
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <span className="text-xs text-gray-600">출처</span>
                  <span className="text-sm text-gray-800">{selectedMessage.source === 'jandi' ? 'Jandi' : 'Gmail'}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-gray-600">발신자</span>
                  <span className="text-sm text-gray-800">{selectedMessage.sender}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-gray-600">추출 시점</span>
                  <span className="text-sm text-gray-800">{selectedMessage.date}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-xs text-gray-600">상태</span>
                  {getStatusBadge(selectedMessage.status)}
                </div>
              </div>
            </div>

            {selectedMessage.status === 'pending' && (
              <div className="border-t border-gray-200 p-4 flex gap-2">
                <button
                  onClick={() => handleAction(selectedMessage.id, 'approve')}
                  className="flex-1 bg-blue-500 hover:bg-blue-600 text-white py-2 rounded text-sm flex items-center justify-center gap-2"
                >
                  <Send className="w-3.5 h-3.5" />
                  승인
                </button>
                <button
                  onClick={() => handleAction(selectedMessage.id, 'edit')}
                  className="px-3 py-2 border border-gray-300 hover:bg-gray-50 rounded"
                >
                  <Edit className="w-3.5 h-3.5 text-gray-600" />
                </button>
                <button
                  onClick={() => handleAction(selectedMessage.id, 'delete')}
                  className="px-3 py-2 border border-gray-300 hover:bg-gray-50 rounded"
                >
                  <Trash2 className="w-3.5 h-3.5 text-gray-600" />
                </button>
              </div>
            )}
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
            메시지를 선택하여 상세 정보를 확인하세요
          </div>
        )}
      </div>
    </div>
  );
}
