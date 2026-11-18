import { api } from './client';

export async function getTodayBriefing(date) {
  const response = await api.get('/api/briefings/today', { params: { date } });
  return response.data;
}

export async function getPreparationBriefing(date) {
  const response = await api.get('/api/briefings/preparation', { params: { date } });
  return response.data;
}

