import { useState, useRef, useEffect } from 'react';
import { ChevronLeft, ChevronRight, ChevronDown, Search, Check, X, Edit2, Save } from 'lucide-react';
import { createSchedule, getSchedules } from '../api/schedules';
import { createSource, getSources } from '../api/sources';
import { getAnalysesBySource } from '../api/analyses';
import { getSalesmapRecordsByAnalysis } from '../api/salesmapRecords';
import { getApiErrorMessage } from '../api/errors';

export function Dashboard() {
  const isDev = import.meta.env.DEV;
  const [currentMonth, setCurrentMonth] = useState(new Date(2026, 4, 1));
  const [showUserFilter, setShowUserFilter] = useState(false);
  const [userSearch, setUserSearch] = useState('');
  const [selectedDate, setSelectedDate] = useState(null);
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [editingSchedule, setEditingSchedule] = useState(null);
  const [apiStatuses, setApiStatuses] = useState({
    schedules: { label: '일정 조회', status: 'loading', count: null, message: '조회 중' },
    sources: { label: '메일 조회', status: 'loading', count: null, message: '조회 중' },
    analyses: { label: 'AI 분석 조회', status: 'idle', count: null, message: '메일 조회 후 확인' },
    salesmapRecords: { label: 'Salesmap 등록 조회', status: 'idle', count: null, message: '분석 조회 후 확인' },
  });
  const [testPanelMessage, setTestPanelMessage] = useState('');
  const [isCreatingTestData, setIsCreatingTestData] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const dropdownRef = useRef(null);
  const modalRef = useRef(null);

  const [users, setUsers] = useState([
    { id: 'user1', name: '김영업', selected: true },
    { id: 'user2', name: '박세일', selected: true },
    { id: 'user3', name: '이마케터', selected: false },
    { id: 'user4', name: '최영업', selected: false },
  ]);

  const currentUserId = 'user1';

  const [events, setEvents] = useState([
    { id: 'e1', date: 5, type: 'call', title: '고객 전화', time: '10:00', userId: 'user1', userName: '김영업', description: 'ABC 기업 담당자와 신규 제품 문의 관련 통화', relatedPerson: 'ABC 기업 김담당' },
    { id: 'e2', date: 5, type: 'meeting', title: '오전 팀 미팅', time: '09:00', userId: 'user2', userName: '박세일', description: '주간 영업 현황 공유', relatedPerson: '영업팀 전체' },
    { id: 'e3', date: 10, type: 'meeting', title: '고객사 미팅', time: '14:00', userId: 'user2', userName: '박세일', description: 'XYZ 회사 계약 논의', relatedPerson: 'XYZ 회사 이사장' },
    { id: 'e4', date: 12, type: 'meeting', title: '제안 발표', time: '15:00', userId: 'user1', userName: '김영업', description: 'DEF 그룹 신규 제안서 발표', relatedPerson: 'DEF 그룹 임원진' },
    { id: 'e5', date: 12, type: 'task', title: '보고서 작성', time: '10:00', userId: 'user1', userName: '김영업', description: '월간 영업 실적 보고서 작성', relatedPerson: '-' },
    { id: 'e6', date: 15, type: 'email', title: '제안서 발송', time: '11:00', userId: 'user3', userName: '이마케터', description: 'GHI 기업에 제안서 이메일 전송', relatedPerson: 'GHI 기업 구매팀' },
    { id: 'e7', date: 18, type: 'call', title: '영업 콜', time: '16:00', userId: 'user1', userName: '김영업', description: '신규 고객 상담 전화', relatedPerson: '최지연 과장' },
    { id: 'e8', date: 22, type: 'meeting', title: '클라이언트 미팅', time: '13:00', userId: 'user2', userName: '박세일', description: '프로젝트 진행 상황 점검', relatedPerson: 'JKL 회사' },
  ]);

  const [todaySchedule] = useState([
    { id: '1', time: '10:00', title: '김영희 고객 미팅', type: 'meeting', userId: 'user1', userName: '김영업', status: 'SCHEDULED' },
    { id: '2', time: '14:00', title: '신규 제안 발표', type: 'meeting', userId: 'user2', userName: '박세일', status: 'SCHEDULED' },
    { id: '3', time: '16:30', title: '박철수 고객 전화', type: 'call', userId: 'user1', userName: '김영업', status: 'SCHEDULED' },
  ]);

  const [upcomingSchedule] = useState([
    { id: '4', time: '09:00', title: '이민수 고객 이메일 확인', type: 'email', date: '05/11', userId: 'user1', userName: '김영업', status: 'SCHEDULED' },
    { id: '5', time: '11:00', title: '최지연 미팅', type: 'meeting', date: '05/12', userId: 'user2', userName: '박세일', status: 'SCHEDULED' },
    { id: '6', time: '15:00', title: '월간 영업 보고', type: 'meeting', date: '05/14', userId: 'user3', userName: '이마케터', status: 'COMPLETED' },
    { id: '7', time: '10:30', title: '신규 고객 상담', type: 'call', date: '05/15', userId: 'user1', userName: '김영업', status: 'CANCELED' },
  ]);

  useEffect(() => {
    const updateApiStatus = (key, nextStatus) => {
      setApiStatuses((prev) => ({
        ...prev,
        [key]: {
          ...prev[key],
          ...nextStatus,
        },
      }));
    };

    const handleApiError = (key, error) => {
      console.error(`Failed to fetch ${key}:`, error);
      updateApiStatus(key, {
        status: 'error',
        count: null,
        message: getApiErrorMessage(error),
      });
    };

    const fetchDashboardApiStatuses = async () => {
      try {
        const schedules = await getSchedules();
        updateApiStatus('schedules', {
          status: 'success',
          count: schedules.length,
          message: '확인됨',
        });
      } catch (error) {
        handleApiError('schedules', error);
      }

      let sources = [];

      try {
        sources = await getSources();
        updateApiStatus('sources', {
          status: 'success',
          count: sources.length,
          message: '확인됨',
        });
      } catch (error) {
        handleApiError('sources', error);
      }

      const firstSourceId = sources[0]?.sourceId;

      if (!firstSourceId) {
        updateApiStatus('analyses', {
          status: 'idle',
          count: 0,
          message: '조회할 메일 없음',
        });
        updateApiStatus('salesmapRecords', {
          status: 'idle',
          count: 0,
          message: '조회할 분석 없음',
        });
        return;
      }

      let analyses = [];

      try {
        analyses = await getAnalysesBySource(firstSourceId);
        updateApiStatus('analyses', {
          status: 'success',
          count: analyses.length,
          message: '확인됨',
        });
      } catch (error) {
        handleApiError('analyses', error);
      }

      const firstAnalysisId = analyses[0]?.analysisId;

      if (!firstAnalysisId) {
        updateApiStatus('salesmapRecords', {
          status: 'idle',
          count: 0,
          message: '조회할 분석 없음',
        });
        return;
      }

      try {
        const records = await getSalesmapRecordsByAnalysis(firstAnalysisId);
        updateApiStatus('salesmapRecords', {
          status: 'success',
          count: records.length,
          message: '확인됨',
        });
      } catch (error) {
        handleApiError('salesmapRecords', error);
      }
    };

    fetchDashboardApiStatuses();
  }, [refreshKey]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setShowUserFilter(false);
      }
      if (modalRef.current && !modalRef.current.contains(event.target)) {
        if (showScheduleModal && !editingSchedule) {
          setShowScheduleModal(false);
          setSelectedDate(null);
        }
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [showScheduleModal, editingSchedule]);

  const handlePreviousMonth = () => {
    setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1, 1));
  };

  const handleNextMonth = () => {
    setCurrentMonth(new Date(currentMonth.getFullYear(), currentMonth.getMonth() + 1, 1));
  };

  const toggleUser = (userId) => {
    setUsers(users.map(user =>
      user.id === userId ? { ...user, selected: !user.selected } : user
    ));
  };

  const toggleSelectAll = () => {
    const allSelected = users.every(u => u.selected);
    setUsers(users.map(user => ({ ...user, selected: !allSelected })));
  };

  const selectedUserIds = users.filter(u => u.selected).map(u => u.id);
  const selectedCount = selectedUserIds.length;

  const filteredEvents = events.filter(event => selectedUserIds.includes(event.userId));
  const filteredTodaySchedule = todaySchedule.filter(item => selectedUserIds.includes(item.userId));
  const filteredUpcomingSchedule = upcomingSchedule.filter(item => selectedUserIds.includes(item.userId));

  const filteredUsers = users.filter(user =>
    user.name.toLowerCase().includes(userSearch.toLowerCase())
  );

  const getDaysInMonth = () => {
    const year = currentMonth.getFullYear();
    const month = currentMonth.getMonth();
    const firstDay = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const days = [];
    for (let i = 0; i < firstDay; i++) {
      days.push(null);
    }
    for (let i = 1; i <= daysInMonth; i++) {
      days.push(i);
    }
    return days;
  };

  const getEventsForDate = (date) => {
    return filteredEvents.filter(e => e.date === date);
  };

  const handleDateClick = (date) => {
    const dayEvents = getEventsForDate(date);
    if (dayEvents.length > 0) {
      setSelectedDate(date);
      setShowScheduleModal(true);
    }
  };

  const handleEditSchedule = (schedule) => {
    if (schedule.userId === currentUserId) {
      setEditingSchedule({ ...schedule });
    }
  };

  const handleSaveSchedule = () => {
    if (editingSchedule) {
      setEvents(events.map(e =>
        e.id === editingSchedule.id ? editingSchedule : e
      ));
      setEditingSchedule(null);
      alert('일정이 수정되었습니다.');
    }
  };

  const handleCancelEdit = () => {
    setEditingSchedule(null);
  };

  const getTypeLabel = (type) => {
    switch (type) {
      case 'call': return '전화';
      case 'meeting': return '미팅';
      case 'email': return '이메일';
      case 'task': return '업무';
      default: return type;
    }
  };

  const getTypeColor = (type) => {
    switch (type) {
      case 'call': return 'bg-orange-100 text-orange-700';
      case 'meeting': return 'bg-blue-100 text-blue-700';
      case 'email': return 'bg-green-100 text-green-700';
      case 'task': return 'bg-purple-100 text-purple-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  const getTypeDot = (type) => {
    switch (type) {
      case 'call': return 'bg-orange-500';
      case 'meeting': return 'bg-blue-500';
      case 'email': return 'bg-green-500';
      default: return 'bg-gray-500';
    }
  };

  const getScheduleStatusLabel = (status) => {
    switch (status) {
      case 'SCHEDULED': return '진행 예정';
      case 'COMPLETED': return '완료';
      case 'CANCELED': return '취소';
      default: return '진행 예정';
    }
  };

  const getScheduleStatusColor = (status) => {
    switch (status) {
      case 'COMPLETED': return 'bg-green-100 text-green-700 border-green-200';
      case 'CANCELED': return 'bg-red-100 text-red-700 border-red-200';
      default: return 'bg-blue-100 text-blue-700 border-blue-200';
    }
  };

  const monthName = currentMonth.toLocaleString('ko-KR', { year: 'numeric', month: 'long' });

  const getApiStatusText = (status) => {
    if (status.status === 'success') {
      return `${status.count}건 확인됨`;
    }

    if (status.status === 'error') {
      return status.message;
    }

    return status.message;
  };

  const getApiStatusColor = (status) => {
    if (status.status === 'success') {
      return 'text-green-700 bg-green-50 border-green-200';
    }

    if (status.status === 'error') {
      return 'text-red-700 bg-red-50 border-red-200';
    }

    return 'text-gray-600 bg-gray-50 border-gray-200';
  };

  const formatLocalDateTime = (date) => {
    const pad = (value) => String(value).padStart(2, '0');

    return [
      date.getFullYear(),
      pad(date.getMonth() + 1),
      pad(date.getDate()),
    ].join('-') + 'T' + [
      pad(date.getHours()),
      pad(date.getMinutes()),
      pad(date.getSeconds()),
    ].join(':');
  };

  const logApiError = (label, error) => {
    console.error(label, {
      status: error.response?.status,
      message: error.response?.data?.message,
      data: error.response?.data?.data,
      rawError: error,
    });
  };

  const handleCreateTestSource = async () => {
    try {
      setIsCreatingTestData(true);
      setTestPanelMessage('');

      await createSource({
        sourceType: 'EMAIL',
        title: '예시 이메일',
        content: '고객사 미팅 일정과 후속 조치가 포함된 예시 내용입니다.',
      });

      setTestPanelMessage('메일 예시 데이터가 생성되었습니다.');
      setRefreshKey((prev) => prev + 1);
    } catch (error) {
      logApiError('Failed to create sample source. Check request body and backend DTO.', error);
      setTestPanelMessage(`메일 예시 데이터 생성 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setIsCreatingTestData(false);
    }
  };

  const handleCreateTestSchedule = async () => {
    const oneHourLater = new Date(Date.now() + 60 * 60 * 1000);

    try {
      setIsCreatingTestData(true);
      setTestPanelMessage('');

      await createSchedule({
        title: '예시 일정',
        scheduleDateTime: formatLocalDateTime(oneHourLater),
        memo: '프론트-백 연동 확인용 일정',
      });

      setTestPanelMessage('일정 예시 데이터가 생성되었습니다.');
      setRefreshKey((prev) => prev + 1);
    } catch (error) {
      logApiError('Failed to create sample schedule. Check request body and backend DTO.', error);
      setTestPanelMessage(`일정 예시 데이터 생성 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setIsCreatingTestData(false);
    }
  };

  return (
    <div className="p-6 flex gap-6 h-full">
      <div className="w-72 space-y-5 flex flex-col">
        {isDev && (
          <details className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
            <summary className="text-sm text-gray-700 cursor-pointer">개발용 연동 확인</summary>
            <div className="space-y-2 mt-3">
              {Object.entries(apiStatuses).map(([key, status]) => (
                <div
                  key={key}
                  className={`border rounded px-3 py-2 ${getApiStatusColor(status)}`}
                >
                  <div className="text-xs font-medium">{status.label}</div>
                  <div className="text-xs mt-0.5">{getApiStatusText(status)}</div>
                </div>
              ))}

              <div className="pt-2 space-y-2">
                <button
                  onClick={handleCreateTestSource}
                  disabled={isCreatingTestData}
                  className="w-full px-3 py-2 text-xs bg-blue-500 hover:bg-blue-600 disabled:bg-blue-300 text-white rounded"
                >
                  메일 예시 생성
                </button>
                <button
                  onClick={handleCreateTestSchedule}
                  disabled={isCreatingTestData}
                  className="w-full px-3 py-2 text-xs border border-gray-300 hover:bg-gray-50 disabled:bg-gray-100 text-gray-700 rounded"
                >
                  일정 예시 생성
                </button>
                {testPanelMessage && (
                  <p className="text-xs text-gray-600">{testPanelMessage}</p>
                )}
              </div>
            </div>
          </details>
        )}

        <div className="flex-1">
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5 h-full flex flex-col">
            <h3 className="text-sm text-gray-700 mb-4">오늘의 일정</h3>
            <div className="space-y-2.5 overflow-y-auto flex-1">
              {filteredTodaySchedule.map(item => (
                <ScheduleListItem
                  key={item.id}
                  item={item}
                  getTypeColor={getTypeColor}
                  getTypeDot={getTypeDot}
                  getScheduleStatusColor={getScheduleStatusColor}
                  getScheduleStatusLabel={getScheduleStatusLabel}
                />
              ))}
            </div>
          </div>
        </div>

        <div className="flex-1">
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5 h-full flex flex-col">
            <h3 className="text-sm text-gray-700 mb-4">다가오는 일정</h3>
            <div className="space-y-2.5 overflow-y-auto flex-1">
              {filteredUpcomingSchedule.map(item => (
                <ScheduleListItem
                  key={item.id}
                  item={item}
                  getTypeColor={getTypeColor}
                  getTypeDot={getTypeDot}
                  getScheduleStatusColor={getScheduleStatusColor}
                  getScheduleStatusLabel={getScheduleStatusLabel}
                />
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="flex-1">
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 h-full flex flex-col">
          <div className="mb-5 flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <button onClick={handlePreviousMonth} className="p-1.5 hover:bg-gray-100 rounded">
                  <ChevronLeft className="w-4 h-4 text-gray-600" />
                </button>
                <h3 className="text-base text-gray-800 min-w-[120px] text-center">{monthName}</h3>
                <button onClick={handleNextMonth} className="p-1.5 hover:bg-gray-100 rounded">
                  <ChevronRight className="w-4 h-4 text-gray-600" />
                </button>
              </div>

              <div className="relative" ref={dropdownRef}>
                <button
                  onClick={() => setShowUserFilter(!showUserFilter)}
                  className="flex items-center gap-2 px-3 py-1.5 text-sm border border-gray-300 rounded hover:bg-gray-50"
                >
                  <span className="text-gray-700">사용자 {selectedCount}명</span>
                  <ChevronDown className="w-3.5 h-3.5 text-gray-500" />
                </button>

                {showUserFilter && (
                  <div className="absolute top-full left-0 mt-2 w-64 bg-white border border-gray-200 rounded-lg shadow-lg z-10">
                    <div className="p-3 border-b border-gray-200">
                      <div className="relative">
                        <Search className="absolute left-2.5 top-2 w-3.5 h-3.5 text-gray-400" />
                        <input
                          type="text"
                          value={userSearch}
                          onChange={(e) => setUserSearch(e.target.value)}
                          placeholder="사용자 검색"
                          className="w-full pl-8 pr-3 py-1.5 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                        />
                      </div>
                    </div>

                    <div className="p-2 border-b border-gray-200">
                      <button
                        onClick={toggleSelectAll}
                        className="w-full flex items-center gap-2 px-2 py-1.5 hover:bg-gray-50 rounded text-sm"
                      >
                        <div className={`w-4 h-4 border rounded flex items-center justify-center ${
                          users.every(u => u.selected) ? 'bg-blue-500 border-blue-500' : 'border-gray-300'
                        }`}>
                          {users.every(u => u.selected) && <Check className="w-3 h-3 text-white" />}
                        </div>
                        <span className="text-gray-700">전체 선택</span>
                      </button>
                    </div>

                    <div className="max-h-48 overflow-y-auto p-2">
                      {filteredUsers.map(user => (
                        <button
                          key={user.id}
                          onClick={() => toggleUser(user.id)}
                          className="w-full flex items-center gap-2 px-2 py-1.5 hover:bg-gray-50 rounded text-sm"
                        >
                          <div className={`w-4 h-4 border rounded flex items-center justify-center ${
                            user.selected ? 'bg-blue-500 border-blue-500' : 'border-gray-300'
                          }`}>
                            {user.selected && <Check className="w-3 h-3 text-white" />}
                          </div>
                          <span className="text-gray-700">{user.name}</span>
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="flex-1 flex flex-col">
            <div className="grid grid-cols-7 gap-1 mb-2">
              {['일', '월', '화', '수', '목', '금', '토'].map(day => (
                <div key={day} className="text-center text-xs text-gray-500 py-2">{day}</div>
              ))}
            </div>

            <div className="grid grid-cols-7 gap-1 flex-1">
              {getDaysInMonth().map((day, index) => {
                const dayEvents = day ? getEventsForDate(day) : [];
                return (
                  <div
                    key={index}
                    onClick={() => day && handleDateClick(day)}
                    className={`border border-gray-100 p-2 rounded min-h-[80px] ${
                      day ? 'hover:bg-blue-50/30 cursor-pointer bg-white transition-colors' : 'bg-gray-50/50'
                    }`}
                  >
                    {day && (
                      <>
                        <div className="text-sm text-gray-700 mb-1.5 font-medium">{day}</div>
                        <div className="space-y-0.5">
                          {dayEvents.slice(0, 3).map((event) => (
                            <div
                              key={event.id}
                              className={`text-xs px-1.5 py-0.5 rounded truncate ${getTypeColor(event.type)}`}
                            >
                              <span className="font-medium">{event.time}</span> {event.title}
                            </div>
                          ))}
                          {dayEvents.length > 3 && (
                            <div className="text-xs text-gray-500 px-1">+{dayEvents.length - 3}건</div>
                          )}
                        </div>
                      </>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          <div className="mt-4 pt-4 border-t border-gray-100 flex gap-4 text-xs">
            <Legend color="bg-orange-400" label="전화" />
            <Legend color="bg-blue-400" label="미팅" />
            <Legend color="bg-green-400" label="이메일" />
            <Legend color="bg-purple-400" label="업무" />
          </div>
        </div>
      </div>

      {showScheduleModal && selectedDate && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
          <div ref={modalRef} className="bg-white rounded-lg w-full max-w-2xl max-h-[80vh] overflow-auto border border-gray-200 shadow-xl text-left">
            <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-base text-gray-800">
                {currentMonth.getFullYear()}년 {currentMonth.getMonth() + 1}월 {selectedDate}일 일정
              </h3>
              <button
                onClick={() => {
                  setShowScheduleModal(false);
                  setSelectedDate(null);
                  setEditingSchedule(null);
                }}
                className="p-1 hover:bg-gray-100 rounded"
              >
                <X className="w-5 h-5 text-gray-600" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              {getEventsForDate(selectedDate).map((event) => (
                <div key={event.id} className="border border-gray-200 rounded-lg p-4">
                  {editingSchedule?.id === event.id ? (
                    <div className="space-y-4">
                      <div>
                        <label className="block text-xs text-gray-600 mb-1.5">제목</label>
                        <input
                          type="text"
                          value={editingSchedule.title}
                          onChange={(e) => setEditingSchedule({ ...editingSchedule, title: e.target.value })}
                          className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                        />
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs text-gray-600 mb-1.5">시간</label>
                          <input
                            type="time"
                            value={editingSchedule.time}
                            onChange={(e) => setEditingSchedule({ ...editingSchedule, time: e.target.value })}
                            className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          />
                        </div>
                        <div>
                          <label className="block text-xs text-gray-600 mb-1.5">유형</label>
                          <select
                            value={editingSchedule.type}
                            onChange={(e) => setEditingSchedule({ ...editingSchedule, type: e.target.value })}
                            className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          >
                            <option value="meeting">미팅</option>
                            <option value="call">전화</option>
                            <option value="task">업무</option>
                            <option value="email">이메일</option>
                          </select>
                        </div>
                      </div>
                      <div>
                        <label className="block text-xs text-gray-600 mb-1.5">관련 고객/담당자</label>
                        <input
                          type="text"
                          value={editingSchedule.relatedPerson}
                          onChange={(e) => setEditingSchedule({ ...editingSchedule, relatedPerson: e.target.value })}
                          className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                        />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-600 mb-1.5">설명</label>
                        <textarea
                          value={editingSchedule.description}
                          onChange={(e) => setEditingSchedule({ ...editingSchedule, description: e.target.value })}
                          className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          rows={3}
                        />
                      </div>
                      <div className="flex gap-2">
                        <button
                          onClick={handleSaveSchedule}
                          className="flex-1 bg-blue-500 hover:bg-blue-600 text-white py-2 rounded text-sm flex items-center justify-center gap-2"
                        >
                          <Save className="w-4 h-4" />
                          저장
                        </button>
                        <button
                          onClick={handleCancelEdit}
                          className="px-4 py-2 border border-gray-300 hover:bg-gray-50 rounded text-sm"
                        >
                          취소
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div>
                      <div className="flex items-start justify-between mb-3">
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-2">
                            <span className={`px-2 py-0.5 text-xs rounded ${getTypeColor(event.type)}`}>
                              {getTypeLabel(event.type)}
                            </span>
                            <span className="text-sm text-gray-600">{event.time}</span>
                          </div>
                          <h4 className="text-base text-gray-800 mb-1">{event.title}</h4>
                          <p className="text-xs text-gray-500">담당자: {event.userName}</p>
                        </div>
                        {event.userId === currentUserId && (
                          <button
                            onClick={() => handleEditSchedule(event)}
                            className="p-2 hover:bg-gray-100 rounded"
                          >
                            <Edit2 className="w-4 h-4 text-gray-600" />
                          </button>
                        )}
                      </div>
                      <div className="space-y-2 text-sm">
                        <div>
                          <span className="text-xs text-gray-500">관련 고객/담당자</span>
                          <p className="text-gray-700">{event.relatedPerson}</p>
                        </div>
                        <div>
                          <span className="text-xs text-gray-500">설명</span>
                          <p className="text-gray-700">{event.description}</p>
                        </div>
                      </div>
                      {event.userId !== currentUserId && (
                        <div className="mt-3 pt-3 border-t border-gray-200">
                          <span className="text-xs text-gray-500 italic">다른 사용자의 일정입니다. 보기만 가능합니다.</span>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ScheduleListItem({ item, getTypeColor, getTypeDot, getScheduleStatusColor, getScheduleStatusLabel }) {
  return (
    <div className={`flex items-start gap-2.5 p-2.5 border rounded-md ${getTypeColor(item.type)}`}>
      <div className={`w-1.5 h-1.5 rounded-full mt-1.5 flex-shrink-0 ${getTypeDot(item.type)}`}></div>
      <div className="flex-1 min-w-0">
        <div className="text-sm text-gray-700">
          {item.date && <span className="text-xs text-gray-500 mr-2">{item.date}</span>}
          {item.time} - {item.title}
        </div>
        <div className="flex items-center gap-2 mt-1">
          <div className="text-xs text-gray-500">{item.userName}</div>
          <span className={`px-2 py-0.5 text-[11px] rounded-full border ${getScheduleStatusColor(item.status)}`}>
            {getScheduleStatusLabel(item.status)}
          </span>
        </div>
      </div>
    </div>
  );
}

function Legend({ color, label }) {
  return (
    <div className="flex items-center gap-1.5">
      <div className={`w-2.5 h-2.5 ${color} rounded-full`}></div>
      <span className="text-gray-600">{label}</span>
    </div>
  );
}
