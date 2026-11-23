import { api } from './client';

// 앱 안 알림의 시간 설정과 횟수 판정. 아침 묶음 하루 1번, 즉시 알림 하루 3건은 서버가 센다.

export async function getNotificationSettings() {
  const response = await api.get('/api/notifications/settings');
  return response.data.data;
}

export async function updateNotificationSettings(morningTime) {
  const response = await api.put('/api/notifications/settings', { morningTime });
  return response.data.data;
}

export async function claimMorningBundle() {
  const response = await api.post('/api/notifications/morning-bundle/claim');
  return response.data.data;
}

export async function claimImmediateNotification(key) {
  const response = await api.post('/api/notifications/immediate/claim', { key });
  return response.data.data;
}
