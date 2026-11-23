import { api } from './client';

export async function getTodayBriefing(date) {
  const response = await api.get('/api/briefings/today', { params: { date } });
  return response.data.data;
}

export async function getPreparationBriefing(date) {
  const response = await api.get('/api/briefings/preparation', { params: { date } });
  return response.data.data;
}

export async function registerTaskOnCalendar(taskId, scheduleDateTime) {
  const response = await api.post('/api/briefings/calendar-registration', {
    taskId,
    scheduleDateTime,
  });
  return response.data.data;
}

