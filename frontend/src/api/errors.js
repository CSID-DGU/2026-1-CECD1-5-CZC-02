export function getApiErrorMessage(error) {
  const status = error.response?.status;
  const responseBody = error.response?.data;
  const backendMessage = responseBody?.message;
  const backendData = responseBody?.data;
  const detail = backendData?.message || backendData?.detail || backendData?.errorCode;

  if (backendMessage || detail) {
    return [
      status ? `${status}` : null,
      backendMessage,
      detail && detail !== backendMessage ? detail : null,
    ].filter(Boolean).join(' - ');
  }

  if (status === 400) {
    return '400 - 요청값 확인 필요';
  }

  if (status === 401) {
    return '401 - 로그인 만료 가능성';
  }

  if (status === 403) {
    return '403 - 권한 문제 가능성';
  }

  if (status === 404) {
    return '404 - API 경로 또는 데이터 없음';
  }

  if (status === 500) {
    return '500 - 백엔드 내부 오류';
  }

  if (status === 502) {
    return '502 - 외부 AI/Salesmap 연동 실패';
  }

  return error.message || '요청 실패';
}
