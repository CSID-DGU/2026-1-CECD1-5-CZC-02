import { api } from './client';

export async function getSources(params = {}) {
  const response = await api.get('/api/sources', {
    params: {
      page: 0,
      size: 30,
      ...params,
    },
  });

  return response.data.data;
}

export async function getSourceById(sourceId) {
  const response = await api.get(`/api/sources/${sourceId}`);

  return response.data.data;
}

export async function createSource(payload) {
  const response = await api.post('/api/sources', payload);

  return response.data.data;
}

export async function deleteSource(sourceId) {
  const response = await api.delete(`/api/sources/${sourceId}`);

  return response.data.data;
}
