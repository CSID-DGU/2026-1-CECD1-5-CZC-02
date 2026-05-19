import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Edit, Trash2, Send, ChevronRight } from 'lucide-react';

export function MessageView() {
  const { source } = useParams();
  const [selectedMessage, setSelectedMessage] = useState(null);
  const [statusFilter, setStatusFilter] = useState('pending');

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

  return (
    <div className="p-6 h-full flex gap-5">
      {/* Left: Message List */}
      <div className="flex-1 space-y-4 overflow-auto">
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

        {filteredMessages.map(message => (
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
                <h4 className="text-sm text-gray-800 mb-1">{message.subject}</h4>
                <p className="text-xs text-gray-600 mb-1">발신: {message.sender}</p>
                <p className="text-xs text-gray-500 line-clamp-2">{message.preview}</p>
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
        {selectedMessage ? (
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
                    <p className="text-sm text-gray-800">{selectedMessage.subject}</p>
                  </div>
                  <div>
                    <p className="text-xs text-gray-600 mb-1">AI 분석</p>
                    <p className="text-sm text-gray-700">{selectedMessage.aiAnalysis}</p>
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
                  <p className="text-sm text-gray-700 whitespace-pre-line">{selectedMessage.preview}</p>
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
