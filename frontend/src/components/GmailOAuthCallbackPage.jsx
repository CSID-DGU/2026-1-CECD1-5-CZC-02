import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { completeGmailOAuth } from '../api/integrations';

export function GmailOAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [message] = useState('Gmail 계정 연동을 완료하는 중입니다.');

  useEffect(() => {
    const completeOAuth = async () => {
      const code = searchParams.get('code');
      const state = searchParams.get('state');
      const oauthError = searchParams.get('error');

      if (oauthError) {
        sessionStorage.setItem('gmailOAuthMessage', `Google OAuth 오류: ${oauthError}`);
        navigate('/settings?gmail=error', { replace: true });
        return;
      }

      if (!code || !state) {
        sessionStorage.setItem('gmailOAuthMessage', 'Gmail OAuth code 또는 state가 없습니다.');
        navigate('/settings?gmail=error', { replace: true });
        return;
      }

      try {
        const result = await completeGmailOAuth({ code, state });
        const account = result?.externalAccountId ? ` (${result.externalAccountId})` : '';
        sessionStorage.setItem('gmailOAuthMessage', `Gmail 계정 연동이 완료되었습니다${account}.`);
        navigate('/settings?gmail=connected', { replace: true });
      } catch (error) {
        console.error('Failed to complete Gmail OAuth:', {
          status: error.response?.status,
          message: error.response?.data?.message,
          data: error.response?.data?.data,
          rawError: error,
        });
        sessionStorage.setItem(
          'gmailOAuthMessage',
          error.response?.data?.message || 'Gmail 계정 연동에 실패했습니다.'
        );
        navigate('/settings?gmail=error', { replace: true });
      }
    };

    completeOAuth();
  }, [navigate, searchParams]);

  return (
    <div className="p-6">
      <div className="max-w-2xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg !font-semibold !text-black mb-2">Gmail 연동</h2>
        <p className="text-sm text-gray-600">{message}</p>
      </div>
    </div>
  );
}
