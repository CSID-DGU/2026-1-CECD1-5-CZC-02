import { api } from '../../api/client.js';

export function createIdentityApi(transport) {
  const route = noteId => `/api/person-identities/for-note/${encodeURIComponent(noteId)}`;
  return {
    async list(noteId, signal) { return (await transport.get(route(noteId), { signal })).data.data; },
    async person(id, signal) { return (await transport.get(`/api/persons/${encodeURIComponent(id)}`, { signal })).data.data; },
    async search(q, page = 0) { return (await transport.get('/api/persons', { params: { q, page, size: 20 } })).data.data; },
    async select(noteId, id, body, etag) {
      if (!etag) throw new Error('저장 버전이 없어요. 최신 결과를 다시 불러와 주세요.');
      const response = await transport.post(`${route(noteId)}/${encodeURIComponent(id)}/selection`, body,
        { headers: { 'If-Match': etag } });
      const item = response.data.data;
      if (item?.resolution?.resolutionId !== id || item.resolution.decidedBy !== 'HUMAN' || !item.resolution.personId)
        throw new Error('저장 결과를 확인하지 못했어요. 다시 불러와 확인해 주세요.');
      return item;
    },
  };
}
export const identityApi = createIdentityApi(api);
