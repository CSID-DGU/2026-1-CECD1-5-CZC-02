import { api } from './client';

export async function getMe() {
  const response = await api.get('/api/auth/me');

  return response.data.data;
}
