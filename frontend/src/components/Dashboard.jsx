import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronLeft, ChevronRight, X, Edit2, Save } from 'lucide-react';
import { deleteSchedule, getSchedules, recordScheduleOutcome, updateSchedule } from '../api/schedules';
import { getSources } from '../api/sources';
import { getAnalysesBySource } from '../api/analyses';
import { getApiErrorMessage } from '../api/errors';

const MEETING_OUTCOMES = [
  { value: 'SUCCESS', label: '잘됨', className: 'border-emerald-200 text-emerald-700 hover:bg-emerald-50', activeClassName: 'bg-emerald-500 border-emerald-500 text-white' },
  { value: 'ON_HOLD', label: '보류', className: 'border-amber-200 text-amber-700 hover:bg-amber-50', activeClassName: 'bg-amber-500 border-amber-500 text-white' },
  { value: 'LOST', label: '무산', className: 'border-rose-200 text-rose-700 hover:bg-rose-50', activeClassName: 'bg-rose-500 border-rose-500 text-white' },
];

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

// 일정 memo를 구/신 포맷 모두 지원해 사람이 읽기 좋은 필드로 분해한다.
// 구 포맷: "summary: ...", "nextAction: ...", "actionReason: ..."
// 신 포맷: "보낸 사람: ...", "참석자: ...", "<요약>", "다음 행동: ..."
function parseScheduleMemo(memo = '') {
  const result = { sender: '', attendees: '', type: '', summary: '', nextAction: '' };

  memo.split('\n').forEach((raw) => {
    const line = raw.trim();
    if (!line) {
      return;
    }

    const matched = line.match(/^([^:：]{1,12})[:：]\s*(.*)$/);
    const label = matched?.[1]?.trim();
    const value = matched?.[2]?.trim() ?? '';

    switch (label) {
      case '보낸 사람':
        result.sender = value;
        break;
      case '고객사':
        if (!result.sender) result.sender = value;
        break;
      case '참석자':
        result.attendees = value;
        break;
      case '업무 유형':
        result.type = value;
        break;
      case 'summary':
      case '요약':
        result.summary = value;
        break;
      case 'nextAction':
      case '다음 행동':
        result.nextAction = value;
        break;
      case 'actionReason':
      case '판단 근거':
        break; // 내부 판단 근거는 사용자에게 노출하지 않음
      default:
        if (!result.summary) {
          result.summary = line;
        }
    }
  });

  // 요약 끝에 "/ 참석자: ..." 꼬리가 붙어 있으면 분리한다.
  const tailAttendees = result.summary.match(/\/\s*참석자[:：]\s*([^/\n]+)$/);
  if (tailAttendees) {
    if (!result.attendees) {
      result.attendees = tailAttendees[1].trim();
    }
    result.summary = result.summary.replace(/\/\s*참석자[:：]\s*[^/\n]+$/, '').trim();
  }

  result.summarySegments = result.summary
    ? result.summary.split('/').map((part) => part.trim()).filter(Boolean)
    : [];

  return result;
}

function normalizeScheduleStatus(status) {
  return status || 'SCHEDULED';
}

function formatMonthDay(dateValue = '') {
  const [, month, day] = dateValue.split('-');
  return month && day ? `${Number(month)}/${Number(day)}` : '';
}

function isPastEvent(event) {
  const startsAt = new Date(`${event.dateValue}T${event.timeValue || '00:00'}:00`);
  return !Number.isNaN(startsAt.getTime()) && startsAt <= new Date();
}

