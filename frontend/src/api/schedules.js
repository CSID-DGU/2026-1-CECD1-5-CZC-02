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

// 미팅이 끝난 뒤 결과 기록: outcome = SUCCESS(잘됨) | ON_HOLD(보류) | LOST(무산)
export async function recordScheduleOutcome(scheduleId, outcome) {
  const response = await api.patch(`/api/schedules/${scheduleId}/outcome`, { outcome });

  return response.data.data;
}
