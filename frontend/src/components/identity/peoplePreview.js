// 명시적으로 선택한 화면 미리보기 전용. 네트워크·DB·브라우저 저장소에 쓰지 않는다.
const persons = [
  { personId: 'p_preview_001', name: '가상인물_001', nameVariants: ['Synthetic Contact A'], company: '가상회사_파랑', department: '영업팀', title: '부장', emails: ['contact-a@preview.example'], phones: ['000-0000-0001'], channels: ['EMAIL', 'MEETING'], companyHistory: [{ company: '가상회사_이전', title: '차장', until: '2025-12-31' }], isSynthetic: true, photoAvailable: false },
  { personId: 'p_preview_002', name: '가상인물_001', nameVariants: [], company: '가상회사_초록', department: '개발팀', title: '과장', emails: ['contact-b@preview.example'], phones: [], channels: ['MEETING'], companyHistory: [], isSynthetic: true, photoAvailable: false },
  { personId: 'p_preview_003', name: '가상인물_003', nameVariants: [], company: '가상회사_보라', department: null, title: null, emails: [], phones: [], channels: [], companyHistory: [], isSynthetic: true, photoAvailable: false },
];
const fields = () => ({ discussion: [], customerRequests: [], concerns: [], commitments: [], nextContact: [] });
const meetings = [
  { noteId: 'mn_preview_001', sourceRefs: [{ sourceId: 'src_preview_001', sourceType: 'MEETING_NOTE' }], occurredAt: '2026-10-14T14:00:00', location: '가상회사_파랑 본사', mode: 'FULL', participants: [{ displayName: '가상인물_001', resolutionId: 'ir_preview_001', personId: 'p_preview_001' }], fields: { ...fields(), discussion: [{ text: '가상 프로젝트 일정과 제안서 준비 논의', evidence: '가상 프로젝트 일정을 검토하고 제안서 준비 사항을 논의했다.' }], commitments: [{ text: '가상 제안서 전달', evidence: '가상 제안서를 전달하기로 했다.' }, { text: '가상 검토 자료 공유', evidence: '가상 검토 자료를 공유하기로 했다.' }] }, missingFields: [], status: 'CONFIRMED', generatedBy: 'HUMAN' },
  { noteId: 'mn_preview_002', sourceRefs: [{ sourceId: 'src_preview_002', sourceType: 'EMAIL' }], occurredAt: '2026-10-09T10:30:00', location: null, mode: 'FULL', participants: [{ displayName: '가상인물_001', resolutionId: 'ir_preview_001', personId: 'p_preview_001' }], fields: { ...fields(), discussion: [{ text: '가상 후보 프로필 요청 메일', evidence: '가상 후보 프로필을 요청하는 메일을 받았다.' }] }, missingFields: [], status: 'CONFIRMED', generatedBy: 'HUMAN' },
];
function read(value, signal) {
  if (signal?.aborted) throw new DOMException('취소된 요청', 'AbortError');
  return structuredClone(value);
}
export const peoplePreviewApi = {
  async list(q = '', page = 0, signal) {
    const needle = q.trim().toLowerCase();
    return read(persons.filter(p => [p.name, ...p.nameVariants, p.company, p.title, ...p.emails, ...p.phones].filter(Boolean).some(v => v.toLowerCase().includes(needle))).slice(page * 20, page * 20 + 20), signal);
  },
  async person(id, signal) {
    const person = persons.find(p => p.personId === id);
    if (!person) throw new Error('미리보기 인물이 없습니다.');
    return read(person, signal);
  },
  async meetings(id, page = 0, signal) {
    return read(meetings.filter(note => note.participants.some(p => p.personId === id)).slice(page * 20, page * 20 + 20), signal);
  },
};
