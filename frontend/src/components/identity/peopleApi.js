import { api } from '../../api/client.js';

export function createPeopleApi(transport) {
  return {
    async list(q, page = 0, signal) {
      return (await transport.get('/api/persons', { params: { q, page, size: 20 }, signal })).data.data;
    },
    async person(id, signal) {
      return (await transport.get(`/api/persons/${encodeURIComponent(id)}`, { signal })).data.data;
    },
    async merges(id, page = 0, signal) {
      return (await transport.get(`/api/persons/${encodeURIComponent(id)}/merges`, { params: { page, size: 20 }, signal })).data.data;
    },
    async merge(targetId, sourcePersonId) {
      return (await transport.post(`/api/persons/${encodeURIComponent(targetId)}/merges`, { sourcePersonId, confirmed: true })).data.data;
    },
    async undoMerge(targetId, mergeId) {
      return (await transport.post(`/api/persons/${encodeURIComponent(targetId)}/merges/${encodeURIComponent(mergeId)}/undo`, { confirmed: true })).data.data;
    },
    async meetings(id, page = 0, signal) {
      return (await transport.get(`/api/persons/${encodeURIComponent(id)}/meetings`,
        { params: { page, size: 20 }, signal })).data.data;
    },
  };
}
export const peopleApi = createPeopleApi(api);
