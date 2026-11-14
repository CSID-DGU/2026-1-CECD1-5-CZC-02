const titles = new Set(['사원','주임','대리','과장','차장','부장','팀장','실장','이사','상무','전무','대표']);
const isTitle = token => token === '님' || titles.has(token) || (token.endsWith('님') && titles.has(token.slice(0,-1)));

// 표시용 정보 부족 검사. 이름·연락처를 새로 만들어 채우지 않는다.
export function hasIdentityClue(mention = {}) {
  const tokens = (mention.name || '').normalize('NFC').trim().split(/\s+/u).filter(Boolean);
  while (tokens.length && isTitle(tokens[0])) tokens.shift();
  while (tokens.length && isTitle(tokens.at(-1))) tokens.pop();
  const email = (mention.email || '').trim();
  const parts = email.split('@');
  const usableEmail = parts.length === 2 && parts.every(Boolean) && !/\s/u.test(email);
  const phone = (mention.phone || '').replace(/[\s().-]/gu, '');
  return tokens.length > 0 || usableEmail || /^\+?[0-9]+$/u.test(phone);
}

export function findReviewItem(participant, items = [], participants = []) {
  const byId = participant.resolutionId ? items.filter(i => i.resolution.resolutionId === participant.resolutionId) : [];
  if (participant.resolutionId) {
    if (participants.filter(p => p.resolutionId === participant.resolutionId).length > 1) return null;
    return byId.length === 1 ? byId[0] : null;
  }
  // 이름/이메일로 여러 명이 맞으면 첫 사람을 임의로 고르지 않는다.
  const name = participant.displayName;
  if (!name || participants.filter(p => p.displayName === name).length > 1) return null;
  const matching = items.filter(({ resolution: r }) => r.mention?.name === name
    || (!r.mention?.name && r.mention?.email?.toLowerCase() === name.toLowerCase()));
  return matching.length === 1 ? matching[0] : null;
}

export function personLabel(person) {
  if (!person) return '인물 정보 확인 필요';
  return [person.name, person.company, person.title].filter(Boolean).join(' · ');
}

export function registrationBody(form) {
  if (!form.name?.trim()) throw new Error('대표 이름을 입력해 주세요.');
  if (!['true','false'].includes(form.isSynthetic)) throw new Error('데이터 유형을 선택해 주세요.');
  return { name: form.name.trim(), company: form.company?.trim() || null,
    emails: form.email?.trim() ? [form.email.trim()] : [], phones: form.phone?.trim() ? [form.phone.trim()] : [],
    nameVariants: [], channels: [], companyHistory: [], isSynthetic: form.isSynthetic === 'true' };
}

export function identityError(error) {
  if (error.response?.status === 409) return '다른 화면에서 변경됐거나 이미 선택했어요. 다시 불러와 확인해 주세요.';
  return error.response?.data?.message || error.message || '저장하지 못했어요. 입력을 유지했으니 다시 확인해 주세요.';
}

// 기존 RULE 응답의 반복 안내도 후보별 근거와 분리해 참석자마다 한 번만 보여 준다.
export function groupCandidateReasons(candidates = []) {
  const notice = reason => /^후보가 \d+명입니다\./u.test(reason);
  const visible = candidates.slice(0, 3);
  return {
    notices: [...new Set(visible.flatMap(candidate => (candidate.reasons || []).filter(notice)))],
    candidates: visible.map(candidate => ({ ...candidate, reasons: (candidate.reasons || []).filter(reason => !notice(reason)) })),
  };
}
