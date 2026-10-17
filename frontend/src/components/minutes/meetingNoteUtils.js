// 회의록 화면(② 미팅 기록, ③ 확인 화면) 공통 값과 작은 규칙.

// contracts/meeting_note.schema.json의 fields 5문항 (D-009)
export const FIELD_DEFS = [
  { key: 'discussion', label: '논의 내용' },
  { key: 'customerRequests', label: '고객 요청' },
  { key: 'concerns', label: '우려·이슈' },
  { key: 'commitments', label: '약속' },
  { key: 'nextContact', label: '다음 접촉' },
];

// LIGHT 모드 사람별 3칸
export const PER_PERSON_SLOTS = [
  { key: 'status', label: '근황' },
  { key: 'request', label: '요청 사항' },
  { key: 'followUp', label: '후속 조치' },
];

export const MODE_OPTIONS = [
  { value: '', label: '자동 (메모 모양으로 판단)' },
  { value: 'FULL', label: '일반 회의록 (5문항)' },
  { value: 'LIGHT', label: '여러 명 방문 · 간단 양식 (사람별 3칸)' },
];

export function noteTitle(summaryOrNote) {
  const names = summaryOrNote.participantNames
    || (summaryOrNote.participants || []).map((participant) => participant.displayName);
  if (names && names.length > 0) {
    return names.join(', ');
  }
  return summaryOrNote.location || summaryOrNote.sourceTitle || '미팅 메모';
}

// "2026-10-14T15:00:00" → "10/14 15:00". 시각은 KST, 시간대 표기 없음 (D-007)
export function formatMeetingDateTime(value) {
  if (!value) {
    return '시각 모름';
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
  if (!match) {
    return value;
  }
  return `${Number(match[2])}/${Number(match[3])} ${match[4]}:${match[5]}`;
}

// <input type="datetime-local">에 넣는 현재 시각 (분 단위)
export function nowForDateTimeInput() {
  const now = new Date();
  const pad = (number) => String(number).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

/**
 * 원문을 줄·문장 단위로 나눈다. 근거(evidence)는 원문 글자 그대로여야 하므로
 * 나눈 조각은 모두 원문의 부분 문자열이다 (앞뒤 공백만 뺌).
 */
export function splitSentences(content) {
  if (!content) {
    return [];
  }
  const sentences = [];
  content.split(/\r?\n/).forEach((line) => {
    line.split(/(?<=[.!?。])\s+/).forEach((piece) => {
      const sentence = piece.trim();
      if (sentence.length >= 2 && !sentences.includes(sentence)) {
        sentences.push(sentence);
      }
    });
  });
  return sentences;
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** 원문에서 근거 위치를 찾는다. 공백·줄바꿈 차이만 허용한다 (AI 모듈 검증과 같은 기준). */
export function findEvidenceRange(content, evidence) {
  if (!content || !evidence) {
    return null;
  }
  const exact = content.indexOf(evidence);
  if (exact >= 0) {
    return { start: exact, end: exact + evidence.length };
  }
  const words = evidence.trim().split(/\s+/).map(escapeRegExp);
  if (words.length === 0 || !words[0]) {
    return null;
  }
  const match = new RegExp(words.join('\\s+')).exec(content);
  return match ? { start: match.index, end: match.index + match[0].length } : null;
}

export function cloneFields(fields) {
  return FIELD_DEFS.reduce((result, { key }) => ({ ...result, [key]: [...(fields?.[key] || [])] }), {});
}

export function sameJson(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}
