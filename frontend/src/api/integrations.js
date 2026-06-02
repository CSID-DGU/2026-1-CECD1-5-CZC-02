import { api } from './client';

export async function getIntegrations() {
  const response = await api.get('/api/integrations');

  const data = response.data.data;
  return Array.isArray(data) ? data : data ? [data] : [];
}

export async function getGmailAuthorizationUrl() {
  const response = await api.get('/api/integrations/gmail/authorize');

  return response.data.data;
}

export async function completeGmailOAuth({ code, state }) {
  const response = await api.get('/api/integrations/gmail/callback', {
    params: { code, state },
  });

  return response.data.data;
}

export async function disconnectGmailIntegration() {
  const response = await api.delete('/api/integrations/gmail');

  return response.data.data;
}

export async function collectGmailMessages(params = {}) {
  const response = await api.post('/api/integrations/gmail/collect', null, {
    params,
  });

  return response.data.data;
}

export async function syncGmailIfConnected() {
  try {
    const integrations = await getIntegrations();
    const gmailIntegration = integrations.find(
      (integration) => integration.provider === 'GMAIL' && integration.status === 'CONNECTED'
    );

    if (!gmailIntegration) {
      return null;
    }

    return await collectGmailMessages({ mode: 'auto' });
  } catch (error) {
    console.warn('Gmail auto sync skipped:', error.response?.data?.message || error.message);
    return null;
  }
}
