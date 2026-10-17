import { useState } from 'react';
import taskSamples from './samples/task.example.json';

/*
 * ③ 확인 화면의 할 일 영역 — C 담당 자리 (D-017).
 * C의 할 일 API·컴포넌트가 생기면 이 파일을 C의 컴포넌트로 바꾼다.
 * 그 전까지는 초안 만들기 응답의 tasks(지금은 백엔드 가짜 구현 = contracts 샘플)를, 없으면 샘플 복사본을 보여준다.
 * 여기서 바꾼 값은 저장되지 않는다.
 */

// docs/project/task-types.md 11부류
const TASK_TYPE_LABELS = {
  MEETING_PREP: '미팅 준비',
  VISIT_PREP: '방문·출장 준비',
  CONTRACT_PREP: '계약 준비',
  RENEWAL_INTERVIEW: '만료 대응 면담',
  PROPOSAL_SUBMISSION: '제안서·인력 제출',
  REJECTION_NOTICE: '미채택 통보',
  DOC_COLLECTION: '서류 회수',
  END_EVALUATION: '종료 평가',
  PROFITABILITY_REVIEW: '수익성 검토',
  SOURCING_START: '인력 소싱 착수',
  NONE: '해당 없음',
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function formatDueDate(value) {
  if (!value) {
    return '기한 없음';
  }
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  return `${month}/${day} (${WEEKDAYS[date.getDay()]})`;
}

function Chip({ className, children }) {
  return <span className={`rounded-md px-1.5 py-0.5 text-[11px] font-semibold ${className}`}>{children}</span>;
}

function TaskCard({ task, selected, readOnly, onSelectEvidence }) {
  const [checked, setChecked] = useState(true);
  const [taskType, setTaskType] = useState(task.taskType);

  return (
    <div
      onClick={() => onSelectEvidence(task.evidence)}
      className={`cursor-pointer rounded-xl border p-3 text-left transition-colors ${
        selected ? 'border-amber-300 bg-amber-50/60 ring-2 ring-amber-100' : 'border-slate-200 bg-white hover:border-slate-300'
      }`}
    >
      <label className="flex items-start gap-2" onClick={(event) => event.stopPropagation()}>
        <input
          type="checkbox"
          checked={checked}
          disabled={readOnly}
          onChange={(event) => setChecked(event.target.checked)}
          className="mt-1"
        />
        <span className={`text-sm font-semibold ${checked ? 'text-slate-800' : 'text-slate-400 line-through'}`}>{task.content}</span>
      </label>
      <div className="mt-2 grid grid-cols-3 gap-1.5 text-[11px] text-slate-500">
        <div>
          <p>담당</p>
          <p className="mt-0.5 rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-700">
            {task.assignee?.startsWith('user_') ? '나' : (task.assignee || '미정')}
          </p>
        </div>
        <div>
          <p>기한</p>
          <p className="mt-0.5 rounded-lg border border-slate-200 px-2 py-1 text-xs text-slate-700">{formatDueDate(task.dueDate)}</p>
        </div>
        <div>
          <p>종류</p>
          <select
            value={taskType}
            disabled={readOnly}
            onClick={(event) => event.stopPropagation()}
            onChange={(event) => setTaskType(event.target.value)}
            className="mt-0.5 w-full rounded-lg border border-slate-200 bg-white px-1 py-1 text-xs text-slate-700"
          >
            {Object.entries(TASK_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>
      </div>
      <div className="mt-2 flex flex-wrap gap-1">
        {task.commitmentKind === 'EXPLICIT' && <Chip className="bg-emerald-50 text-emerald-700">명시적 약속</Chip>}
        {task.commitmentKind === 'IMPLICIT' && <Chip className="bg-violet-50 text-violet-700">암묵적 후속</Chip>}
        {task.dueDateIsDefault && <Chip className="bg-amber-50 text-amber-700">기한은 기본값</Chip>}
      </div>
    </div>
  );
}

export function TaskDraftPanel({ tasks, selectedEvidence, readOnly, onSelectEvidence }) {
  const items = tasks || taskSamples;

  return (
    <div className="space-y-2">
      {items.length === 0 && <p className="text-sm text-slate-400">뽑힌 할 일이 없어요.</p>}
      {items.map((task) => (
        <TaskCard
          key={task.taskId}
          task={task}
          readOnly={readOnly}
          selected={selectedEvidence === task.evidence}
          onSelectEvidence={onSelectEvidence}
        />
      ))}
      <p className="text-[11px] text-slate-400">
        할 일은 C 영역이에요. 아직 C 연결 전이라 샘플 할 일이고, 여기서 바꾼 값은 저장되지 않아요.
      </p>
    </div>
  );
}
