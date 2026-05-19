import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Settings, Search, LayoutDashboard, ChevronDown, ChevronRight } from 'lucide-react';
// 이미지 파일들은 src/assets/ 에 넣고 경로 수정하세요
// import logoImg from '../assets/logo.png';
// import jandiIcon from '../assets/jandi-icon.png';
// import jandiText from '../assets/jandi-text.png';
// import gmailIcon from '../assets/gmail-icon.png';
// import gmailText from '../assets/gmail-text.png';
import { ReminderModal } from './ReminderModal';

export function DashboardLayout({ children }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [salesmapConnected, setSalesmapConnected] = useState(false);
  const [companyName, setCompanyName] = useState('');
  const [jandiConnected, setJandiConnected] = useState(false);
  const [gmailConnected, setGmailConnected] = useState(true);
  const [showReminder, setShowReminder] = useState(false);
  const [currentReminder, setCurrentReminder] = useState(null);

  useEffect(() => {
    const connected = localStorage.getItem('salesmapConnected') === 'true';
    const company = localStorage.getItem('salesmapCompany') || '';
    const jandiConn = localStorage.getItem('jandiConnected') === 'true';
    setSalesmapConnected(connected);
    setCompanyName(company);
    setJandiConnected(jandiConn);
  }, [location]);

  // 리마인더 로직 - 하루 전과 30분 전
  useEffect(() => {
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

    // 데모를 위해 3초 후에 리마인더 표시
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
            <div className="flex items-center gap-2">
              {/* <img src={logoImg} alt="NimbusTech" className="w-7 h-7 object-contain" style={{ mixBlendMode: 'multiply' }} /> */}
              <div className="w-7 h-7 bg-blue-100 rounded-full flex items-center justify-center text-sm">☁️</div>
              <span className="text-sm text-gray-700 font-medium">NimbusTech</span>
            </div>
          </div>

          <nav className="flex-1 py-2">
            {/* Schedule Automation Section */}
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
                <div className="flex items-center gap-2.5">
                  {/* <img src={jandiIcon} alt="Jandi Icon" className="w-6 h-6 object-contain" /> */}
                  <span className="text-sm">📋 JANDI</span>
                </div>
              </button>
              <button
                onClick={() => navigate('/messages/gmail')}
                className={`w-full flex items-center px-4 py-3 transition-colors ${
                  location.pathname.includes('/messages/gmail')
                    ? 'bg-white text-gray-900 border-l-2 border-blue-500'
                    : 'text-gray-600 hover:bg-white/50'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  {/* <img src={gmailIcon} alt="Gmail Icon" className="w-6 h-6 object-contain" /> */}
                  <span className="text-sm">✉️ Gmail</span>
                </div>
              </button>
            </div>

            {/* Main Menu */}
            <div className="mt-6">
              <div className="px-3 py-2 text-xs text-gray-500 uppercase tracking-wide">
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
            <div className="text-xs text-gray-500">
              Salesmap 연동 시스템
            </div>
          </div>
        </div>

        {/* Main Content */}
        <div className="flex-1 flex flex-col">
          {/* Top Bar */}
          <div className="bg-white border-b border-gray-200 px-6 py-3 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <h2 className="text-base text-gray-800">
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

              {/* User Profile */}
              <div className="flex items-center gap-2 pl-3 border-l border-gray-200">
                <div className="w-7 h-7 bg-blue-500 rounded-full flex items-center justify-center">
                  <span className="text-xs text-white">김</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-sm text-gray-700">김영업</span>
                  <ChevronDown className="w-3 h-3 text-gray-500" />
                </div>
              </div>
            </div>
          </div>

          {/* Content Area */}
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
