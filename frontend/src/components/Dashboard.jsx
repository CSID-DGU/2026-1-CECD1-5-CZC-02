import { useState, useRef, useEffect, useCallback } from 'react';
import { ChevronLeft, ChevronRight, ChevronDown, Search, Check, X, Edit2, Save } from 'lucide-react';
import { createSchedule, deleteSchedule, getSchedules, updateSchedule } from '../api/schedules';
import { createSource, getSources } from '../api/sources';
import { getAnalysesBySource } from '../api/analyses';
import { getSalesmapRecordsByAnalysis } from '../api/salesmapRecords';
import { getApiErrorMessage } from '../api/errors';

const CURRENT_USER_ID = 'user1';

function pad(value) {
  return String(value).padStart(2, '0');
}

function toDateInput(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function toTimeInput(date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatDateTimeForApi(dateValue, timeValue) {
  return `${dateValue}T${timeValue}:00`;
}

function formatDisplayTime(date) {
  return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
}

function stripScheduleMeta(value = '') {
  return value
    .split('\n')
    .filter((line) => (
      !line.startsWith('업무 유형:')
      && !line.startsWith('보낸 사람:')
      && !line.startsWith('참석자:')
    ))
    .join('\n')
    .trim();
}

function extractMemoValue(memo = '', label) {
  const found = memo.match(new RegExp(`${label}:\\s*([^\\n]+)`));
  return found?.[1]?.trim() || '';
}

function extractAttendees(memo = '') {
  const explicit = extractMemoValue(memo, '참석자');
  if (explicit) {
    return explicit;
  }

  const found = memo.match(/참석자[:：]\s*([^/\n]+)/);
  return found?.[1]?.trim() || '';
}

function normalizeScheduleStatus(status) {
  return status || 'SCHEDULED';
}

export function Dashboard() {
  const isDev = import.meta.env.DEV;
  const [currentMonth, setCurrentMonth] = useState(() => new Date());
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
  const [events, setEvents] = useState([]);
  const [todaySchedule, setTodaySchedule] = useState([]);
  const [upcomingSchedule, setUpcomingSchedule] = useState([]);
  const dropdownRef = useRef(null);
  const modalRef = useRef(null);

  const [users, setUsers] = useState([
    { id: 'user1', name: '나', selected: true },
    { id: 'user2', name: '김영업', selected: true },
    { id: 'user3', name: '마케팅팀', selected: false },
    { id: 'user4', name: '고객지원팀', selected: false },
  ]);

  const getDemoEvents = useCallback(() => ([
    {
      id: 'demo-call-1',
      date: 5,
      type: 'call',
      title: '신규 고객 전화 상담',
      time: '오전 10:00',
      timeValue: '10:00',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-05`,
      userId: 'user2',
      userName: '김영업',
      sender: 'ABC 기업',
      attendees: '김담당',
      description: '신규 제품 문의와 도입 가능성을 전화로 확인합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-email-1',
      date: 9,
      type: 'email',
      title: '제안서 발송',
      time: '오전 11:00',
      timeValue: '11:00',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-09`,
      userId: 'user3',
      userName: '마케팅팀',
      sender: 'GHI 기업',
      attendees: '구매팀',
      description: '제품 소개 자료와 제안서를 이메일로 발송합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-meeting-1',
      date: 11,
      type: 'meeting',
      title: 'Delta Systems 도입 미팅',
      time: '오후 02:00',
      timeValue: '14:00',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-11`,
      userId: 'user2',
      userName: '김영업',
      sender: 'Delta Systems',
      attendees: '최유진, 김영업',
      description: 'Sales Analytics Platform 도입 범위와 예상 비용을 논의합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-task-sales-1',
      date: 18,
      type: 'task',
      title: 'ABC 고객사 견적서 정리',
      time: '오전 09:30',
      timeValue: '09:30',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-18`,
      userId: 'user2',
      userName: '김영업',
      sender: 'ABC 고객사',
      attendees: '박지훈',
      description: '미팅 후 전달할 견적서와 제안 조건을 정리합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-call-marketing-1',
      date: 12,
      type: 'call',
      title: '캠페인 리드 확인 전화',
      time: '오후 03:30',
      timeValue: '15:30',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-12`,
      userId: 'user3',
      userName: '마케팅팀',
      sender: '마케팅 리드',
      attendees: '이수진',
      description: '캠페인 유입 고객의 관심 제품과 미팅 가능 여부를 확인합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-email-marketing-1',
      date: 19,
      type: 'email',
      title: '제품 소개 자료 업데이트',
      time: '오전 10:30',
      timeValue: '10:30',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-19`,
      userId: 'user3',
      userName: '마케팅팀',
      sender: '내부 협업',
      attendees: '영업팀',
      description: '신규 기능 설명과 데모 이미지를 포함한 소개 자료를 업데이트합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-call-support-1',
      date: 6,
      type: 'call',
      title: '고객 문의 응대',
      time: '오후 01:30',
      timeValue: '13:30',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-06`,
      userId: 'user4',
      userName: '고객지원팀',
      sender: '기존 고객',
      attendees: '지원 담당자',
      description: '사용 중 발생한 문의사항을 확인하고 후속 안내를 진행합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-email-support-1',
      date: 16,
      type: 'email',
      title: '사용 가이드 발송',
      time: '오후 05:00',
      timeValue: '17:00',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-16`,
      userId: 'user4',
      userName: '고객지원팀',
      sender: '신규 고객',
      attendees: '온보딩 담당자',
      description: '계정 설정과 캘린더 연동 방법을 이메일로 안내합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-task-support-1',
      date: 24,
      type: 'task',
      title: 'FAQ 문서 보완',
      time: '오전 11:00',
      timeValue: '11:00',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-24`,
      userId: 'user4',
      userName: '고객지원팀',
      sender: '내부 업무',
      attendees: '고객지원팀',
      description: '반복 문의가 많은 Gmail 연동과 일정 등록 항목을 보완합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
    {
      id: 'demo-task-1',
      date: 14,
      type: 'task',
      title: '월간 영업 보고서 작성',
      time: '오후 04:00',
      timeValue: '16:00',
      dateValue: `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-14`,
      userId: 'user1',
      userName: '나',
      sender: '내부 업무',
      attendees: '영업팀',
      description: '이번 달 영업 활동과 후속 조치 현황을 정리합니다.',
      status: 'SCHEDULED',
      isDemo: true,
    },
  ]), [currentMonth]);

  const toSchedulePanelItem = useCallback((event) => ({
    id: event.id,
    scheduleId: event.scheduleId,
    time: event.time,
    title: event.title,
    type: event.type,
    date: `${currentMonth.getMonth() + 1}/${event.date}`,
    userId: event.userId,
    userName: event.userName,
    status: normalizeScheduleStatus(event.status),
  }), [currentMonth]);

  const updateCalendarState = useCallback((nextEvents) => {
    const sortedEvents = [...nextEvents].sort((a, b) => {
      const dateDiff = a.date - b.date;
      if (dateDiff !== 0) {
        return dateDiff;
      }
      return (a.timeValue || '').localeCompare(b.timeValue || '');
    });

    setEvents(sortedEvents);

    const today = new Date();
    const isCurrentVisibleMonth =
      today.getFullYear() === currentMonth.getFullYear()
      && today.getMonth() === currentMonth.getMonth();

    const activeEvents = sortedEvents.filter((event) => event.status !== 'CANCELED');
    const todayItems = isCurrentVisibleMonth
      ? activeEvents.filter((event) => event.date === today.getDate()).map(toSchedulePanelItem)
      : [];

    const upcomingItems = activeEvents
      .filter((event) => !isCurrentVisibleMonth || event.date !== today.getDate())
      .slice(0, 8)
      .map(toSchedulePanelItem);

    setTodaySchedule(todayItems);
    setUpcomingSchedule(upcomingItems);
  }, [currentMonth, toSchedulePanelItem]);

  const syncSchedulesToCalendar = useCallback((schedules) => {
    if (!Array.isArray(schedules)) {
      updateCalendarState(getDemoEvents());
      return;
    }

    const backendEvents = schedules
      .filter((schedule) => schedule.scheduleDateTime)
      .filter((schedule) => schedule.status !== 'CANCELED')
      .map((schedule) => {
        const scheduleDate = new Date(schedule.scheduleDateTime);
        const memo = schedule.memo || '';
        const attendees = extractAttendees(memo);
        const sender = extractMemoValue(memo, '보낸 사람') || 'AI 분석';
        const type = extractMemoValue(memo, '업무 유형') || 'meeting';

        return {
          id: `schedule-${schedule.scheduleId}`,
          scheduleId: schedule.scheduleId,
          analysisId: schedule.analysisId,
          sourceId: schedule.sourceId,
          date: scheduleDate.getDate(),
          dateValue: toDateInput(scheduleDate),
          timeValue: toTimeInput(scheduleDate),
          type,
          title: schedule.title || '일정',
          time: formatDisplayTime(scheduleDate),
          userId: CURRENT_USER_ID,
          userName: '나',
          sender,
          attendees,
          description: stripScheduleMeta(memo),
          status: normalizeScheduleStatus(schedule.status),
        };
      });

    updateCalendarState([...getDemoEvents(), ...backendEvents]);
  }, [getDemoEvents, updateCalendarState]);

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
        const schedules = await getSchedules({ size: 50 });
        syncSchedulesToCalendar(schedules);
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
        sources = await getSources({ size: 20 });
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
  }, [refreshKey, syncSchedulesToCalendar]);

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

  useEffect(() => {
    updateCalendarState(events);
    // currentMonth changes should refresh side lists without refetching.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentMonth]);

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

  const getEventsForDate = (date) => filteredEvents.filter(e => e.date === date);

  const handleDateClick = (date) => {
    const dayEvents = getEventsForDate(date);
    if (dayEvents.length > 0) {
      setSelectedDate(date);
      setShowScheduleModal(true);
    }
  };

  const handleEditSchedule = (schedule) => {
    if (schedule.userId !== CURRENT_USER_ID) {
      return;
    }

    setEditingSchedule({
      ...schedule,
      dateValue: schedule.dateValue || `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-${pad(schedule.date)}`,
      timeValue: schedule.timeValue || '09:00',
      sender: schedule.sender || '',
      attendees: schedule.attendees || '',
      description: schedule.description || '',
    });
  };

  const buildScheduleMemo = (schedule) => {
    const lines = [];
    if (schedule.type?.trim()) {
      lines.push(`업무 유형: ${schedule.type.trim()}`);
    }
    if (schedule.sender?.trim()) {
      lines.push(`보낸 사람: ${schedule.sender.trim()}`);
    }
    if (schedule.attendees?.trim()) {
      lines.push(`참석자: ${schedule.attendees.trim()}`);
    }
    if (schedule.description?.trim()) {
      lines.push(schedule.description.trim());
    }

    return lines.join('\n');
  };

  const handleSaveSchedule = async () => {
    if (!editingSchedule) {
      return;
    }

    const dateTime = new Date(`${editingSchedule.dateValue}T${editingSchedule.timeValue}:00`);
    const nextEvent = {
      ...editingSchedule,
      date: dateTime.getDate(),
      time: formatDisplayTime(dateTime),
      status: normalizeScheduleStatus(editingSchedule.status),
    };

    try {
      if (editingSchedule.scheduleId) {
        await updateSchedule(editingSchedule.scheduleId, {
          title: editingSchedule.title,
          scheduleDateTime: formatDateTimeForApi(editingSchedule.dateValue, editingSchedule.timeValue),
          memo: buildScheduleMemo(editingSchedule),
        });
      }

      const nextEvents = events.map((event) =>
        event.id === editingSchedule.id ? nextEvent : event
      );
      updateCalendarState(nextEvents);
      setEditingSchedule(null);
    } catch (error) {
      console.error('Failed to update schedule.', {
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        rawError: error,
      });
      alert(`일정 수정 실패: ${getApiErrorMessage(error)}`);
    }
  };

  const handleDeleteSchedule = async (scheduleId) => {
    if (!scheduleId) {
      return;
    }

    if (!window.confirm('이 일정을 캘린더에서 삭제하시겠습니까?')) {
      return;
    }

    try {
      await deleteSchedule(scheduleId);
      setRefreshKey((prev) => prev + 1);
    } catch (error) {
      console.error('Failed to delete schedule.', {
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        rawError: error,
      });
      alert(`일정 삭제 실패: ${getApiErrorMessage(error)}`);
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
      case 'call': return 'bg-orange-100 text-orange-700 border-orange-200';
      case 'meeting': return 'bg-blue-100 text-blue-700 border-blue-200';
      case 'email': return 'bg-green-100 text-green-700 border-green-200';
      case 'task': return 'bg-purple-100 text-purple-700 border-purple-200';
      default: return 'bg-gray-100 text-gray-700 border-gray-200';
    }
  };

  const getTypeDot = (type) => {
    switch (type) {
      case 'call': return 'bg-orange-500';
      case 'meeting': return 'bg-blue-500';
      case 'email': return 'bg-green-500';
      case 'task': return 'bg-purple-500';
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
    return `${toDateInput(date)}T${toTimeInput(date)}:${pad(date.getSeconds())}`;
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
      console.error('Failed to create sample source.', error);
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
        memo: '프론트와 백엔드 연동 확인용 일정',
      });

      setTestPanelMessage('일정 예시 데이터가 생성되었습니다.');
      setRefreshKey((prev) => prev + 1);
    } catch (error) {
      console.error('Failed to create sample schedule.', error);
      setTestPanelMessage(`일정 예시 데이터 생성 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setIsCreatingTestData(false);
    }
  };

  return (
    <div className="p-6 flex gap-6 h-full">
      <div className="w-72 space-y-5 flex flex-col shrink-0">
        {false && isDev && (
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

        <SchedulePanel
          title="오늘의 일정"
          items={filteredTodaySchedule}
          getTypeColor={getTypeColor}
          getTypeDot={getTypeDot}
          getScheduleStatusColor={getScheduleStatusColor}
          getScheduleStatusLabel={getScheduleStatusLabel}
          onDelete={handleDeleteSchedule}
        />

        <SchedulePanel
          title="다가오는 일정"
          items={filteredUpcomingSchedule}
          getTypeColor={getTypeColor}
          getTypeDot={getTypeDot}
          getScheduleStatusColor={getScheduleStatusColor}
          getScheduleStatusLabel={getScheduleStatusLabel}
          onDelete={handleDeleteSchedule}
        />
      </div>

      <div className="flex-1 min-w-0">
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
                          onChange={(event) => setUserSearch(event.target.value)}
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
                          users.every(user => user.selected) ? 'bg-blue-500 border-blue-500' : 'border-gray-300'
                        }`}>
                          {users.every(user => user.selected) && <Check className="w-3 h-3 text-white" />}
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

          <div className="flex-1 flex flex-col min-h-0">
            <div className="grid grid-cols-7 gap-1 mb-2">
              {['일', '월', '화', '수', '목', '금', '토'].map(day => (
                <div key={day} className="text-center text-xs text-gray-500 py-2">{day}</div>
              ))}
            </div>

            <div className="grid grid-cols-7 gap-1 flex-1 min-h-0">
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
                              className={`text-xs px-1.5 py-0.5 rounded truncate border ${getTypeColor(event.type)}`}
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
                          onChange={(event) => setEditingSchedule({ ...editingSchedule, title: event.target.value })}
                          className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                        />
                      </div>
                      <div className="grid grid-cols-3 gap-4">
                        <div>
                          <label className="block text-xs text-gray-600 mb-1.5">날짜</label>
                          <input
                            type="date"
                            value={editingSchedule.dateValue}
                            onChange={(event) => setEditingSchedule({ ...editingSchedule, dateValue: event.target.value })}
                            className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          />
                        </div>
                        <div>
                          <label className="block text-xs text-gray-600 mb-1.5">시간</label>
                          <input
                            type="time"
                            value={editingSchedule.timeValue}
                            onChange={(event) => setEditingSchedule({ ...editingSchedule, timeValue: event.target.value })}
                            className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          />
                        </div>
                        <div>
                          <label className="block text-xs text-gray-600 mb-1.5">업무 유형</label>
                          <select
                            value={editingSchedule.type}
                            onChange={(event) => setEditingSchedule({ ...editingSchedule, type: event.target.value })}
                            className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          >
                            <option value="meeting">미팅</option>
                            <option value="call">전화</option>
                            <option value="task">업무</option>
                            <option value="email">이메일</option>
                          </select>
                        </div>
                      </div>
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <label className="block text-xs text-gray-600 mb-1.5">보낸 사람</label>
                          <input
                            type="text"
                            value={editingSchedule.sender}
                            onChange={(event) => setEditingSchedule({ ...editingSchedule, sender: event.target.value })}
                            className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          />
                        </div>
                        <div>
                          <label className="block text-xs text-gray-600 mb-1.5">참석자</label>
                          <input
                            type="text"
                            value={editingSchedule.attendees}
                            onChange={(event) => setEditingSchedule({ ...editingSchedule, attendees: event.target.value })}
                            className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          />
                        </div>
                      </div>
                      <div>
                        <label className="block text-xs text-gray-600 mb-1.5">설명</label>
                        <textarea
                          value={editingSchedule.description}
                          onChange={(event) => setEditingSchedule({ ...editingSchedule, description: event.target.value })}
                          className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
                          rows={4}
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
                            <span className={`px-2 py-0.5 text-xs rounded border ${getTypeColor(event.type)}`}>
                              {getTypeLabel(event.type)}
                            </span>
                            <span className="text-sm text-gray-600">{event.time}</span>
                          </div>
                          <h4 className="text-base text-gray-800 mb-1">{event.title}</h4>
                          <p className="text-xs text-gray-500">담당자: {event.userName}</p>
                        </div>
                        {event.userId === CURRENT_USER_ID && (
                          <div className="flex items-center gap-1">
                            {event.scheduleId && event.status !== 'CANCELED' && (
                              <button
                                onClick={() => handleDeleteSchedule(event.scheduleId)}
                                className="px-2 py-1 text-xs border border-red-200 text-red-600 rounded hover:bg-red-50"
                              >
                                삭제
                              </button>
                            )}
                            <button
                              onClick={() => handleEditSchedule(event)}
                              className="p-2 hover:bg-gray-100 rounded"
                            >
                              <Edit2 className="w-4 h-4 text-gray-600" />
                            </button>
                          </div>
                        )}
                      </div>
                      <div className="space-y-2 text-sm">
                        <div>
                          <span className="text-xs text-gray-500">보낸 사람</span>
                          <p className="text-gray-700">{event.sender || '해당 없음'}</p>
                        </div>
                        <div>
                          <span className="text-xs text-gray-500">참석자</span>
                          <p className="text-gray-700">{event.attendees || '해당 없음'}</p>
                        </div>
                        <div>
                          <span className="text-xs text-gray-500">설명</span>
                          <p className="text-gray-700 whitespace-pre-wrap break-words">{event.description || '해당 없음'}</p>
                        </div>
                      </div>
                      {event.userId !== CURRENT_USER_ID && (
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

function SchedulePanel({
  title,
  items,
  getTypeColor,
  getTypeDot,
  getScheduleStatusColor,
  getScheduleStatusLabel,
  onDelete,
}) {
  return (
    <div className="flex-1 min-h-0">
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-5 h-full flex flex-col">
        <h3 className="text-sm text-gray-700 mb-4">{title}</h3>
        <div className="space-y-2.5 overflow-y-auto flex-1">
          {items.length === 0 ? (
            <p className="text-xs text-gray-500">표시할 일정이 없습니다.</p>
          ) : (
            items.map(item => (
              <ScheduleListItem
                key={item.id}
                item={item}
                getTypeColor={getTypeColor}
                getTypeDot={getTypeDot}
                getScheduleStatusColor={getScheduleStatusColor}
                getScheduleStatusLabel={getScheduleStatusLabel}
                onDelete={onDelete}
              />
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function ScheduleListItem({ item, getTypeColor, getTypeDot, getScheduleStatusColor, getScheduleStatusLabel, onDelete }) {
  return (
    <div className={`flex items-start gap-2.5 p-2.5 border rounded-md ${getTypeColor(item.type)}`}>
      <div className={`w-1.5 h-1.5 rounded-full mt-1.5 flex-shrink-0 ${getTypeDot(item.type)}`}></div>
      <div className="flex-1 min-w-0">
        <div className="text-sm text-gray-700 break-words">
          {item.date && <span className="text-xs text-gray-500 mr-2">{item.date}</span>}
          {item.time} - {item.title}
        </div>
        <div className="flex items-center gap-2 mt-1">
          <div className="text-xs text-gray-500">{item.userName}</div>
          <span className={`px-2 py-0.5 text-[11px] rounded-full border ${getScheduleStatusColor(item.status)}`}>
            {getScheduleStatusLabel(item.status)}
          </span>
          {item.scheduleId && item.status !== 'CANCELED' && (
            <button
              onClick={() => onDelete(item.scheduleId)}
              className="ml-auto text-[11px] text-red-600 hover:text-red-700"
            >
              삭제
            </button>
          )}
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
