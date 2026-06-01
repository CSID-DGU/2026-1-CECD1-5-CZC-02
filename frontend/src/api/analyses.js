import { api } from './client';

// There is no global analysis list endpoint yet. Use getAnalysesBySource(sourceId).
export async function getAnalysesBySource(sourceId) {
  const response = await api.get(`/api/analysis/source/${sourceId}`);

  return response.data.data;
}

export async function getAnalysisById(analysisId) {
  const response = await api.get(`/api/analysis/${analysisId}`);

  return response.data.data;
}

// Analysis creation exists on the backend, but UI trigger is intentionally deferred
// until the source analysis workflow is finalized.
export async function createAnalysis(payload) {
  const response = await api.post('/api/analysis', payload);

  return response.data.data;
}

export async function createGroupAnalysis(payload) {
  const response = await api.post('/api/analysis/group', payload);

  return response.data.data;
}
