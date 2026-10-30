import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Settings, Search, LayoutDashboard, ClipboardList, UsersRound, ChevronDown, ChevronRight, Wind, KanbanSquare, FlaskConical, NotebookPen, ClipboardCheck, ListChecks } from 'lucide-react';
import jandiLogo from '../assets/잔디 로고 이미지 .jpg';
import gmailLogo from '../assets/gmail로고 이미지 spaced.png';
import { getMe } from '../api/auth';
import { getIntegrations } from '../api/integrations';
import { getDemoStatus } from '../api/demo';
import { getSchedules } from '../api/schedules';
import { getMeetingNotes } from '../api/meetingNotes';
import { ReminderModal } from './ReminderModal';
import { ActivityProvider, ActivityToasts, AnalysisProgressChip } from './ActivityFeed';
import { useActivityFeed } from './useActivityFeed';

const THIRTY_MINUTES_STORAGE_PREFIX = 'schedule-30m-shown';

function formatDate(date) {
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

function formatTime(date) {
  return date.toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function isSameDate(left, right) {
  return (
    left.getFullYear() === right.getFullYear()
    && left.getMonth() === right.getMonth()
    && left.getDate() === right.getDate()
  );
}

function toScheduleDateTime(value) {
  if (!value) {
    return null;
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function toReminderItem(schedule) {
  const dateTime = toScheduleDateTime(schedule.scheduleDateTime);
  if (!dateTime) {
    return null;
  }

  return {
    id: schedule.scheduleId,
    title: schedule.title || '일정',
    dateTime: schedule.scheduleDateTime,
    date: formatDate(dateTime),
    time: formatTime(dateTime),
    type: '일정',
    content: schedule.memo || '',
  };
}

function userInitial(name, email) {
  const source = (name || email || 'U').trim();
  return source.charAt(0).toUpperCase();
}

export function DashboardLayout({ children }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [companyName, setCompanyName] = useState(() => localStorage.getItem('salesmapCompany') || '');
  const [gmailCalendarConnected, setGmailCalendarConnected] = useState(false);
  const [currentUser, setCurrentUser] = useState(null);
  const [demoEnabled, setDemoEnabled] = useState(false);
  const [draftNoteCount, setDraftNoteCount] = useState(0);
  const [showReminder, setShowReminder] = useState(false);
  const [currentReminder, setCurrentReminder] = useState(null);
  const [reminderQueue, setReminderQueue] = useState([]);
  const loginSummaryShownRef = useRef(false);
  const activity = useActivityFeed();

  const enqueueReminder = useCallback((reminder) => {
    setReminderQueue((prev) => {
      const exists = prev.some((item) => item.id === reminder.id);
      if (exists || currentReminder?.id === reminder.id) {
        return prev;
      }

      return [...prev, reminder];
    });
  }, [currentReminder]);

  useEffect(() => {
    if (!currentReminder && reminderQueue.length > 0) {
      const [nextReminder, ...rest] = reminderQueue;
      setCurrentReminder(nextReminder);
      setReminderQueue(rest);
      setShowReminder(true);
    }
  }, [currentReminder, reminderQueue]);

  useEffect(() => {
    const timerId = window.setTimeout(() => {
      setCompanyName(localStorage.getItem('salesmapCompany') || '');
    }, 0);

    return () => window.clearTimeout(timerId);
  }, [location]);

  useEffect(() => {
    let ignore = false;

    const fetchIntegrationStatus = async () => {
      try {
        const integrations = await getIntegrations();
        if (ignore) {
          return;
        }

        const gmailConnected = integrations.some(
          (integration) => integration.provider === 'GMAIL' && integration.status === 'CONNECTED'
        );
        setGmailCalendarConnected(gmailConnected);
      } catch (error) {
        console.error('Failed to fetch integration status:', error);
        if (!ignore) {
          setGmailCalendarConnected(false);
        }
      }
    };

    fetchIntegrationStatus();

    return () => {
      ignore = true;
    };
  }, [location]);

  useEffect(() => {
    let ignore = false;

    const fetchUser = async () => {
      try {
        const me = await getMe();
        if (!ignore) {
          setCurrentUser(me);
        }
      } catch (error) {
        console.error('Failed to fetch current user:', error);
      }
    };

    fetchUser();
    getDemoStatus()
      .then((status) => {
        if (!ignore) {
          setDemoEnabled(Boolean(status?.enabled));
        }
      })
      .catch(() => {});

    return () => {
      ignore = true;
    };
  }, []);

  // 확인 대기함 배지: 아직 확정하지 않은 회의록 수 (화면을 옮기거나 회의록을 확정할 때 갱신)
  useEffect(() => {
    let ignore = false;
    const refreshDraftNoteCount = () => {
      getMeetingNotes('DRAFT')
        .then((notes) => {
          if (!ignore) {
            setDraftNoteCount(notes.length);
          }
        })
        .catch(() => {});
    };

    refreshDraftNoteCount();
    window.addEventListener('meeting-notes:changed', refreshDraftNoteCount);

    return () => {
      ignore = true;
      window.removeEventListener('meeting-notes:changed', refreshDraftNoteCount);
    };
  }, [location]);

  const fetchSchedulesForReminder = useCallback(async () => {
    try {
      return await getSchedules({ size: 100 });
    } catch (error) {
      console.error('Failed to fetch schedules for reminders:', error);
      return [];
    }
  }, []);

  useEffect(() => {
    let ignore = false;

    const showLoginSummary = async () => {
      if (loginSummaryShownRef.current) {
        return;
      }

      const today = new Date();
      const tomorrow = new Date(today);
      tomorrow.setDate(today.getDate() + 1);

      const schedules = await fetchSchedulesForReminder();
      if (ignore) {
        return;
      }

      const items = schedules
        .filter((schedule) => schedule.status !== 'CANCELED')
        .map(toReminderItem)
        .filter(Boolean)
        .filter((item) => {
          const itemDate = toScheduleDateTime(item.dateTime);
          return itemDate && (isSameDate(itemDate, today) || isSameDate(itemDate, tomorrow));
        })
        .sort((left, right) => new Date(left.dateTime) - new Date(right.dateTime));

      if (items.length > 0) {
        loginSummaryShownRef.current = true;
        enqueueReminder({
          id: `login-summary:${today.toISOString().slice(0, 10)}`,
          kind: 'SUMMARY',
          label: '오늘과 내일의 일정',
          title: `오늘과 내일 일정 ${items.length}건이 있습니다.`,
          items,
        });
      }
    };

    showLoginSummary();

    return () => {
      ignore = true;
    };
  }, [enqueueReminder, fetchSchedulesForReminder]);

  useEffect(() => {
    let ignore = false;

    const checkThirtyMinuteReminders = async () => {
      const schedules = await fetchSchedulesForReminder();
      if (ignore) {
        return;
      }

      const now = new Date();
      schedules
        .filter((schedule) => schedule.status !== 'CANCELED')
        .forEach((schedule) => {
          const item = toReminderItem(schedule);
          if (!item) {
            return;
          }

          const scheduleDate = toScheduleDateTime(item.dateTime);
          const minutesLeft = (scheduleDate.getTime() - now.getTime()) / (1000 * 60);
          const storageKey = `${THIRTY_MINUTES_STORAGE_PREFIX}:${schedule.scheduleId}:${item.dateTime}`;

          if (minutesLeft > 0 && minutesLeft <= 30 && !sessionStorage.getItem(storageKey)) {
            sessionStorage.setItem(storageKey, 'true');
            enqueueReminder({
              ...item,
              id: storageKey,
              kind: 'THIRTY_MINUTES',
              label: '30분 전 알림',
            });
          }
        });
    };

    checkThirtyMinuteReminders();
    const intervalId = window.setInterval(checkThirtyMinuteReminders, 60 * 1000);

    return () => {
      ignore = true;
      window.clearInterval(intervalId);
    };
  }, [enqueueReminder, fetchSchedulesForReminder]);

  const handleSalesmapClick = () => {
    if (!gmailCalendarConnected) {
      navigate('/settings');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    navigate('/login', { replace: true });
  };

  const menuItems = [
    { path: '/meeting-notes', icon: NotebookPen, label: '미팅 기록' },
    { path: '/meeting-notes/inbox', icon: ClipboardCheck, label: '확인 대기함', badge: draftNoteCount },
    { path: '/tasks', icon: ListChecks, label: '할 일' },
    { path: '/pipeline', icon: KanbanSquare, label: '딜 파이프라인' },
    { path: '/customers', icon: UsersRound, label: '고객 타임라인' },
    { path: '/dashboard', icon: LayoutDashboard, label: '대시보드' },
    { path: '/history', icon: ClipboardList, label: '처리 이력' },
    { path: '/settings', icon: Settings, label: '설정' },
  ];

  const pageTitle = (() => {
    if (location.pathname.startsWith('/messages')) {
      return '메일 상세 및 AI 분석';
    }
    if (location.pathname.startsWith('/settings/gmail/callback')) {
      return 'Gmail 연동';
    }
    if (/^\/meeting-notes\/mn_/.test(location.pathname)) {
      return '회의록 확인';
    }
    return menuItems.find(item => item.path === location.pathname)?.label || '대시보드';
  })();

  const isActive = (path) => location.pathname === path;
  const displayName = currentUser?.name || currentUser?.email || '사용자';
  const avatarText = userInitial(currentUser?.name, currentUser?.email);
  // Salesmap 반영은 Google Calendar 양방향 연동으로 이루어지므로 Gmail·Calendar 연결 여부가 곧 연동 상태다.
  const topSalesmapConnected = gmailCalendarConnected;
  const topCompanyName = topSalesmapConnected ? (companyName || '연동된 회사') : 'Salesmap 연동';

  return (
    <>
      <div className="h-screen flex bg-gradient-to-b from-[#F6F8FC] to-[#EEF2F9] overflow-hidden">
        <div className="w-56 shrink-0 bg-white/70 backdrop-blur border-r border-slate-200/70 flex flex-col">
          <div className="p-5 border-b border-slate-200/70">
            <div className="flex items-center gap-1.5">
              <Wind className="w-6 h-6 text-blue-500" strokeWidth={2.5} />
              <span className="text-xl font-extrabold tracking-tight text-gray-900" style={{ fontFamily: 'var(--display)' }}>
                Sales<span className="text-blue-600">Breeze</span>
              </span>
            </div>
          </div>

          <nav className="flex-1 py-2">
            <div>
              <div className="px-3 py-2 text-xs text-gray-500 uppercase tracking-wide flex items-center justify-between">
                <span>일정관리 자동화</span>
                <ChevronRight className="w-3 h-3" />
              </div>
              <button
                onClick={() => navigate('/messages/jandi')}
                className={`w-full flex items-center px-4 py-3 transition-colors ${
                  location.pathname.includes('/messages/jandi')
                    ? 'bg-white text-gray-900 border-l-2 border-blue-500'
                    : 'text-gray-600 hover:bg-white/50'
                }`}
              >
                <img src={jandiLogo} alt="JANDI" className="h-8 w-auto object-contain" style={{ mixBlendMode: 'multiply' }} />
              </button>
              <button
                onClick={() => navigate('/messages/gmail')}
                className={`w-full flex items-center px-4 py-3 transition-colors ${
                  location.pathname.includes('/messages/gmail')
                    ? 'bg-white text-gray-900 border-l-2 border-blue-500'
                    : 'text-gray-600 hover:bg-white/50'
                }`}
              >
                <img src={gmailLogo} alt="Gmail" className="ml-1.5 h-[22px] w-auto object-contain" style={{ mixBlendMode: 'multiply' }} />
              </button>
            </div>

            <div className="mt-6">
              <div className="px-3 py-2 text-xs text-gray-500 uppercase tracking-wide text-left">
                메뉴
              </div>
              {menuItems.map((item) => {
                const Icon = item.icon;
                return (
                  <button
                    key={item.path}
                    onClick={() => navigate(item.path)}
                    className={`w-full flex items-center gap-3 px-4 py-2.5 text-sm transition-colors ${
                      isActive(item.path)
                        ? 'bg-gradient-to-r from-blue-50 to-transparent text-blue-700 font-semibold border-l-2 border-blue-500'
                        : 'text-slate-600 font-medium hover:bg-slate-50 border-l-2 border-transparent'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    <span>{item.label}</span>
                    {item.badge > 0 && (
                      <span className="ml-auto rounded-full bg-red-50 px-1.5 text-[11px] font-semibold text-red-600">
                        {item.badge}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </nav>

          <div className="p-4 border-t border-slate-200/70">
            <div className="text-xs text-slate-400 text-left">
              Salesmap 연동 시스템
            </div>
          </div>
        </div>

        <div className="flex-1 min-w-0 flex flex-col">
          <div className="bg-white/80 backdrop-blur border-b border-slate-200/70 px-6 py-3 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h2 className="whitespace-nowrap text-base font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>
                {pageTitle}
              </h2>
              {demoEnabled && (
                <button
                  type="button"
                  onClick={() => navigate('/settings')}
                  title="데모 데이터 모드가 켜져 있습니다. 설정에서 끌 수 있습니다."
                  className="inline-flex shrink-0 items-center gap-1 whitespace-nowrap rounded-full border border-amber-300 bg-amber-50 px-2.5 py-0.5 text-[11px] font-semibold text-amber-700 hover:bg-amber-100"
                >
                  <FlaskConical className="h-3 w-3" />
                  데모 데이터
                </button>
              )}
              <AnalysisProgressChip job={activity.job} onClick={() => navigate('/messages/gmail')} />
            </div>

            <div className="flex items-center gap-3">
              <button
                onClick={handleSalesmapClick}
                className={`px-4 py-1.5 text-sm border rounded ${
                  topSalesmapConnected
                    ? 'border-green-300 bg-green-50 text-green-700'
                    : 'border-gray-300 text-gray-700 hover:bg-gray-50'
                }`}
              >
                {topCompanyName}
              </button>
              <button className="p-1.5 hover:bg-gray-100 rounded">
                <Search className="w-4 h-4 text-gray-600" />
              </button>

              <div className="flex items-center gap-2 pl-3 border-l border-gray-200">
                <div className="w-7 h-7 bg-blue-500 rounded-full flex items-center justify-center">
                  <span className="text-xs font-semibold text-white">{avatarText}</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-sm text-gray-700">{displayName}</span>
                  <ChevronDown className="w-3 h-3 text-gray-500" />
                </div>
                <button
                  onClick={handleLogout}
                  className="ml-2 px-2.5 py-1 text-xs border border-gray-300 rounded text-gray-600 hover:bg-gray-50"
                >
                  로그아웃
                </button>
              </div>
            </div>
          </div>

          <div className="flex-1 min-w-0 overflow-auto">
            <ActivityProvider value={activity}>
              {children}
            </ActivityProvider>
          </div>
        </div>
      </div>

      <ActivityToasts
        toasts={activity.toasts}
        onDismiss={activity.dismissToast}
        onOpen={() => navigate('/messages/gmail')}
      />

      {currentReminder && (
        <ReminderModal
          isOpen={showReminder}
          onClose={() => {
            setShowReminder(false);
            setCurrentReminder(null);
          }}
          reminder={currentReminder}
        />
      )}
    </>
  );
}
