import { TASK_TYPES } from '../tasks/taskUtils';

// 할 일 보드와 같은 이름을 쓴다
export function taskTypeLabel(taskType) {
  return TASK_TYPES[taskType] || (taskType ? '기타' : '분류 없음');
}
