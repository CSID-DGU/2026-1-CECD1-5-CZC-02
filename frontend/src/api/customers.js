import { api } from './client';

export async function getCustomers() {
  const response = await api.get('/api/customers');

  return response.data.data;
}

export async function getCustomerTimeline(customerContactId) {
  const response = await api.get(`/api/customers/${customerContactId}/timeline`);

  return response.data.data;
}
