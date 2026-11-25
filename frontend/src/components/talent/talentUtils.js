// 인력 화면에서 쓰는 순수 함수 (talentUtils.test.mjs로 검사).

export const STATUS_LABELS = {
  PROCESSING: '분석 중',
  DRAFT: '확인 대기',
  CONFIRMED: '등록됨',
  FAILED: '읽지 못함',
};

export const GRADE_LABELS = {
  JUNIOR: '초급',
  INTERMEDIATE: '중급',
  ADVANCED: '고급',
  EXPERT: '특급',
};

export const SOURCE_LABELS = { RULE: '규칙', LLM: 'AI', HUMAN: '사람' };

export const SKILL_CATEGORIES = ['LANGUAGE', 'FRAMEWORK', 'DBMS', 'OS', 'MACHINE', 'TOOL', 'MIDDLEWARE', 'OTHER'];

export const MISSING_LABELS = {
  name: '이름',
  birthYear: '생년',
  grade: '등급',
  projects: '프로젝트',
  skills: '기술',
  industries: '산업',
  totalCareer: '총 경력',
  availableFrom: '투입 가능일',
  'availableFrom.date': '투입 가능일(진행 중 프로젝트 종료일 모름)',
};

export function careerText(months) {
  if (months == null) return '모름';
  const years = Math.floor(months / 12);
  const rest = months % 12;
  if (years === 0) return `${rest}개월`;
  return rest === 0 ? `${years}년` : `${years}년 ${rest}개월`;
}

/** 투입 가능일 문구. 진행 중 프로젝트가 없으면 '바로 가능(확인 필요)', 종료일을 모르면 추측하지 않는다. */
export function availabilityText(summary) {
  if (!summary) return '';
  if (summary.availableFrom) return summary.availableFrom;
  if (summary.availableBasis === 'NO_ONGOING_PROJECT') return '진행 중 없음 (확인 필요)';
  if (summary.availableBasis === 'ONGOING_WITHOUT_END') return '진행 중 · 종료일 모름';
  return '모름';
}

export function periodText(period) {
  if (!period) return '기간 모름';
  const start = period.start || '?';
  const end = period.ongoing ? '진행 중' : (period.end || '?');
  return `${start} ~ ${end}`;
}

/**
 * 원문 글을 화면용 줄로 나눈다. 표 한 줄은 " | "로 이어져 있으므로 칸으로 다시 나눈다.
 * hits: 근거가 들어 있는 줄 번호 (근거가 여러 줄이면 첫 줄 기준).
 */
export function sourceRows(sourceText) {
  return String(sourceText || '').split('\n').map((line, index) => ({
    index,
    line,
    cells: line.includes(' | ') ? line.split(' | ') : null,
  }));
}

export function findEvidenceLine(rows, evidence) {
  if (!evidence) return -1;
  const exact = rows.findIndex((row) => row.line.includes(evidence));
  if (exact >= 0) return exact;
  const first = evidence.split('\n')[0];
  return first ? rows.findIndex((row) => row.line.includes(first)) : -1;
}

/** 확인 화면 입력값과 원래 문서를 비교해 바뀐 칸만 보낸다 (같으면 빈 객체). */
export function buildPatch(document, form) {
  const patch = {};
  const value = (key) => document?.[key]?.value ?? null;
  const name = form.name.trim() || null;
  if (name !== value('name')) patch.name = name;
  const birth = form.birthYear === '' || form.birthYear == null ? null : Number(form.birthYear);
  if (birth !== value('birthYear')) patch.birthYear = birth;
  const grade = form.grade || null;
  if (grade !== value('grade')) patch.grade = grade;

  const beforeSkills = (document?.skills || []).map((skill) => skill.name);
  const afterSkills = form.skills.map((skill) => skill.name);
  if (beforeSkills.join('\n') !== afterSkills.join('\n')) {
    patch.skills = form.skills.map((skill) => (beforeSkills.includes(skill.name) ? skill.name : { name: skill.name, category: skill.category || 'OTHER' }));
  }
  const beforeIndustries = (document?.industries || []).map((item) => item.name);
  if (beforeIndustries.join('\n') !== form.industries.join('\n')) patch.industries = [...form.industries];
  return patch;
}

export function formFromDocument(document) {
  return {
    name: document?.name?.value ?? '',
    birthYear: document?.birthYear?.value ?? '',
    grade: document?.grade?.value ?? '',
    skills: (document?.skills || []).map((skill) => ({ name: skill.name, category: skill.category })),
    industries: (document?.industries || []).map((item) => item.name),
  };
}

export function isValidBirthYear(value) {
  if (value === '' || value == null) return true;
  const number = Number(value);
  return Number.isInteger(number) && number >= 1900 && number <= 2100;
}
