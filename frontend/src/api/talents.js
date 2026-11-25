import { api } from './client';

// 인력 후보 풀 API (/api/talents). profileId는 "rp_" + PK. 고객 담당자(/api/persons)와 따로다.

function readAsBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

// 접수만 하고 바로 돌아온다. 분석이 끝나면 PROCESSING → DRAFT(또는 FAILED).
export async function uploadResume(file) {
  const contentBase64 = await readAsBase64(file);
  const response = await api.post('/api/talents/resumes', { fileName: file.name, contentBase64 });
  return response.data.data;
}

// params: { status, q, skills('Java,Oracle'), minCareerMonths, industry, availableBy('YYYY-MM-DD') }
export async function listTalents(params = {}) {
  const clean = Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value != null));
  const response = await api.get('/api/talents', { params: clean });
  return response.data.data;
}

export async function getTalent(profileId) {
  const response = await api.get(`/api/talents/${profileId}`);
  return response.data.data;
}

// 고친 칸만 보낸다: { name, birthYear, grade, skills: ['Java', { name, category }], industries: ['금융'] }
export async function updateTalent(profileId, patch) {
  const response = await api.patch(`/api/talents/${profileId}`, patch);
  return response.data.data;
}

export async function confirmTalent(profileId) {
  const response = await api.post(`/api/talents/${profileId}/confirm`);
  return response.data.data;
}

export async function deleteTalent(profileId) {
  await api.delete(`/api/talents/${profileId}`);
}

// 확정한 후보만 CSV로 받는다 (SALESMAP 연락처 가져오기용). 자동 전송하지 않는다.
export async function downloadTalentCsv() {
  const response = await api.get('/api/talents/export.csv', { responseType: 'blob' });
  const url = URL.createObjectURL(response.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = '인력후보.csv';
  link.click();
  URL.revokeObjectURL(url);
}

// ── 후보 추천 · 제출 리스트 ──
// 추천은 백엔드 규칙 계산(LLM 없음). 제출 리스트에는 담당자가 고른 후보만 저장한다.

export async function listMatchTasks() {
  const response = await api.get('/api/talents/match/tasks');
  return response.data.data;
}

export async function matchForTask(taskId) {
  const response = await api.get('/api/talents/match', { params: { taskId } });
  return response.data.data;
}

// taskId 없이 text만 보내면 요청 글만으로 찾는다. 둘 다 보내면 할 일은 그대로 두고 고친 글로 다시 계산한다.
export async function matchText({ taskId, text }) {
  const response = await api.post('/api/talents/match', { taskId: taskId || null, text });
  return response.data.data;
}

export async function saveSubmission(taskId, profileIds) {
  const response = await api.put(`/api/talents/submissions/${taskId}`, { profileIds });
  return response.data.data;
}

// 미팅 준비 브리핑 한 칸: 같은 할 일 · 같은 회의록 · 같은 고객 담당자의 제출 리스트
export async function relatedSubmissions(taskId) {
  const response = await api.get(`/api/talents/submissions/${taskId}/related`);
  return response.data.data;
}
