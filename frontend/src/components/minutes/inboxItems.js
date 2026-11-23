// 확인 대기함 요약. 값이 undefined면 불러오는 중, null이면 불러오지 못한 것이다 — 0으로 꾸미지 않는다.

/**
 * 사람이 골라야 하는 참석자 수. 규칙이 확정했거나 사람이 이미 고른 참석자는 세지 않는다.
 * 식별 결과가 아예 없는 참석자(식별 실패 등)는 끝난 것이 아니므로 확인 필요로 센다.
 */
export function pendingPeopleCount(items, participantCount = items.length) {
  const undecided = items.filter(({ resolution }) => resolution?.decidedBy !== 'HUMAN' && resolution?.decision !== 'CONFIRMED').length;
  return undecided + Math.max(0, participantCount - items.length);
}

function sumKnown(values) {
  if (values.some((value) => value === null)) {
    return null;
  }
  if (values.some((value) => value === undefined)) {
    return undefined;
  }
  return values.reduce((total, value) => total + value, 0);
}

export function buildInboxSummary(notes, tasks, identities) {
  const noteIds = new Set(notes.map((note) => note.noteId));
  const draftTasks = Array.isArray(tasks) ? tasks.filter((task) => task.status === 'DRAFT') : tasks;
  const rows = notes.map((note) => {
    const items = identities[note.noteId];
    return {
      note,
      draftTasks: Array.isArray(draftTasks) ? draftTasks.filter((task) => task.noteId === note.noteId).length : draftTasks,
      pendingPeople: Array.isArray(items) ? pendingPeopleCount(items, note.participantNames?.length ?? items.length) : items,
    };
  });
  return {
    rows,
    // 보관한 회의록처럼 대기함 목록에 없는 회의록에 남은 초안
    otherDraftTasks: Array.isArray(draftTasks) ? draftTasks.filter((task) => !noteIds.has(task.noteId)) : [],
    totals: {
      notes: notes.length,
      tasks: Array.isArray(draftTasks) ? draftTasks.length : draftTasks,
      people: sumKnown(rows.map((row) => row.pendingPeople)),
    },
  };
}

/** 회의록마다 부르는 요청이 한꺼번에 몰리지 않게 동시에 limit개까지만 실행한다. */
export async function runWithLimit(items, limit, run) {
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const item = items[next];
      next += 1;
      await run(item);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
}
