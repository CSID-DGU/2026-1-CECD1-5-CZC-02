import assert from 'node:assert/strict';
import test from 'node:test';
import {
  availabilityText, buildPatch, careerText, findEvidenceLine, formFromDocument, isStoredTaskId, isValidBirthYear,
  requirementSummary, sameSelection, sourceRows, toggleSelection,
} from './talentUtils.js';

// 가짜 이력서 값만 쓴다.
const document = {
  name: { value: '모의석', evidence: '성명 | 모의석', source: 'RULE' },
  birthYear: null,
  grade: { value: 'INTERMEDIATE', evidence: '중급', source: 'RULE' },
  skills: [{ name: 'Java', category: 'LANGUAGE' }, { name: 'Oracle', category: 'DBMS' }],
  industries: [{ name: '금융' }],
};

test('경력 개월을 년·개월로 쓴다', () => {
  assert.equal(careerText(null), '모름');
  assert.equal(careerText(7), '7개월');
  assert.equal(careerText(24), '2년');
  assert.equal(careerText(57), '4년 9개월');
});

test('투입 가능일은 추측하지 않는다', () => {
  assert.equal(availabilityText({ availableFrom: '2027-02-01' }), '2027-02-01');
  assert.equal(availabilityText({ availableFrom: null, availableBasis: 'ONGOING_WITHOUT_END' }), '진행 중 · 종료일 모름');
  assert.equal(availabilityText({ availableFrom: null, availableBasis: null }), '모름');
});

test('고친 칸만 보낸다', () => {
  const form = formFromDocument(document);
  assert.deepEqual(buildPatch(document, form), {});
  form.grade = 'EXPERT';
  form.birthYear = '1990';
  form.skills = [{ name: 'Java', category: 'LANGUAGE' }, { name: '모의툴', category: 'TOOL' }];
  assert.deepEqual(buildPatch(document, form), {
    birthYear: 1990,
    grade: 'EXPERT',
    skills: ['Java', { name: '모의툴', category: 'TOOL' }],
  });
});

test('이름을 비우면 null로 보낸다', () => {
  const form = formFromDocument(document);
  form.name = '  ';
  assert.deepEqual(buildPatch(document, form), { name: null });
});

test('원문 표 줄을 칸으로 나누고 근거 줄을 찾는다', () => {
  const rows = sourceRows('개인이력카드\n성명 | 모의석 | 생년월일\n샘플 프로젝트 | 2020.01~2021.12');
  assert.equal(rows[0].cells, null);
  assert.deepEqual(rows[1].cells, ['성명', '모의석', '생년월일']);
  assert.equal(findEvidenceLine(rows, '성명 | 모의석'), 1);
  assert.equal(findEvidenceLine(rows, '없는 글'), -1);
  assert.equal(findEvidenceLine(rows, null), -1);
});

test('생년 입력 검사', () => {
  assert.equal(isValidBirthYear(''), true);
  assert.equal(isValidBirthYear('1990'), true);
  assert.equal(isValidBirthYear('1800'), false);
  assert.equal(isValidBirthYear('19.5'), false);
});

test('요청 조건 요약은 아는 칸만 쓴다', () => {
  assert.equal(requirementSummary({
    skills: [{ name: 'Java', required: true }, { name: 'Spring', required: true }, { name: 'React', required: false }],
    headcount: { value: 3 },
    minCareerMonths: { value: 60 },
    minGrade: null,
    startFrom: { value: '2027-02-01' },
    industries: [{ name: '금융' }],
  }), 'Java·Spring · 3명 · 경력 5년 이상 · 2027-02-01부터 · 우대 React · 금융 경험 가점');
  assert.equal(requirementSummary({ skills: [], headcount: null, minCareerMonths: null, minGrade: null, startFrom: null, industries: [] }),
    '조건을 찾지 못함');
});

test('제출 리스트 고르기는 순서를 지킨다', () => {
  assert.deepEqual(toggleSelection(['rp_2'], 'rp_1'), ['rp_2', 'rp_1']);
  assert.deepEqual(toggleSelection(['rp_2', 'rp_1'], 'rp_2'), ['rp_1']);
  assert.equal(sameSelection(['rp_1', 'rp_2'], ['rp_1', 'rp_2']), true);
  assert.equal(sameSelection(['rp_1', 'rp_2'], ['rp_2', 'rp_1']), false);
  assert.equal(isStoredTaskId('t_12'), true);
  assert.equal(isStoredTaskId('t_draft_1'), false);
});
