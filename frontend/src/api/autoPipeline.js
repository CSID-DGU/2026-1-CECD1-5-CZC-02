import { api } from './client';

export async function getAutoPipelineStatus() {
  const response = await api.get('/api/auto-pipeline/status');

  return response.data.data;
}

export async function enableAutoPipeline() {
  const response = await api.post('/api/auto-pipeline/enable');

  return response.data.data;
}

export async function disableAutoPipeline() {
  const response = await api.post('/api/auto-pipeline/disable');

  return response.data.data;
}
