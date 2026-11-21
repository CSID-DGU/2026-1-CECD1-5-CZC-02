import { api } from './client';

export async function getTodayBriefing(date) {
  const response = await api.get('/api/briefings/today', { params: { date } });
  return response.data;
}

