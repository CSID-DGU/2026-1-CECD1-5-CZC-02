export function duplicateNames(people) {
  const counts = new Map();
  for (const person of people) {
    const key = person.name.normalize('NFC').trim();
    counts.set(key, (counts.get(key) || 0) + 1);
  }
  return new Set([...counts].filter(([, count]) => count > 1).map(([name]) => name));
}

// 근거는 수정/자동 완료 처리하지 않는다. 이행 상태를 모르는 약속 기록이다.
export function commitmentRecords(meetings, personId) {
  return meetings.flatMap(note => {
    if (note.status !== 'CONFIRMED') return [];
    if (note.mode === 'LIGHT') return (note.perPerson || [])
      .filter(row => row.personId === personId && row.followUp)
      .map(row => ({ ...row.followUp, noteId: note.noteId, occurredAt: note.occurredAt, scope: '인물별 후속 기록' }));
    const ids = [...new Set((note.participants || []).map(row => row.personId).filter(Boolean))];
    if (!ids.includes(personId)) return [];
    // 다인 회의의 약속을 특정 한 사람에게 귀속시키지 않는다.
    return (note.fields?.commitments || []).map(item => ({ ...item, noteId: note.noteId,
      occurredAt: note.occurredAt, scope: (note.participants || []).length === 1 ? '미팅 약속 기록' : '공동 미팅 약속 · 개인 귀속 미확인' }));
  });
}
