import { api } from './client';

export async function getSchedules() {
  const response = await api.get('/api/schedules');

  return response.data.data;
}

export async function createSchedule(payload) {
  const response = await api.post('/api/schedules', payload);

  return response.data.data;
}
