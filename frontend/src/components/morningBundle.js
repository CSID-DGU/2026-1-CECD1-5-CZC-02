// 아침 묶음 브리핑의 내용 만들기. 시간·날짜는 KST로 판단한다 (D-007).
const MAX_ITEMS = 6;

function kstParts(now) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(now);
  return Object.fromEntries(parts.map(({ type, value }) => [type, value]));
}

export function kstDate(now) {
  const { year, month, day } = kstParts(now);
  return `${year}-${month}-${day}`;
}

export function isPastMorningTime(now, morningTime) {
  const { hour, minute } = kstParts(now);
  return `${hour}:${minute}` >= morningTime;
}

function taskItems(tasks, label) {
  return (tasks || []).map((task) => ({
    id: `${label}:${task.taskId}`,
    dateTime: '',
    title: task.content,
    meta: task.dueDate ? `${label} · ${task.dueDate}` : label,
  }));
}

/** 알릴 것이 없으면 null. 할 일 ID·종류 코드 같은 내부 값은 넣지 않는다. */
export function buildMorningBundle(briefing, schedules, today) {
  const overdue = briefing?.overdue || [];
  const dueToday = briefing?.dueToday || [];
  const tomorrow = briefing?.tomorrowPreparation || [];
  const pending = briefing?.pendingConfirmation || [];
  const todaySchedules = (schedules || [])
    .filter((schedule) => schedule.status !== 'CANCELED' && schedule.scheduleDateTime?.startsWith(today))
    .sort((left, right) => left.scheduleDateTime.localeCompare(right.scheduleDateTime))
    .map((schedule) => ({
      id: `schedule:${schedule.scheduleId}`,
      dateTime: schedule.scheduleDateTime,
      title: schedule.title || '일정',
      meta: `오늘 일정 · ${schedule.scheduleDateTime.slice(11, 16)}`,
    }));
  const all = [
    ...taskItems(overdue, '기한 지남'),
    ...todaySchedules,
    ...taskItems(dueToday, '오늘 기한'),
    ...taskItems(tomorrow, '내일 준비'),
    ...taskItems(pending, '확인 대기'),
  ];
  if (all.length === 0) {
    return null;
  }
  const items = all.slice(0, MAX_ITEMS);
  if (all.length > MAX_ITEMS) {
    items.push({ id: 'more', dateTime: '', title: `외 ${all.length - MAX_ITEMS}건`, meta: '오늘 브리핑에서 전체 보기' });
  }
  const counts = [
    ['기한 지남', overdue.length],
    ['오늘 일정', todaySchedules.length],
    ['오늘 기한', dueToday.length],
    ['내일 준비', tomorrow.length],
    ['확인 대기', pending.length],
  ].filter(([, count]) => count > 0).map(([label, count]) => `${label} ${count}`);
  return { heading: '아침 묶음 브리핑', label: '오늘 챙길 일', title: counts.join(' · '), items };
}
