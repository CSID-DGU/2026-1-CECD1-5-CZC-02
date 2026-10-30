import { api } from './client';

// 회의록 API (docs/api-spec.md "Meeting Notes"). noteId는 "mn_" + PK.

// 메모 → 회의록 초안. AI 모듈 응답이 최대 수 분 걸릴 수 있다 (D-019).
export async function createMeetingNoteDraft(payload) {
  const response = await api.post('/api/meeting-notes', payload);

  return response.data.data;
}

// 메일(같은 스레드 전체) → 회의록 초안. sourceId는 메일함의 원본 숫자 ID.
// 이미 만든 메일이면 { created: false, noteId } 만 온다.
export async function createMeetingNoteFromSource(sourceId) {
  const response = await api.post(`/api/meeting-notes/from-source/${sourceId}`);

  return response.data.data;
}

// status: 'DRAFT' | 'CONFIRMED' | undefined(전체)
export async function getMeetingNotes(status) {
  const response = await api.get('/api/meeting-notes', {
    params: status ? { status } : {},
  });

  return response.data.data;
}

export async function getMeetingNote(noteId) {
  const response = await api.get(`/api/meeting-notes/${noteId}`);

  return response.data.data;
}

// 고친 문항만 보낸다. 바뀐 값마다 서버가 수정 기록(revision)을 남긴다.
export async function updateMeetingNote(noteId, payload) {
  const response = await api.patch(`/api/meeting-notes/${noteId}`, payload);

  return response.data.data;
}

export async function confirmMeetingNote(noteId) {
  const response = await api.post(`/api/meeting-notes/${noteId}/confirm`);

  return response.data.data;
}
