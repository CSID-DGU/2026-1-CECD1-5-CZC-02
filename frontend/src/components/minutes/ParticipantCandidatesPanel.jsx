import { IdentityCandidatesPanel } from '../identity/IdentityCandidatesPanel';

/** D-017의 A 담당 영역. 서버 저장 결과만 사용하며 B 화면 틀·문항·할 일은 변경하지 않는다. */
export function ParticipantCandidatesPanel({ participants, readOnly }) {
  return <IdentityCandidatesPanel participants={participants} readOnly={readOnly} />;
}
