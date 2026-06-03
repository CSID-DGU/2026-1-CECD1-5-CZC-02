import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarCheck, CheckCircle2, Link2, Settings as SettingsIcon, UserRound } from 'lucide-react';
import { getApiErrorMessage } from '../api/errors';
import { disconnectGmailIntegration, getGmailAuthorizationUrl, getIntegrations } from '../api/integrations';
import jandiIcon from '../assets/image-8.png';
import jandiText from '../assets/image-9.png';
import gmailIcon from '../assets/image-10.png';
import gmailText from '../assets/image-14.png';

function StatusBadge({ connected, pending = false }) {
  if (connected) {
    return (
      <span className="inline-flex items-center gap-1 rounded-full border border-green-200 bg-green-50 px-2.5 py-1 text-xs font-medium text-green-700">
        <CheckCircle2 className="h-3.5 w-3.5" />
        연결됨
      </span>
    );
  }

  if (pending) {
    return (
      <span className="inline-flex items-center rounded-full border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs font-medium text-gray-600">
        연동 준비 중
      </span>
    );
  }

  return (
    <span className="inline-flex items-center rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-500">
      연결되지 않음
    </span>
  );
}

function InfoRow({ label, value }) {
  return (
    <div className="flex items-center justify-between gap-4 text-sm">
      <span className="text-gray-500">{label}</span>
      <span className="text-right font-medium text-gray-800">{value || '해당 없음'}</span>
    </div>
  );
}

