import { useState, useEffect } from 'react';
import { Settings as SettingsIcon } from 'lucide-react';
import jandiIcon from '../assets/image-8.png';
import jandiText from '../assets/image-9.png';
import gmailIcon from '../assets/image-10.png';
import gmailText from '../assets/image-14.png';

export function SettingsPage() {
  const [connections, setConnections] = useState([
    {
      id: 'gmail',
      name: 'Gmail',
      icon: (
        <div className="flex items-center gap-2">
          <img src={gmailIcon} alt="Gmail Icon" className="w-6 h-6 object-contain" />
          <img src={gmailText} alt="Gmail" className="h-5 w-12 object-contain" style={{ mixBlendMode: 'darken', backgroundColor: '#ffffff' }} />
        </div>
      ),
      connected: true,
      color: 'bg-white'
    },
    {
      id: 'jandi',
      name: 'Jandi',
      icon: (
        <div className="flex items-center gap-2">
          <img src={jandiIcon} alt="Jandi Icon" className="w-6 h-6 object-contain" />
          <img src={jandiText} alt="JANDI" className="h-4 object-contain" style={{ mixBlendMode: 'darken', backgroundColor: '#ffffff' }} />
        </div>
      ),
      connected: false,
      color: 'bg-white'
    }
  ]);

  useEffect(() => {
    const jandiConnected = localStorage.getItem('jandiConnected') === 'true';
    setConnections(prev => prev.map(conn =>
      conn.id === 'jandi' ? { ...conn, connected: jandiConnected } : conn
    ));
  }, []);

  const handleToggleConnection = (id) => {
    setConnections(connections.map(conn => {
      if (conn.id === id) {
        const newConnected = !conn.connected;
        if (id === 'jandi') {
          localStorage.setItem('jandiConnected', newConnected.toString());
        }
        return { ...conn, connected: newConnected };
      }
      return conn;
    }));
  };

  return (
    <div className="p-6">
      <div className="max-w-2xl">
        <h2 className="text-lg !font-bold !text-black mb-6">계정 설정</h2>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <div className="p-5 border-b border-gray-200 flex items-center justify-between">
            <h3 className="text-sm text-gray-700">계정 연결</h3>
            <SettingsIcon className="w-5 h-5 text-gray-400" />
          </div>

          <div className="divide-y divide-gray-200">
            {connections.map((connection) => {
              const getBackgroundColor = () => {
                if (!connection.connected) return 'bg-white';
                return 'bg-blue-50';
              };

              const getStatusColor = () => {
                if (!connection.connected) return 'text-gray-500';
                return 'text-blue-600';
              };

              const getButtonStyle = () => {
                if (connection.connected) {
                  return 'bg-blue-500 text-white hover:bg-blue-600 border border-blue-500';
                }
                return 'bg-blue-500 text-white hover:bg-blue-600';
              };

              return (
                <div
                  key={connection.id}
                  className={`w-full flex items-center justify-between px-5 py-4 transition-colors ${getBackgroundColor()}`}
                >
                  <div className="flex items-center gap-3">
                    <div className="flex items-center gap-2">
                      {connection.icon}
                    </div>
                    <div className="flex flex-col items-start">
                      <span className={`text-xs ${getStatusColor()}`}>
                        {connection.connected ? '연결됨' : '연결되지 않음'}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => handleToggleConnection(connection.id)}
                    className={`px-4 py-1.5 text-sm rounded transition-colors ${getButtonStyle()}`}
                  >
                    {connection.connected ? '연결 해제' : '연결'}
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        <div className="mt-6 bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <h3 className="text-sm text-gray-700 mb-4">프로필 설정</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-xs text-gray-600 mb-2">이름</label>
              <input
                type="text"
                defaultValue="김영업"
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-600 mb-2">이메일</label>
              <input
                type="email"
                defaultValue="sales@nimbustech.com"
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-600 mb-2">부서</label>
              <input
                type="text"
                defaultValue="영업팀"
                className="w-full px-3 py-2 text-sm border border-gray-300 rounded outline-none focus:border-blue-500"
              />
            </div>
            <button className="px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white text-sm rounded">
              저장
            </button>
          </div>
        </div>

        <div className="mt-6 bg-white rounded-lg shadow-sm border border-gray-200 p-5">
          <h3 className="text-sm text-gray-700 mb-4">Salesmap 연동 설정</h3>
          <div className="space-y-3">
            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-md">
              <div>
                <p className="text-sm text-gray-800">자동 동기화</p>
                <p className="text-xs text-gray-500">AI 분석 결과를 자동으로 Salesmap에 전송</p>
              </div>
              <button className="px-3 py-1 text-xs bg-blue-100 text-blue-700 rounded hover:bg-blue-200">
                활성화
              </button>
            </div>
            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-md">
              <div>
                <p className="text-sm text-gray-800">API 연결 상태</p>
                <p className="text-xs text-gray-500">Salesmap API 연결 확인</p>
              </div>
              <span className="px-2 py-1 text-xs bg-green-100 text-green-700 rounded">연결됨</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
