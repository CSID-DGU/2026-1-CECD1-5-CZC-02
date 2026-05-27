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
