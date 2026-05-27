export function getApiErrorMessage(error) {
  const status = error.response?.status;

  if (status === 400) {
    return '요청값 확인 필요';
  }

  if (status === 401) {
    return '로그인 만료 가능성';
  }

  if (status === 403) {
    return '권한 문제 가능성';
  }

  if (status === 404) {
    return 'API 경로 또는 데이터 없음';
  }

  if (status === 500) {
    return '백엔드 내부 오류';
  }

  return '조회 실패';
}
