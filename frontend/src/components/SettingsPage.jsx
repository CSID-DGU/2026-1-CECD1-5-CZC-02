import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarCheck, CheckCircle2, FlaskConical, Link2, RotateCcw, Settings as SettingsIcon, Zap } from 'lucide-react';
import { getApiErrorMessage } from '../api/errors';
import { disconnectGmailIntegration, getGmailAuthorizationUrl, getIntegrations } from '../api/integrations';
import { disableAutoPipeline, enableAutoPipeline, getAutoPipelineStatus } from '../api/autoPipeline';
import { disableDemo, enableDemo, getDemoStatus, resetDemo } from '../api/demo';
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

const DEMO_FLASH_KEY = 'demoModeFlash';

function DemoModePanel() {
  const [status, setStatus] = useState(null);
  const [isWorking, setIsWorking] = useState(false);
  const [message] = useState(() => sessionStorage.getItem(DEMO_FLASH_KEY) || '');
  const [errorMessage, setErrorMessage] = useState('');
  const enabled = Boolean(status?.enabled);

  useEffect(() => {
    sessionStorage.removeItem(DEMO_FLASH_KEY);
    getDemoStatus()
      .then(setStatus)
      .catch((error) => setErrorMessage(`데모 상태 조회 실패: ${getApiErrorMessage(error)}`));
  }, []);

  // 켜고 끄면 모든 화면이 다른 사용자 데이터를 봐야 하므로 새로고침으로 한 번에 다시 불러온다.
  const reloadWith = (flash) => {
    sessionStorage.setItem(DEMO_FLASH_KEY, flash);
    window.location.reload();
  };

  const handleToggle = async () => {
    try {
      setIsWorking(true);
      setErrorMessage('');
      if (enabled) {
        await disableDemo();
        reloadWith('데모 모드를 껐습니다. 실제 계정 데이터로 돌아왔습니다.');
      } else {
        await enableDemo();
        reloadWith('데모 모드를 켰습니다. Gmail 메일함에서 "Gmail 새로고침"을 눌러 데모 메일을 받아보세요.');
      }
    } catch (error) {
      setErrorMessage(`데모 모드 전환 실패: ${getApiErrorMessage(error)}`);
      setIsWorking(false);
    }
  };

  const handleReset = async () => {
    if (!window.confirm('데모 메일·분석·일정을 모두 지우고 처음부터 다시 시작할까요? (실제 계정 데이터는 영향 없음)')) {
      return;
    }
    try {
      setIsWorking(true);
      setErrorMessage('');
      await resetDemo();
      reloadWith('데모 데이터를 초기화했습니다. "Gmail 새로고침"부터 다시 시작하세요.');
    } catch (error) {
      setErrorMessage(`데모 초기화 실패: ${getApiErrorMessage(error)}`);
      setIsWorking(false);
    }
  };

  const nextArrival = !status
    ? ''
    : status.deliveredStage >= status.lastStage
      ? '시나리오 메일이 모두 도착했습니다.'
      : status.deliveredStage === 0
        ? `"Gmail 새로고침"을 누르면 첫 메일 묶음이 도착합니다. (남은 ${status.pendingMailCount}통)`
        : `다음 "Gmail 새로고침"에서 일정 변경·취소·확정 메일과 새 문의가 도착합니다. (남은 ${status.pendingMailCount}통)`;

  return (
    <section className={`rounded-2xl border border-dashed p-4 ${enabled ? 'border-amber-300 bg-amber-50/60' : 'border-slate-300 bg-white/60'}`}>
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-start gap-3 text-left">
          <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${enabled ? 'bg-amber-500' : 'bg-slate-100'}`}>
            <FlaskConical className={`h-4 w-4 ${enabled ? 'text-white' : 'text-slate-400'}`} />
          </span>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">개발자 기능</span>
              <h3 className="text-sm font-bold text-slate-900">데모 데이터 모드</h3>
              <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                enabled ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-500'
              }`}>
                {enabled ? '켜짐' : '꺼짐'}
              </span>
            </div>
            <p className="mt-1 max-w-2xl text-xs leading-5 text-slate-500">
              실제 Gmail 없이 고객사 6곳의 가짜 영업 메일로 전체 흐름을 시연합니다. 메일만 가짜이고
              AI 분석·딜 파이프라인·일정·답장 초안은 실제로 동작합니다. 켜져 있는 동안 실제 계정 데이터는 가려지고, 끄면 그대로 돌아옵니다.
              데모에서 보낸 답장과 등록한 일정은 외부(Gmail·Google Calendar)로 나가지 않습니다.
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={handleToggle}
          disabled={isWorking || !status}
          role="switch"
          aria-checked={enabled}
          aria-label="데모 데이터 모드"
          className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition disabled:opacity-60 ${
            enabled ? 'bg-amber-500' : 'bg-slate-300'
          }`}
        >
          <span className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition ${
            enabled ? 'translate-x-6' : 'translate-x-1'
          }`} />
        </button>
      </div>

      {(message || errorMessage) && (
        <p className={`mt-3 text-xs ${errorMessage ? 'text-red-600' : 'text-amber-700'}`}>{errorMessage || message}</p>
      )}

      {enabled && status && (
        <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-amber-200/70 pt-3 text-left">
          <div className="space-y-1 text-xs text-slate-600">
            <p>
              데모 메일 계정 <b className="font-medium text-slate-800">{status.demoAccountEmail}</b>
              {' · '}도착 {status.deliveredMailCount}통 / 단계 {status.deliveredStage}/{status.lastStage}
            </p>
            <p className="text-slate-500">{nextArrival}</p>
            <p className="text-slate-400">
              순서: Gmail 새로고침 → 수집 메일 AI 분석 → 미팅 건 승인·일정 등록 → 다시 새로고침(변경·취소 메일) → 분석·승인
            </p>
          </div>
          <button
            type="button"
            onClick={handleReset}
            disabled={isWorking}
            className="inline-flex items-center gap-1.5 rounded-md border border-amber-300 bg-white px-3 py-1.5 text-xs font-medium text-amber-700 hover:bg-amber-50 disabled:opacity-50"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            처음부터 다시
          </button>
        </div>
      )}
    </section>
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
  const [companyName, setCompanyName] = useState(() => localStorage.getItem('salesmapCompany') || '');
  const [isLoading, setIsLoading] = useState(true);
  const [isConnectingGmail, setIsConnectingGmail] = useState(false);
  const [isDisconnectingGmail, setIsDisconnectingGmail] = useState(false);
  const [statusMessage, setStatusMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [autoEnabled, setAutoEnabled] = useState(false);
  const [isTogglingAuto, setIsTogglingAuto] = useState(false);

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
    let ignore = false;
    getAutoPipelineStatus()
      .then((result) => {
        if (!ignore) {
          setAutoEnabled(Boolean(result?.enabled));
          localStorage.setItem('autoPipelineEnabled', String(Boolean(result?.enabled)));
        }
      })
      .catch((error) => console.error('Failed to load auto-pipeline status:', error));
    return () => {
      ignore = true;
    };
  }, []);

  const handleToggleAuto = async () => {
    try {
      setIsTogglingAuto(true);
      const result = autoEnabled ? await disableAutoPipeline() : await enableAutoPipeline();
      const next = Boolean(result?.enabled);
      setAutoEnabled(next);
      localStorage.setItem('autoPipelineEnabled', String(next));
      setStatusMessage(next
        ? '자동 수집·분석을 켰습니다. 새 메일을 주기적으로 자동 수집하고 분석합니다.'
        : '자동 수집·분석을 껐습니다.');
      setErrorMessage('');
    } catch (error) {
      console.error('Failed to toggle auto-pipeline:', error);
      setErrorMessage(`자동 모드 전환 실패: ${getApiErrorMessage(error)}`);
    } finally {
      setIsTogglingAuto(false);
    }
  };

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

  return (
    <div className="h-full overflow-y-auto bg-gradient-to-b from-[#F6F8FC] to-[#EEF2F9] px-6 py-7">
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="relative overflow-hidden rounded-2xl border border-white/60 bg-white/80 shadow-[0_10px_40px_-18px_rgba(37,99,235,0.35)] backdrop-blur p-6">
          <div className="absolute -right-16 -top-16 h-56 w-56 rounded-full bg-gradient-to-br from-blue-400/20 to-violet-400/10 blur-2xl" />
          <div className="relative flex items-start justify-between gap-4">
            <div className="text-left">
              <p className="inline-flex items-center gap-1.5 text-xs font-semibold text-blue-600">
                <SettingsIcon className="h-3.5 w-3.5" /> 계정 관리
              </p>
              <h2 className="mt-1 text-[26px] leading-tight tracking-tight text-slate-900" style={{ fontFamily: 'var(--display)', fontWeight: 700 }}>
                계정 설정
              </h2>
              <p className="mt-2 max-w-2xl text-sm text-slate-500">
                Gmail 수집, Google Calendar 경유 Salesmap 등록, 연동 회사명을 관리합니다.
              </p>
            </div>
          </div>
        </div>

        {(statusMessage || errorMessage) && (
          <div className={`rounded-xl border px-4 py-3 text-sm ${
            errorMessage
              ? 'border-red-200 bg-red-50 text-red-700'
              : 'border-blue-200 bg-blue-50 text-blue-700'
          }`}>
            {errorMessage || statusMessage}
          </div>
        )}

        <section className={`relative overflow-hidden rounded-2xl border p-5 shadow-sm transition ${
          autoEnabled ? 'border-blue-200 bg-gradient-to-br from-blue-50/80 to-violet-50/50' : 'border-slate-200 bg-white'
        }`}>
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-start gap-3 text-left">
              <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${
                autoEnabled ? 'bg-gradient-to-br from-sky-500 to-indigo-500 shadow-lg shadow-blue-500/20' : 'bg-slate-100'
              }`}>
                <Zap className={`h-5 w-5 ${autoEnabled ? 'text-white' : 'text-slate-400'}`} />
              </span>
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-base font-bold text-slate-900" style={{ fontFamily: 'var(--display)' }}>
                    자동 수집·분석 모드
                  </h3>
                  <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                    autoEnabled ? 'bg-blue-100 text-blue-700' : 'bg-slate-100 text-slate-500'
                  }`}>
                    {autoEnabled ? '켜짐' : '꺼짐'}
                  </span>
                </div>
                <p className="mt-1 max-w-xl text-sm text-slate-500">
                  켜두면 서버가 주기적으로 새 메일을 <b>자동 수집</b>하고 <b>AI 분석</b>까지 마쳐 "승인 대기"로 쌓아둡니다.
                  담당자는 결과를 확인하고 <b>승인만</b> 하면 됩니다. (수동으로 눌러 기다릴 필요 없음)
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={handleToggleAuto}
              disabled={isTogglingAuto}
              role="switch"
              aria-checked={autoEnabled}
              className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition disabled:opacity-60 ${
                autoEnabled ? 'bg-gradient-to-r from-sky-500 to-indigo-500' : 'bg-slate-300'
              }`}
            >
              <span className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition ${
                autoEnabled ? 'translate-x-6' : 'translate-x-1'
              }`} />
            </button>
          </div>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
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
                  className="flex-1 rounded-lg bg-gradient-to-r from-sky-500 to-indigo-500 px-3 py-2 text-sm font-semibold text-white shadow-sm transition hover:from-sky-600 hover:to-indigo-600 disabled:opacity-50"
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
                <StatusBadge connected={false} pending />
              </div>
              <p className="mb-4 text-sm leading-6 text-gray-600">
                JANDI는 메시지 수집 API를 제공하지 않아 영업 활동 수집 소스로는 연결할 수 없습니다.
                Incoming Webhook을 이용한 알림 발송은 검토 중입니다.
              </p>
              <div className="space-y-2 border-t border-gray-100 pt-3">
                <InfoRow label="연결 계정" value="연결 안 됨" />
                <InfoRow label="상태" value="연동 준비 중" />
              </div>
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

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
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
          <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
            <label className="mb-2 block text-sm font-medium text-gray-900">연동 회사명</label>
            <input
              type="text"
              value={companyName}
              onChange={(e) => {
                setCompanyName(e.target.value);
                localStorage.setItem('salesmapCompany', e.target.value.trim());
              }}
              className="w-full max-w-sm rounded border border-gray-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500"
              placeholder="예: 대성물류"
            />
            <p className="mt-1.5 text-xs text-gray-500">상단 바에 표시되는 회사명입니다.</p>
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

        <DemoModePanel />
      </div>
    </div>
  );
}