export function SettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [integrations, setIntegrations] = useState([]);
  const [jandiConnected, setJandiConnected] = useState(() => localStorage.getItem('jandiConnected') === 'true');
  const [isLoading, setIsLoading] = useState(true);
  const [isConnectingGmail, setIsConnectingGmail] = useState(false);
  const [isDisconnectingGmail, setIsDisconnectingGmail] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  const gmailIntegration = useMemo(
    () => integrations.find((integration) => integration.provider === 'GMAIL'),
    [integrations]
  );
  const gmailConnected = gmailIntegration?.status === 'CONNECTED';

  const loadIntegrations = useCallback(async () => {
    try {
      setIsLoading(true);
      setErrorMessage('');
      const nextIntegrations = await getIntegrations();
      setIntegrations(nextIntegrations);
      return nextIntegrations;
    } catch (error) {
      console.error('Failed to fetch integrations:', {
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        rawError: error,
      });
      setErrorMessage(`연동 상태 조회 실패: ${getApiErrorMessage(error)}`);
      return [];
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadIntegrations();
  }, [loadIntegrations]);

  useEffect(() => {
    const gmailResult = searchParams.get('gmail');
    const storedMessage = sessionStorage.getItem('gmailOAuthMessage');
    let timerId;

    if (gmailResult === 'connected') {
      timerId = window.setTimeout(() => {
        setStatusMessage(storedMessage || 'Gmail 계정 연동이 완료되었습니다.');
        setErrorMessage('');
        sessionStorage.removeItem('gmailOAuthMessage');
        setSearchParams({}, { replace: true });
        loadIntegrations();
      }, 0);
      return () => window.clearTimeout(timerId);
    }

    if (gmailResult === 'error') {
      timerId = window.setTimeout(() => {
        setStatusMessage('');
        setErrorMessage(storedMessage || 'Gmail 계정 연동에 실패했습니다.');
        sessionStorage.removeItem('gmailOAuthMessage');
        setSearchParams({}, { replace: true });
      }, 0);
      return () => window.clearTimeout(timerId);
    }
  }, [loadIntegrations, searchParams, setSearchParams]);

  const handleGmailConnect = async () => {
    try {
      setIsConnectingGmail(true);
      setStatusMessage('');
      setErrorMessage('');

      const response = await getGmailAuthorizationUrl();
      if (!response?.authorizationUrl) {
        throw new Error('Gmail 연결 주소가 응답에 없습니다.');
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

  const handleGmailDisconnect = async () => {
    if (!window.confirm('Gmail 연결을 해제하시겠습니까?')) {
      return;
    }

    try {
      setIsDisconnectingGmail(true);
      setStatusMessage('');
      setErrorMessage('');

      await disconnectGmailIntegration();
      await loadIntegrations();
      setStatusMessage('Gmail 연결이 해제되었습니다. 필요한 경우 다시 연결할 수 있습니다.');
    } catch (error) {
      console.error('Failed to disconnect Gmail:', {
        status: error.response?.status,
        message: error.response?.data?.message,
        data: error.response?.data?.data,
        rawError: error,
      });
      setErrorMessage(`Gmail 연결 해제 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setIsDisconnectingGmail(false);
    }
  };

  const handleJandiToggle = () => {
    const nextValue = !jandiConnected;
    localStorage.setItem('jandiConnected', String(nextValue));
    setJandiConnected(nextValue);
  };

  return (
    <div className="h-full overflow-y-auto bg-[#F8F9FA] px-6 py-7">
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="relative flex items-center justify-center gap-4">
          <div className="text-center">
            <h2 className="text-2xl !font-bold !text-blue-800">계정 설정</h2>
            <p className="mt-2 text-sm !font-semibold !text-gray-800">
              Gmail 수집, Google Calendar 경유 Salesmap 등록, 사용자 정보를 관리합니다.
            </p>
          </div>
          <SettingsIcon className="absolute right-0 top-1 h-5 w-5 text-blue-500" />
        </div>

        {(statusMessage || errorMessage) && (
          <div className={`rounded-lg border px-4 py-3 text-sm ${
            errorMessage
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-blue-200 bg-blue-50 text-blue-700'
          }`}>
            {errorMessage || statusMessage}
          </div>
        )}

        <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="relative mb-5 flex items-center justify-center">
            <div className="mx-auto text-center">
              <h3 className="text-base font-semibold text-gray-900">계정 연결</h3>
              <p className="mt-1 text-sm text-gray-500">메일 수집과 일정 등록에 필요한 외부 계정 상태입니다.</p>
            </div>
            <Link2 className="absolute right-0 top-1 h-5 w-5 text-gray-400" />
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <div className={`rounded-lg border p-4 ${gmailConnected ? 'border-green-200 bg-green-50/40' : 'border-gray-200 bg-white'}`}>
              <div className="mb-4 flex items-start justify-between gap-3">
                <div className="flex items-center gap-2">
                  <img src={gmailIcon} alt="Gmail Icon" className="h-6 w-6 object-contain" />
                  <img src={gmailText} alt="Gmail" className="h-5 w-12 object-contain" style={{ mixBlendMode: 'darken' }} />
                </div>
                <StatusBadge connected={gmailConnected} />
              </div>
              <p className="mb-4 text-sm leading-6 text-gray-600">
                Gmail 메일을 수집하고 승인된 일정을 Google Calendar에 등록합니다.
              </p>
              <div className="space-y-2 border-t border-gray-100 pt-3">
                <InfoRow label="연결 계정" value={gmailIntegration?.externalAccountId} />
                <InfoRow label="상태" value={isLoading ? '확인 중' : (gmailConnected ? '정상 연결' : '미연결')} />
              </div>
              <div className="mt-4 flex gap-2">
                {gmailConnected && (
                  <button
                    onClick={handleGmailDisconnect}
                    disabled={isDisconnectingGmail || isLoading}
                    className="flex-1 rounded-md border border-red-200 px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50 disabled:bg-gray-100 disabled:text-gray-400"
                  >
                    {isDisconnectingGmail ? '해제 중...' : '연결 해제'}
                  </button>
                )}
                <button
                  onClick={handleGmailConnect}
                  disabled={isConnectingGmail || isDisconnectingGmail || isLoading}
                  className="flex-1 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-100 disabled:text-gray-400"
                >
                  {gmailConnected ? '다시 연결' : isConnectingGmail ? '연결 중...' : '연결'}
                </button>
              </div>
            </div>

            <div className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="mb-4 flex items-start justify-between gap-3">
                <div className="flex items-center gap-2">
                  <img src={jandiIcon} alt="Jandi Icon" className="h-6 w-6 object-contain" />
                  <img src={jandiText} alt="JANDI" className="h-4 object-contain" style={{ mixBlendMode: 'darken' }} />
                </div>
                <StatusBadge connected={jandiConnected} pending={!jandiConnected} />
              </div>
              <p className="mb-4 text-sm leading-6 text-gray-600">
                JANDI 메시지 기반 영업 활동 수집은 추후 연결 예정입니다.
              </p>
              <div className="space-y-2 border-t border-gray-100 pt-3">
                <InfoRow label="연결 계정" value={jandiConnected ? 'JANDI workspace' : '준비 중'} />
                <InfoRow label="상태" value={jandiConnected ? '연결됨' : '연동 준비 중'} />
              </div>
              <button
                onClick={handleJandiToggle}
                disabled={!jandiConnected}
                className="mt-4 w-full rounded-md bg-gray-100 px-3 py-2 text-sm font-medium text-gray-400"
              >
                연동 준비 중
              </button>
            </div>

            <div className={`rounded-lg border p-4 ${gmailConnected ? 'border-green-200 bg-green-50/40' : 'border-gray-200 bg-white'}`}>
              <div className="mb-4 flex items-start justify-between gap-3">
                <div className="flex items-center gap-2">
                  <CalendarCheck className="h-6 w-6 text-green-600" />
                  <span className="font-semibold text-gray-900">Salesmap</span>
                </div>
                <StatusBadge connected={gmailConnected} pending={!gmailConnected} />
              </div>
              <p className="mb-4 text-sm leading-6 text-gray-600">
                승인된 일정을 Google Calendar에 등록하면 Salesmap 양방향 캘린더 연동으로 TODO에 반영됩니다.
              </p>
              <div className="space-y-2 border-t border-gray-100 pt-3">
                <InfoRow label="연동 방식" value="Google Calendar 경유" />
                <InfoRow label="가져오기 유형" value="미팅" />
              </div>
            </div>
          </div>
        </section>

        <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="mb-5 flex items-center gap-2">
            <UserRound className="h-5 w-5 text-gray-400" />
            <h3 className="text-base font-semibold text-gray-900">프로필 설정</h3>
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            <div>
              <label className="mb-2 block text-sm font-medium text-gray-700">이름</label>
              <input
                type="text"
                defaultValue="김영업"
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-gray-700">이메일</label>
              <input
                type="email"
                defaultValue="sales@nimbustech.com"
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-gray-700">부서</label>
              <input
                type="text"
                defaultValue="영업팀"
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              />
            </div>
          </div>
          <div className="mt-5 flex justify-end">
            <button className="rounded-md bg-blue-600 px-5 py-2 text-sm font-medium text-white hover:bg-blue-700">
              저장
            </button>
          </div>
        </section>

        <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
          <div className="relative mb-5 flex items-center justify-center">
            <div className="mx-auto text-center">
              <h3 className="text-base font-semibold text-gray-900">Salesmap 연동 상태</h3>
              <p className="mt-1 text-sm text-gray-500">
                Salesmap 직접 쓰기 API 대신 Google Calendar 양방향 연동을 사용합니다.
              </p>
            </div>
            <div className="absolute right-0 top-0">
              <StatusBadge connected={gmailConnected} pending={!gmailConnected} />
            </div>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <div className="rounded-lg bg-gray-50 p-4">
              <p className="text-sm font-medium text-gray-900">자동 동기화</p>
              <p className="mt-1 text-sm text-gray-500">
                Salesmap 등록 버튼을 누르면 승인된 일정이 Google Calendar에 생성됩니다.
              </p>
            </div>
            <div className="rounded-lg bg-gray-50 p-4">
              <p className="text-sm font-medium text-gray-900">연동 조건</p>
              <p className="mt-1 text-sm text-gray-500">
                Gmail 연결과 Salesmap 캘린더 양방향 설정이 모두 완료되어야 합니다.
              </p>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
