import { useState } from 'react';
import identityResolutionSamples from './samples/identity_resolution.example.json';
import personSamples from './samples/person.example.json';

/*
 * ③ 확인 화면의 참석자 후보 영역 — A 담당 자리 (D-017).
 * A의 식별 API·컴포넌트가 생기면(D-022: 백엔드 연결 10/28) 이 파일을 A의 컴포넌트로 바꾼다.
 * 그 전까지는 초안 만들기 응답의 identityResolutions(지금은 백엔드 가짜 구현 = contracts 샘플)를,
 * 없으면 contracts/examples 샘플 복사본(samples/)을 이름으로 맞춰 보여준다.
 * 여기서 고른 값은 아직 저장되지 않는다 (A API가 decidedBy=HUMAN + 수정 기록을 남길 예정).
 */

function personLabel(personId) {
  const person = personSamples.find((item) => item.personId === personId);
  if (!person) {
    return personId;
  }
  return [person.name, person.company, [person.department, person.title].filter(Boolean).join(' ')]
    .filter(Boolean)
    .join(' · ');
}

// D-022 ④: 이름·이메일·전화가 모두 없으면 신규 등록이 아니라 "정보 부족"
function isInfoMissing(mention) {
  return !mention?.name && !mention?.email && !mention?.phone;
}

function Reasons({ reasons }) {
  if (!reasons || reasons.length === 0) {
    return null;
  }
  return (
    <span className="mt-1 flex flex-wrap gap-1">
      {reasons.map((reason) => (
        <span key={reason} className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">{reason}</span>
      ))}
    </span>
  );
}

function findResolution(participant, resolutions) {
  return resolutions.find((item) => participant.resolutionId && item.resolutionId === participant.resolutionId)
    || resolutions.find((item) => item.mention?.name === participant.displayName)
    || null;
}

function ParticipantRow({ participant, resolution, readOnly }) {
  const [choice, setChoice] = useState(resolution?.personId || '');

  if (!resolution) {
    return (
      <div className="rounded-xl border border-dashed border-amber-300 bg-amber-50/60 px-3 py-2 text-xs text-amber-700">
        <span className="font-semibold text-slate-800">{participant.displayName}</span> · 식별 결과 없음 — 확인 필요
      </div>
    );
  }

  if (resolution.decision === 'CONFIRMED') {
    return (
      <div className="rounded-xl border border-emerald-200 bg-emerald-50/50 px-3 py-2 text-left">
        <p className="text-sm text-slate-800">
          <span className="font-semibold">{participant.displayName}</span> → {personLabel(resolution.personId)}
        </p>
        <Reasons reasons={resolution.candidates?.[0]?.reasons} />
      </div>
    );
  }

  if (resolution.decision === 'NEW' || (resolution.candidates || []).length === 0) {
    return isInfoMissing(resolution.mention) ? (
      <div className="rounded-xl border border-dashed border-amber-300 bg-amber-50/60 px-3 py-2 text-xs text-amber-700">
        <span className="font-semibold text-slate-800">{participant.displayName || '이름 없음'}</span> · 정보 부족 — 확인 필요
      </div>
    ) : (
      <div className="rounded-xl bg-violet-50 px-3 py-2 text-xs text-violet-700">
        <span className="font-semibold">{participant.displayName}</span>
        {resolution.mention?.company ? ` · ${resolution.mention.company}` : ''} · 대장에 없음 → 새 사람으로 등록 제안
      </div>
    );
  }

  // CANDIDATES: 점수 대신 이유를 보여준다 (D-022, 기준 모델은 score가 null)
  return (
    <fieldset className="space-y-1.5">
      <legend className="mb-1 text-xs font-semibold text-slate-600">
        참석자 — &quot;{participant.displayName}&quot;은 누구인가요?
      </legend>
      {resolution.candidates.map((candidate) => (
        <label
          key={candidate.personId}
          className={`flex cursor-pointer items-start gap-2 rounded-xl border px-3 py-2 text-left ${
            choice === candidate.personId ? 'border-blue-400 bg-blue-50/50' : 'border-slate-200 bg-white'
          }`}
        >
          <input
            type="radio"
            name={`participant-${resolution.resolutionId}`}
            checked={choice === candidate.personId}
            disabled={readOnly}
            onChange={() => setChoice(candidate.personId)}
            className="mt-1"
          />
          <span className="min-w-0">
            <span className="block text-sm font-semibold text-slate-800">{personLabel(candidate.personId)}</span>
            <Reasons reasons={candidate.reasons} />
          </span>
        </label>
      ))}
      <label
        className={`flex cursor-pointer items-start gap-2 rounded-xl border px-3 py-2 text-left ${
          choice === 'NEW' ? 'border-blue-400 bg-blue-50/50' : 'border-slate-200 bg-white'
        }`}
      >
        <input
          type="radio"
          name={`participant-${resolution.resolutionId}`}
          checked={choice === 'NEW'}
          disabled={readOnly}
          onChange={() => setChoice('NEW')}
          className="mt-1"
        />
        <span>
          <span className="block text-sm font-semibold text-slate-800">새 사람으로 등록</span>
          <span className="text-[11px] text-slate-500">목록에 없는 사람이면 선택</span>
        </span>
      </label>
    </fieldset>
  );
}

export function ParticipantCandidatesPanel({ participants, identityResolutions, readOnly }) {
  const resolutions = identityResolutions || identityResolutionSamples;

  if (!participants || participants.length === 0) {
    return (
      <section className="space-y-1.5">
        <h4 className="text-xs font-semibold text-slate-600">참석자</h4>
        <p className="rounded-xl border border-dashed border-amber-300 bg-amber-50/60 px-3 py-2 text-xs text-amber-700">
          만난 사람을 입력하지 않았어요 · 확인 필요
        </p>
      </section>
    );
  }

  return (
    <section className="space-y-2">
      {participants.map((participant) => (
        <ParticipantRow
          key={participant.displayName}
          participant={participant}
          resolution={findResolution(participant, resolutions)}
          readOnly={readOnly}
        />
      ))}
      <p className="text-[11px] text-slate-400">
        참석자 식별은 A 영역이에요. 아직 A 연결 전이라 샘플 결과이고, 여기서 고른 값은 저장되지 않아요.
      </p>
    </section>
  );
}
