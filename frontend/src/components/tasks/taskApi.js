import { api } from '../../api/client';

export async function listTasks() { return (await api.get('/api/tasks')).data.data; }
export async function importTasks(noteId) {
  return (await api.post(`/api/tasks/from-note/${encodeURIComponent(noteId)}`)).data.data;
}
export async function loadTask(id) {
  const response = await api.get(`/api/tasks/${encodeURIComponent(id)}`);
  const etag = response.headers.etag;
  if (!etag) throw new Error('수정 버전을 확인하지 못했어요. 서버 연결을 확인해 주세요.');
  return { task: response.data.data, etag };
}
export async function saveTask(task, etag) {
  return (await api.put(`/api/tasks/${encodeURIComponent(task.taskId)}`, task, { headers: { 'If-Match': etag } })).data.data;
}
export async function createTask(task) { return (await api.post('/api/tasks', task)).data.data; }
