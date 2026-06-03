import { api } from './client';

export async function getSchedules(params = {}) {
  const response = await api.get('/api/schedules', { params });

  return response.data.data;
}

export async function createSchedule(payload) {
  const response = await api.post('/api/schedules', payload);

  return response.data.data;
}

export async function updateSchedule(scheduleId, payload) {
  const response = await api.patch(`/api/schedules/${scheduleId}`, payload);

  return response.data.data;
}

export async function deleteSchedule(scheduleId) {
  const response = await api.delete(`/api/schedules/${scheduleId}`);

  return response.data.data;
}
