import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Settings, Search, LayoutDashboard, ChevronDown, ChevronRight } from 'lucide-react';
import nimbusTechLogo from '../assets/님버스테크 로고.png';
import jandiLogo from '../assets/잔디 로고 이미지 .jpg';
import gmailLogo from '../assets/gmail로고 이미지 spaced.png';

import { ReminderModal } from './ReminderModal';

export function DashboardLayout({ children }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [salesmapConnected, setSalesmapConnected] = useState(() => localStorage.getItem('salesmapConnected') === 'true');
  const [companyName, setCompanyName] = useState(() => localStorage.getItem('salesmapCompany') || '');
  const [showReminder, setShowReminder] = useState(false);
  const [currentReminder, setCurrentReminder] = useState(null);

  useEffect(() => {
    const timerId = window.setTimeout(() => {
      const connected = localStorage.getItem('salesmapConnected') === 'true';
      const company = localStorage.getItem('salesmapCompany') || '';
      setSalesmapConnected(connected);
      setCompanyName(company);
    }, 0);

    return () => window.clearTimeout(timerId);
  }, [location]);

  useEffect(() => {
    // TODO: Replace this local mock reminder list with GET /api/schedules when schedule UI is connected to backend.
    const upcomingEvents = [
      {
        title: 'ABC 기업 미팅',
        date: '2026-05-05',
        time: '14:00',
        content: '신규 제품 구매 상담 및 계약 논의',
        type: '미팅'
      },
      {
        title: '김영희 고객 전화 통화',
        date: '2026-05-04',
        time: '16:30',
        content: '제안서 피드백 논의',
        type: '전화'
      }
    ];

    const timer = setTimeout(() => {
      setCurrentReminder(upcomingEvents[0]);
      setShowReminder(true);
    }, 3000);

    return () => clearTimeout(timer);
  }, []);

  const handleSalesmapClick = () => {
    if (!salesmapConnected) {
      navigate('/salesmap-login');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    navigate('/login', { replace: true });
  };

  const menuItems = [
    { path: '/dashboard', icon: LayoutDashboard, label: '대시보드' },
    { path: '/settings', icon: Settings, label: '설정' }
  ];

  const isActive = (path) => location.pathname === path;

  return (
    <>
      <div className="h-screen flex bg-[#F8F9FA]">
        {/* Sidebar */}
        <div className="w-56 bg-[#F3F4F6] border-r border-gray-200 flex flex-col">
          <div className="p-5 border-b border-gray-200 bg-[#F3F4F6]">
            <img
              src={nimbusTechLogo}
              alt="NIMBUS TECH"
              className="block w-44 h-auto object-contain"
              style={{ mixBlendMode: 'multiply' }}
            />
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

            {/* Main Menu */}
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
                        ? 'bg-white text-gray-900 border-l-2 border-blue-500'
                        : 'text-gray-600 hover:bg-white/50'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    <span>{item.label}</span>
                  </button>
                );
              })}
            </div>
          </nav>

          <div className="p-4 border-t border-gray-200">
            <div className="text-xs text-gray-500 text-left">
              Salesmap 연동 시스템
            </div>
          </div>
        </div>

        {/* Main Content */}
        <div className="flex-1 flex flex-col">
          <div className="bg-white border-b border-gray-200 px-6 py-3 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h2 className="text-base !font-semibold !text-black">
                {menuItems.find(item => item.path === location.pathname)?.label || '대시보드'}
              </h2>
            </div>

            <div className="flex items-center gap-3">
              <button
                onClick={handleSalesmapClick}
                className={`px-4 py-1.5 text-sm border rounded ${
                  salesmapConnected
                    ? 'border-green-300 bg-green-50 text-green-700'
                    : 'border-gray-300 text-gray-700 hover:bg-gray-50'
                }`}
              >
                {salesmapConnected ? companyName : 'Salesmap 연동'}
              </button>
              <button className="p-1.5 hover:bg-gray-100 rounded">
                <Search className="w-4 h-4 text-gray-600" />
              </button>

              <div className="flex items-center gap-2 pl-3 border-l border-gray-200">
                <div className="w-7 h-7 bg-blue-500 rounded-full flex items-center justify-center">
                  <span className="text-xs text-white">김</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-sm text-gray-700">김영업</span>
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

          <div className="flex-1 overflow-auto">
            {children}
          </div>
        </div>
      </div>

      {currentReminder && (
        <ReminderModal
          isOpen={showReminder}
          onClose={() => setShowReminder(false)}
          reminder={currentReminder}
        />
      )}
    </>
  );
}
