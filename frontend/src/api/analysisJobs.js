import { api } from './client';

export async function startBulkAnalysis() {
  const response = await api.post('/api/analysis-jobs/bulk');

  return response.data;
}

export async function getActivity(since = 0) {
  const response = await api.get('/api/analysis-jobs/activity', { params: { since } });

  return response.data.data;
}