// 미팅이 끝난 뒤 결과를 남긴다. 쌓인 결과는 나중에 딜 성사 예측의 정답 데이터가 된다.
function MeetingOutcomePicker({ outcome, saving, onSelect }) {
  return (
    <div className="mt-3 pt-3 border-t border-slate-100">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400 mb-1.5">미팅 결과</p>
      <div className="flex gap-2">
        {MEETING_OUTCOMES.map((option) => (
          <button
            key={option.value}
            type="button"
            disabled={saving}
            onClick={() => onSelect(option.value)}
            className={`flex-1 rounded-lg border px-3 py-1.5 text-xs font-semibold transition disabled:opacity-60 ${
              outcome === option.value ? option.activeClassName : option.className
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>
      {!outcome && <p className="mt-1.5 text-[11px] text-slate-400">미팅이 어떻게 됐는지 기록해 주세요.</p>}
    </div>
  );
}

function isSameCalendarDate(left, right) {
  return (
    left.getFullYear() === right.getFullYear()
    && left.getMonth() === right.getMonth()
    && left.getDate() === right.getDate()
  );
}

function getLatestAnalysis(analyses) {
  if (!Array.isArray(analyses) || analyses.length === 0) {
    return null;
  }

  return [...analyses].sort((left, right) => (right.analysisId || 0) - (left.analysisId || 0))[0];
}

function createBriefingFromData(schedules = [], sources = [], latestAnalyses = []) {
  const now = new Date();
  const tomorrow = new Date(now);
  tomorrow.setDate(now.getDate() + 1);

  const activeSchedules = schedules.filter((schedule) => (
    schedule.scheduleDateTime && normalizeScheduleStatus(schedule.status) !== 'CANCELED'
  ));

  const todaySchedules = activeSchedules.filter((schedule) => (
    isSameCalendarDate(new Date(schedule.scheduleDateTime), now)
  ));
  const tomorrowSchedules = activeSchedules.filter((schedule) => (
    isSameCalendarDate(new Date(schedule.scheduleDateTime), tomorrow)
  ));
  const pendingAnalyses = latestAnalyses.filter((analysis) => analysis?.status === 'ANALYZED');
  const salesMailCount = latestAnalyses.filter((analysis) => (
    analysis?.businessType === 'SALES_ACTIVITY'
    || ['CREATE', 'UPDATE', 'CANCEL', 'CONFIRM'].includes(analysis?.actionType)
  )).length;
  const urgentAnalyses = pendingAnalyses.filter((analysis) => ['UPDATE', 'CANCEL'].includes(analysis?.actionType));
  const priorityAnalysis = [...urgentAnalyses].sort((left, right) => (
    (right.analysisId || 0) - (left.analysisId || 0)
  ))[0];
  const firstTodaySchedule = [...todaySchedules].sort((left, right) => (
    new Date(left.scheduleDateTime) - new Date(right.scheduleDateTime)
  ))[0];

  const messages = [];
  if (todaySchedules.length > 0) {
    const firstScheduleTime = firstTodaySchedule
      ? new Date(firstTodaySchedule.scheduleDateTime).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
      : null;
    messages.push(
      firstTodaySchedule
        ? `오늘 ${firstScheduleTime} ${firstTodaySchedule.title} 일정이 예정되어 있습니다.`
        : `오늘 예정된 일정 ${todaySchedules.length}건이 있습니다.`
    );
  }

  if (tomorrowSchedules.length > 0) {
    messages.push(`내일 예정된 일정 ${tomorrowSchedules.length}건도 미리 확인해 주세요.`);
  }

  if (pendingAnalyses.length > 0) {
    messages.push(`승인 대기 중인 AI 분석 결과가 ${pendingAnalyses.length}건 있습니다.`);
  }

  if (salesMailCount > 0) {
    messages.push(`최근 수집 메일 ${sources.length}건 중 영업 관련 메일 ${salesMailCount}건이 감지되었습니다.`);
  }

  if (urgentAnalyses.length > 0) {
    messages.push(`일정 변경/취소 요청 ${urgentAnalyses.length}건은 우선 확인하는 것을 권장합니다.`);
  }

  if (messages.length === 0) {
    messages.push('오늘은 긴급하게 처리할 영업 활동이 없습니다.');
  }

  return {
    isLoading: false,
    errorMessage: '',
    todayCount: todaySchedules.length,
    pendingCount: pendingAnalyses.length,
    urgentCount: urgentAnalyses.length,
    prioritySourceId: priorityAnalysis?.sourceId || null,
    messages,
  };
}

export function Dashboard() {
  const navigate = useNavigate();
  const [currentMonth, setCurrentMonth] = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(null);
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [editingSchedule, setEditingSchedule] = useState(null);
  const [calendarError, setCalendarError] = useState('');
  const [outcomeSavingId, setOutcomeSavingId] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [events, setEvents] = useState([]);
  const [todaySchedule, setTodaySchedule] = useState([]);
  const [upcomingSchedule, setUpcomingSchedule] = useState([]);
  const [salesBriefing, setSalesBriefing] = useState({
    isLoading: true,
    errorMessage: '',
    todayCount: 0,
    pendingCount: 0,
    urgentCount: 0,
    prioritySourceId: null,
    messages: ['브리핑을 준비하고 있습니다.'],
  });
  const modalRef = useRef(null);

  const toSchedulePanelItem = useCallback((event) => ({
    id: event.id,
    scheduleId: event.scheduleId,
    time: event.time,
    title: event.title,
    type: event.type,
    date: formatMonthDay(event.dateValue),
    status: normalizeScheduleStatus(event.status),
  }), []);

  const updateCalendarState = useCallback((nextEvents) => {
    const sortedEvents = [...nextEvents].sort((a, b) => (
      `${a.dateValue}T${a.timeValue}`.localeCompare(`${b.dateValue}T${b.timeValue}`)
    ));

    setEvents(sortedEvents);

    const todayValue = toDateInput(new Date());
    const activeEvents = sortedEvents.filter((event) => event.status !== 'CANCELED');
    setTodaySchedule(activeEvents.filter((event) => event.dateValue === todayValue).map(toSchedulePanelItem));
    setUpcomingSchedule(
      activeEvents
        .filter((event) => event.dateValue > todayValue)
        .slice(0, 8)
        .map(toSchedulePanelItem)
    );
  }, [toSchedulePanelItem]);

  const syncSchedulesToCalendar = useCallback((schedules) => {
    if (!Array.isArray(schedules)) {
      updateCalendarState([]);
      return;
    }

    const backendEvents = schedules
      .filter((schedule) => schedule.scheduleDateTime)
      .filter((schedule) => schedule.status !== 'CANCELED')
      .map((schedule) => {
        const scheduleDate = new Date(schedule.scheduleDateTime);
        const memo = schedule.memo || '';
        const parsed = parseScheduleMemo(memo);
        const type = parsed.type || 'meeting';

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
          sender: parsed.sender,
          attendees: parsed.attendees,
          description: parsed.summary,
          summarySegments: parsed.summarySegments,
          nextAction: parsed.nextAction,
          status: normalizeScheduleStatus(schedule.status),
          outcome: schedule.outcome,
        };
      });

    updateCalendarState(backendEvents);
  }, [updateCalendarState]);

  useEffect(() => {
    const fetchDashboardData = async () => {
      setSalesBriefing((prev) => ({
        ...prev,
        isLoading: true,
        errorMessage: '',
      }));

      let schedules = [];
      try {
        schedules = await getSchedules({ size: 100 });
        setCalendarError('');
        syncSchedulesToCalendar(schedules);
      } catch (error) {
        console.error('Failed to fetch schedules:', error);
        setCalendarError(`일정을 불러오지 못했습니다: ${getApiErrorMessage(error)}`);
        syncSchedulesToCalendar([]);
      }

      let sources = [];
      try {
        sources = await getSources({ size: 30 });
      } catch (error) {
        console.error('Failed to fetch sources:', error);
      }

      const analysisResults = await Promise.allSettled(
        sources.slice(0, 30).map(async (source) => {
          const analyses = await getAnalysesBySource(source.sourceId);
          return getLatestAnalysis(analyses);
        })
      );
      const latestAnalyses = analysisResults
        .filter((result) => result.status === 'fulfilled' && result.value)
        .map((result) => result.value);

      setSalesBriefing(createBriefingFromData(schedules, sources, latestAnalyses));
    };

    fetchDashboardData();
  }, [refreshKey, syncSchedulesToCalendar]);

  useEffect(() => {
    const handleClickOutside = (event) => {
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

  // 달력에는 보고 있는 달의 일정만 표시한다(다른 달의 같은 날짜 일정이 섞이지 않게).
  const visibleMonthPrefix = `${currentMonth.getFullYear()}-${pad(currentMonth.getMonth() + 1)}-`;
  const monthEvents = events.filter((event) => event.dateValue?.startsWith(visibleMonthPrefix));

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

  const getEventsForDate = (date) => monthEvents.filter(e => e.date === date);

  const handleDateClick = (date) => {
    const dayEvents = getEventsForDate(date);
    if (dayEvents.length > 0) {
      setSelectedDate(date);
      setShowScheduleModal(true);
    }
  };

  const handleEditSchedule = (schedule) => {
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

  const handleRecordOutcome = async (scheduleId, outcome) => {
    try {
      setOutcomeSavingId(scheduleId);
      await recordScheduleOutcome(scheduleId, outcome);
      setRefreshKey((prev) => prev + 1);
    } catch (error) {
      console.error('Failed to record meeting outcome:', error);
      alert(`미팅 결과 저장 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setOutcomeSavingId(null);
    }
  };

  return (
    <div className="p-6 flex gap-6 h-full">
      <div className="w-72 space-y-3 flex flex-col shrink-0">
        <SalesBriefingCard
          briefing={salesBriefing}
          onOpenPriority={() => navigate('/history?filter=priority')}
        />

        <SchedulePanel
          title="오늘의 일정"
          items={todaySchedule}
          getTypeColor={getTypeColor}
          getTypeDot={getTypeDot}
          getScheduleStatusColor={getScheduleStatusColor}
          getScheduleStatusLabel={getScheduleStatusLabel}
          onDelete={handleDeleteSchedule}
        />

        <SchedulePanel
          title="다가오는 일정"
          items={upcomingSchedule}
          getTypeColor={getTypeColor}
          getTypeDot={getTypeDot}
          getScheduleStatusColor={getScheduleStatusColor}
          getScheduleStatusLabel={getScheduleStatusLabel}
          onDelete={handleDeleteSchedule}
        />
      </div>

      <div className="flex-1 min-w-0">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-6 h-full flex flex-col">
          <div className="mb-5 flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2">
                <button onClick={handlePreviousMonth} className="p-1.5 hover:bg-slate-100 rounded-lg">
                  <ChevronLeft className="w-4 h-4 text-slate-600" />
                </button>
                <h3 className="text-base font-bold text-slate-800 min-w-[120px] text-center" style={{ fontFamily: 'var(--display)' }}>{monthName}</h3>
                <button onClick={handleNextMonth} className="p-1.5 hover:bg-gray-100 rounded">
                  <ChevronRight className="w-4 h-4 text-gray-600" />
                </button>
              </div>

              {calendarError && (
                <span className="text-xs text-red-600">{calendarError}</span>
              )}
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
          <div ref={modalRef} className="bg-white rounded-2xl w-full max-w-2xl max-h-[80vh] overflow-auto border border-slate-200 shadow-2xl text-left">
            <div className="sticky top-0 z-10 bg-white/90 backdrop-blur border-b border-slate-200 px-6 py-4 flex items-center justify-between">
              <h3 className="text-base font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>
                {currentMonth.getFullYear()}년 {currentMonth.getMonth() + 1}월 {selectedDate}일 일정
              </h3>
              <button
                onClick={() => {
                  setShowScheduleModal(false);
                  setSelectedDate(null);
                  setEditingSchedule(null);
                }}
                className="p-1.5 rounded-lg hover:bg-slate-100"
              >
                <X className="w-5 h-5 text-slate-500" />
              </button>
            </div>

            <div className="p-6 space-y-4">
              {getEventsForDate(selectedDate).map((event) => (
                <div key={event.id} className="border border-slate-200 rounded-xl p-4 shadow-sm">
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
                            <span className={`px-2 py-0.5 text-xs rounded-md border ${getTypeColor(event.type)}`}>
                              {getTypeLabel(event.type)}
                            </span>
                            <span className="text-sm font-medium text-slate-500">{event.time}</span>
                          </div>
                          <h4 className="text-base font-bold text-slate-900 mb-1" style={{ fontFamily: 'var(--display)' }}>{event.title}</h4>
                        </div>
                        <div className="flex items-center gap-1">
                          {event.scheduleId && event.status !== 'CANCELED' && (
                            <button
                              onClick={() => handleDeleteSchedule(event.scheduleId)}
                              className="px-2 py-1 text-xs border border-red-200 text-red-600 rounded-lg hover:bg-red-50"
                            >
                              삭제
                            </button>
                          )}
                          <button
                            onClick={() => handleEditSchedule(event)}
                            className="p-2 rounded-lg hover:bg-slate-100"
                          >
                            <Edit2 className="w-4 h-4 text-slate-500" />
                          </button>
                        </div>
                      </div>

                      <div className="space-y-3">
                        <div className="grid grid-cols-2 gap-3">
                          {event.sender && (
                            <DetailField label="보낸 사람" value={event.sender} />
                          )}
                          <DetailField label="참석자" value={event.attendees || '해당 없음'} />
                        </div>

                        {event.summarySegments && event.summarySegments.length > 0 ? (
                          <div>
                            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400 mb-1.5">요약</p>
                            <div className="flex flex-wrap gap-1.5">
                              {event.summarySegments.map((segment, index) => (
                                <span
                                  key={`${event.id}-seg-${index}`}
                                  className="rounded-lg border border-slate-100 bg-slate-50 px-2.5 py-1 text-xs font-medium text-slate-600"
                                >
                                  {segment}
                                </span>
                              ))}
                            </div>
                          </div>
                        ) : event.description ? (
                          <div>
                            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400 mb-1">설명</p>
                            <p className="text-sm text-slate-600 whitespace-pre-wrap break-words">{event.description}</p>
                          </div>
                        ) : null}

                        {event.nextAction && (
                          <div className="rounded-xl bg-gradient-to-br from-blue-50/80 to-violet-50/50 px-3 py-2.5">
                            <p className="flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wide text-blue-500">
                              → 다음 행동
                            </p>
                            <p className="mt-0.5 text-sm text-slate-600">{event.nextAction}</p>
                          </div>
                        )}
                      </div>

                      {event.scheduleId && event.status !== 'CANCELED' && isPastEvent(event) && (
                        <MeetingOutcomePicker
                          outcome={event.outcome}
                          saving={outcomeSavingId === event.scheduleId}
                          onSelect={(outcome) => handleRecordOutcome(event.scheduleId, outcome)}
                        />
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

function SalesBriefingCard({ briefing, onOpenPriority }) {
  return (
    <div className="relative overflow-hidden bg-white rounded-2xl shadow-sm border border-slate-200 p-4 text-left">
      <div className="absolute -right-10 -top-10 h-28 w-28 rounded-full bg-gradient-to-br from-blue-400/15 to-violet-400/10 blur-2xl" />
      <div className="relative flex items-start justify-between gap-3 mb-3">
        <div>
          <p className="inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600 mb-0.5">✨ AI 브리핑</p>
          <h3 className="text-sm font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>오늘의 영업 브리핑</h3>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-2 mb-3">
        <BriefingMetric label="오늘 일정" value={briefing.todayCount} colorClass="text-blue-700 bg-blue-50" />
        <BriefingMetric label="승인 대기" value={briefing.pendingCount} colorClass="text-amber-700 bg-amber-50" />
        <BriefingMetric label="우선 확인" value={briefing.urgentCount} colorClass="text-red-700 bg-red-50" />
      </div>

      {briefing.isLoading ? (
        <p className="text-sm text-gray-500">브리핑을 준비하고 있습니다.</p>
      ) : briefing.errorMessage ? (
        <p className="text-sm text-red-600">{briefing.errorMessage}</p>
      ) : (
        <ul className="space-y-1.5">
          {briefing.messages.slice(0, 2).map((message) => (
            <li key={message} className="flex gap-2 text-xs text-gray-700 leading-relaxed">
              <span className="mt-1.5 h-1.5 w-1.5 rounded-full bg-blue-500 shrink-0" />
              <span>{message}</span>
            </li>
          ))}
        </ul>
      )}

      {!briefing.isLoading && briefing.prioritySourceId && (
        <button
          type="button"
          onClick={onOpenPriority}
          className="mt-3 w-full rounded-md bg-red-50 border border-red-200 px-3 py-2 text-xs font-semibold text-red-700 hover:bg-red-100 transition-colors"
        >
          우선 확인 메일 보기
        </button>
      )}
    </div>
  );
}

function BriefingMetric({ label, value, colorClass }) {
  return (
    <div className={`rounded-lg px-2 py-1.5 text-center ${colorClass}`}>
      <p className="text-base font-bold leading-none">{value}</p>
      <p className="text-[10px] mt-0.5 font-medium">{label}</p>
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
      <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-5 h-full flex flex-col">
        <h3 className="text-sm font-bold text-slate-800 mb-4" style={{ fontFamily: 'var(--display)' }}>{title}</h3>
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

function DetailField({ label, value }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50/60 px-3 py-2">
      <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-0.5 text-sm font-medium text-slate-700 break-words">{value}</p>
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
