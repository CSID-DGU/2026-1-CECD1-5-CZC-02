import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Settings as SettingsIcon } from 'lucide-react';
import { getApiErrorMessage } from '../api/errors';
import { getGmailAuthorizationUrl, getIntegrations } from '../api/integrations';
import jandiIcon from '../assets/image-8.png';
import jandiText from '../assets/image-9.png';
import gmailIcon from '../assets/image-10.png';
import gmailText from '../assets/image-14.png';

export function SettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [integrations, setIntegrations] = useState([]);
  const [jandiConnected, setJandiConnected] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isConnectingGmail, setIsConnectingGmail] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  const gmailIntegration = useMemo(
    () => integrations.find((integration) => integration.provider === 'GMAIL'),
    [integrations]
  );
  const gmailConnected = gmailIntegration?.status === 'CONNECTED';

  useEffect(() => {
    const storedJandiConnected = localStorage.getItem('jandiConnected') === 'true';
    setJandiConnected(storedJandiConnected);
  }, []);

  useEffect(() => {
    const loadIntegrations = async () => {
      try {
        setIsLoading(true);
        setErrorMessage('');
        const nextIntegrations = await getIntegrations();
        setIntegrations(nextIntegrations);
      } catch (error) {
        console.error('Failed to fetch integrations:', {
          status: error.response?.status,
          message: error.response?.data?.message,
          data: error.response?.data?.data,
          rawError: error,
        });
        setErrorMessage(`연동 상태 조회 실패: ${getApiErrorMessage(error)}`);
      } finally {
        setIsLoading(false);
      }
    };

    loadIntegrations();
  }, []);

  useEffect(() => {
    const gmailResult = searchParams.get('gmail');
    const storedMessage = sessionStorage.getItem('gmailOAuthMessage');

    if (gmailResult === 'connected') {
      setStatusMessage(storedMessage || 'Gmail 계정 연동이 완료되었습니다.');
      setErrorMessage('');
      sessionStorage.removeItem('gmailOAuthMessage');
      setSearchParams({}, { replace: true });
      return;
    }

    if (gmailResult === 'error') {
      setStatusMessage('');
      setErrorMessage(storedMessage || 'Gmail 계정 연동에 실패했습니다.');
      sessionStorage.removeItem('gmailOAuthMessage');
      setSearchParams({}, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  const handleGmailConnect = async () => {
    try {
      setIsConnectingGmail(true);
      setStatusMessage('');
      setErrorMessage('');

      const response = await getGmailAuthorizationUrl();
      if (!response?.authorizationUrl) {
        throw new Error('Gmail authorizationUrl이 응답에 없습니다.');
      }

      window.location.assign(response.authorizationUrl);
    } catch (error) {
      console.error('Failed to start Gmail OAuth:', {
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        rawError: error,
      });
      setErrorMessage(`Gmail 연결 시작 실패: ${getApiErrorMessage(error)}`);
      setIsConnectingGmail(false);
    }
  };

  const handleJandiToggle = () => {
    const nextValue = !jandiConnected;
    localStorage.setItem('jandiConnected', String(nextValue));
    setJandiConnected(nextValue);
  };

  const connections = [
    {
      id: 'gmail',
      name: 'Gmail',
      connected: gmailConnected,
      statusText: gmailConnected
        ? `${gmailIntegration.externalAccountId} 연결됨`
        : isLoading
          ? '연동 상태 확인 중'
          : '연결되지 않음',
      icon: (
        <div className="flex items-center gap-2">
          <img src={gmailIcon} alt="Gmail Icon" className="w-6 h-6 object-contain" />
          <img src={gmailText} alt="Gmail" className="h-5 w-12 object-contain" style={{ mixBlendMode: 'darken', backgroundColor: '#ffffff' }} />
        </div>
      ),
      buttonText: gmailConnected ? '연결됨' : isConnectingGmail ? '연결 중...' : '연결',
      disabled: gmailConnected || isConnectingGmail || isLoading,
      onClick: handleGmailConnect,
    },
    {
      id: 'jandi',
      name: 'Jandi',
      connected: jandiConnected,
      statusText: jandiConnected ? '연결됨' : '연결되지 않음',
      icon: (
        <div className="flex items-center gap-2">
          <img src={jandiIcon} alt="Jandi Icon" className="w-6 h-6 object-contain" />
          <img src={jandiText} alt="JANDI" className="h-4 object-contain" style={{ mixBlendMode: 'darken', backgroundColor: '#ffffff' }} />
        </div>
      ),
      buttonText: jandiConnected ? '연결 해제' : '연결',
      disabled: false,
      onClick: handleJandiToggle,
    },
  ];

  return (
    <div className="p-6">
      <div className="max-w-2xl">
        <h2 className="text-lg !font-semibold !text-black mb-6">계정 설정</h2>

        {(statusMessage || errorMessage) && (
          <div className={`mb-4 rounded-md border px-4 py-3 text-sm ${
            errorMessage
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-blue-200 bg-blue-50 text-blue-700'
          }`}>
            {errorMessage || statusMessage}
          </div>
        )}

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <div className="p-5 border-b border-gray-200 flex items-center justify-between">
            <h3 className="text-sm text-gray-700">계정 연결</h3>
            <SettingsIcon className="w-5 h-5 text-gray-400" />
          </div>

          <div className="divide-y divide-gray-200">
            {connections.map((connection) => (
              <div
                key={connection.id}
                className={`w-full flex items-center justify-between px-5 py-4 transition-colors ${
                  connection.connected ? 'bg-blue-50' : 'bg-white'
                }`}
              >
                <div className="flex items-center gap-3">
                  {connection.icon}
                  <div className="flex flex-col items-start">
                    <span className={`text-xs ${connection.connected ? 'text-blue-600' : 'text-gray-500'}`}>
                      {connection.statusText}
                    </span>
                  </div>
                </div>
                <button
                  onClick={connection.onClick}
                  disabled={connection.disabled}
                  className={`px-4 py-1.5 text-sm rounded transition-colors ${
                    connection.disabled
                      ? 'bg-gray-100 text-gray-400 cursor-not-allowed'
                      : 'bg-blue-500 text-white hover:bg-blue-600'
                  }`}
                >
                  {connection.buttonText}
                </button>
              </div>
            ))}
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
