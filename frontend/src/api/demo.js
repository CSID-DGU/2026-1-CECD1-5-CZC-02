import { api } from './client';

export async function getDemoStatus() {
  const response = await api.get('/api/dev/demo/status');

  return response.data.data;
}

export async function enableDemo() {
  const response = await api.post('/api/dev/demo/enable');

  return response.data.data;
}

export async function disableDemo() {
  const response = await api.post('/api/dev/demo/disable');

  return response.data.data;
}

export async function resetDemo() {
  const response = await api.post('/api/dev/demo/reset');

  return response.data.data;
}
