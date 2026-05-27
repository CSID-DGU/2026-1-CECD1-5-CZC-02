import { api } from './client';

// There is no global salesmap record list endpoint yet. Use getSalesmapRecordsByAnalysis(analysisId).
export async function getSalesmapRecordsByAnalysis(analysisId) {
  const response = await api.get(`/api/salesmap/analysis/${analysisId}`);

  return response.data.data;
}

// TODO: Backend does not provide GET /api/salesmap/records/{recordId} yet.
export async function getSalesmapRecordById(recordId) {
  throw new Error(`Salesmap record detail API is not available yet. recordId=${recordId}`);
}
