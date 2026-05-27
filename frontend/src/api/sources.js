import { api } from './client';

export async function getSources() {
  const response = await api.get('/api/sources');

  return response.data.data;
}

export async function getSourceById(sourceId) {
  const response = await api.get(`/api/sources/${sourceId}`);

  return response.data.data;
}
