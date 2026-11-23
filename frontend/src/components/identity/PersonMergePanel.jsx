import { useState } from 'react';
import { identityError, personLabel } from './identityUtils.js';

export function MergeComparison({ target, source }) {
  return <div className="people-merge-comparison">
    {[['유지할 대표 인물', target], ['합칠 인물 · 원본 보존', source]].map(([label, person]) =>
      <section key={label}><h4>{label}</h4><strong>{personLabel(person)}</strong><p>{person.personId}</p>
        <p>이메일: {person.emails?.join(' · ') || '미등록'}</p><p>전화: {person.phones?.join(' · ') || '미등록'}</p>
        <p>별칭: {person.nameVariants?.join(' · ') || '미등록'}</p><p>부서: {person.department || '미등록'} · 직함: {person.title || '미등록'}</p>
        <p>이전 소속: {person.companyHistory?.map(h => [h.company, h.title, h.until].filter(Boolean).join(' · ')).join(' / ') || '미등록'}</p>
      </section>)}
  </div>;
}

export function PersonMergePanel({ person, history, apiClient, onChanged }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [page, setPage] = useState(0);
  const [searched, setSearched] = useState(false);
  const [more, setMore] = useState(false);
  const [source, setSource] = useState(null);
  const [confirmed, setConfirmed] = useState(false);
  const [undoId, setUndoId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [older, setOlder] = useState([]);
  const [auditPage, setAuditPage] = useState(1);
  const [auditMore, setAuditMore] = useState(history.records.length === 20);
  const audit = [...history.records, ...older];
  async function search(event, next = 0) {
    event?.preventDefault(); setBusy(true); setError(''); setSource(null); setConfirmed(false);
    try {
      const rows = await apiClient.list(query, next);
      setResults(rows.filter(p => p.personId !== person.personId)); setPage(next); setMore(rows.length === 20); setSearched(true);
    } catch (e) { setError(e.response?.data?.message || identityError(e)); }
    finally { setBusy(false); }
  }
  async function merge() {
    if (!source || !confirmed || busy) return;
    setBusy(true); setError('');
    try { await apiClient.merge(person.personId, source.personId); onChanged(); }
    catch (e) { setError(e.response?.data?.message || identityError(e)); }
    finally { setBusy(false); }
  }
  async function undo(record) {
    if (undoId !== record.mergeId || busy) return;
    setBusy(true); setError('');
    try { await apiClient.undoMerge(record.targetPersonId, record.mergeId); onChanged(); }
    catch (e) { setError(e.response?.data?.message || identityError(e)); }
    finally { setBusy(false); }
  }
  async function loadOlder() {
    setBusy(true); setError('');
    try {
      const found = await apiClient.merges(person.personId, auditPage);
      setOlder(old => [...old, ...found.records]); setAuditPage(old => old + 1); setAuditMore(found.records.length === 20);
    } catch (e) { setError(e.response?.data?.message || identityError(e)); }
    finally { setBusy(false); }
  }
  return <section className="people-merge" aria-label="인물 병합·되돌리기">
    <button className="people-button" aria-expanded={open} disabled={busy} onClick={() => setOpen(value => !value)}>중복 인물 병합·기록 {audit.filter(item => item.active).length > 0 && '· 병합 기록 있음'}</button>
    {error && <p className="people-error" role="alert">{error}</p>}
    {open && <div className="people-merge-body">
      <h3>이 인물을 대표로 중복 인물 합치기</h3>
      <p className="people-notice">이름이 같아도 다른 사람일 수 있어요. 두 인물을 직접 비교해 주세요. 대표 이름·현재 소속은 유지하고, 등록된 별칭·연락처·이전 소속과 미팅 이력을 함께 보여 줘요. 원본은 보존하며 되돌릴 수 있어요.</p>
      <form className="people-merge-search" onSubmit={search}><label>합칠 인물 검색<input value={query} onChange={e => { setQuery(e.target.value); setSearched(false); setResults([]); setSource(null); setConfirmed(false); }} maxLength={200} disabled={busy} placeholder="이름 · 회사 · 이메일" /></label><button className="people-button" disabled={busy}>검색</button></form>
      {searched && !results.length && <p>이 페이지에 병합할 인물이 없어요.</p>}
      <ul className="people-merge-results">{results.map(item => <li key={item.personId}><label><input type="radio" name="merge-source" checked={source?.personId === item.personId} disabled={busy} onChange={() => { setSource(item); setConfirmed(false); }} />{personLabel(item)} · {item.personId}</label></li>)}</ul>
      {searched && <div className="people-merge-actions"><button className="people-button" disabled={busy || page === 0} onClick={e => search(e, page - 1)}>이전</button><button className="people-button" disabled={busy || !more} onClick={e => search(e, page + 1)}>다음</button></div>}
      {source && <><MergeComparison target={person} source={source} />
        <label className="people-merge-confirm"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} />두 기록이 같은 사람임을 확인했고, {person.name}({person.personId})을 대표로 유지합니다.</label>
        <button className="people-button people-merge-submit" disabled={busy || !confirmed} onClick={merge}>{busy ? '처리 중…' : '확인한 두 인물 병합하기'}</button></>}
      <h3>병합·되돌리기 감사 기록</h3>
      {!audit.length && <p>병합 기록이 없어요.</p>}
      {audit.map(record => <article className="people-merge-audit" key={record.mergeId}>
        <p><strong>{record.sourceBefore.name} ({record.sourcePersonId}) → {record.targetBefore.name} ({record.targetPersonId})</strong></p>
        <p>병합: 사용자 {record.mergedBy} · {record.mergedAt.replace('T', ' ')} (KST)</p>
        {!record.active && <p>되돌림: 사용자 {record.undoneBy} · {record.undoneAt.replace('T', ' ')} (KST)</p>}
        <details><summary>병합 전 정보 보기</summary><MergeComparison target={record.targetBefore} source={record.sourceBefore} /></details>
        {record.active && (undoId === record.mergeId ? <div className="people-merge-actions"><span>두 인물을 다시 분리할까요? 미팅 원본은 보존됩니다.</span><button className="people-button" disabled={busy} onClick={() => undo(record)}>병합 되돌리기 확인</button><button className="people-button" disabled={busy} onClick={() => setUndoId(null)}>취소</button></div>
          : <button className="people-button" disabled={busy} onClick={() => setUndoId(record.mergeId)}>이 병합 되돌리기</button>)}
      </article>)}
      {auditMore && <button className="people-button" disabled={busy} onClick={loadOlder}>이전 병합 기록 더 보기</button>}
    </div>}
  </section>;
}
