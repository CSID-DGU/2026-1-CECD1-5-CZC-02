import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { identityApi } from './identityApi';
import { findReviewItem, groupCandidateReasons, hasIdentityClue, identityError, personLabel, registrationBody } from './identityUtils';

const button = 'rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 disabled:cursor-not-allowed disabled:opacity-40';
const input = 'mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-2 text-sm disabled:bg-slate-100';

function PersonOption({ person, checked, disabled, onChange, reasons = [], name }) {
  return <label className={`flex items-start gap-2 rounded-xl border px-3 py-2 ${checked ? 'border-blue-400 bg-blue-50' : 'border-slate-200 bg-white'}`}>
    <input type="radio" name={name} checked={checked} disabled={disabled || !person} onChange={onChange} className="mt-1" />
    <span className="min-w-0"><span className="block text-sm font-semibold text-slate-800">{personLabel(person)}</span>
      {reasons.map((reason, index) => <span key={index} className="mt-1 block text-xs text-slate-600">{reason}</span>)}
    </span>
  </label>;
}

function NewPersonForm({ form, setForm, disabled, onSubmit }) {
  const edit = event => setForm(previous => ({ ...previous, [event.target.name]: event.target.value }));
  return <form onSubmit={onSubmit} className="space-y-2 rounded-xl border border-violet-200 bg-violet-50/30 p-3">
    <p className="text-xs text-slate-600">새 사람을 등록하기 전에 이름과 현재 소속을 확인해 주세요.</p>
    {[['name','대표 이름 · 호칭 제외'],['company','현재 소속 · 모르면 비워 두기'],['email','이메일 · 모르면 비워 두기'],['phone','전화 · 모르면 비워 두기']].map(([name,label]) =>
      <label key={name} className="block text-xs text-slate-600">{label}<input className={input} name={name} value={form[name]} disabled={disabled}
        required={name === 'name'} onChange={edit} autoComplete="off" /></label>)}
    <label className="block text-xs text-slate-600">데이터 유형<select className={input} name="isSynthetic" value={form.isSynthetic} onChange={edit} required disabled={disabled}>
      <option value="">선택해 주세요</option><option value="true">가상 테스트 인물</option><option value="false">실제 인물</option>
    </select></label>
    <button type="submit" className={`${button} border-violet-300 text-violet-700`} disabled={disabled}>새 인물 등록 후 선택 저장</button>
  </form>;
}

export function IdentityCandidateRow({ participant, item, people = {}, readOnly, apiClient = identityApi, noteId, onSaved }) {
  const resolution = item?.resolution;
  const { candidates, notices } = groupCandidateReasons(resolution?.candidates);
  const [selected, setSelected] = useState(null);
  const [editing, setEditing] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [page, setPage] = useState(0);
  const [didSearch, setDidSearch] = useState(false);
  const [searching, setSearching] = useState(false);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [form, setForm] = useState({ name: resolution?.mention?.name || '', company: resolution?.mention?.company || '',
    email: resolution?.mention?.email || '', phone: resolution?.mention?.phone || '', isSynthetic: '' });
  const human = resolution?.decidedBy === 'HUMAN';
  const disabled = Boolean(readOnly || busy || (human && !editing) || !item || !noteId);
  const missing = resolution && !hasIdentityClue(resolution.mention);

  async function search(event, nextPage = 0) {
    event?.preventDefault(); setSearching(true); setError('');
    try { const found = await apiClient.search(query, nextPage); setResults(found); setPage(nextPage); setDidSearch(true); }
    catch (failure) { setError(identityError(failure)); }
    finally { setSearching(false); }
  }
  async function save(body) {
    setBusy(true); setError('');
    try { const saved = await apiClient.select(noteId, resolution.resolutionId, body, item.etag); await onSaved(saved); }
    catch (failure) { setError(identityError(failure)); }
    finally { setBusy(false); }
  }
  function register(event) {
    event.preventDefault();
    try { const newPerson = registrationBody(form); return save({ newPerson }); }
    catch (failure) { setError(identityError(failure)); }
  }

  return <section className="space-y-2 rounded-xl border border-slate-200 bg-white p-3" aria-label={`${participant.displayName || '이름 없는 참석자'} 인물 확인`}>
    <div className="flex flex-wrap items-center justify-between gap-2"><h4 className="text-sm font-semibold text-slate-800">{participant.displayName || '이름 없는 참석자'}</h4>
      <span className={`rounded-md px-2 py-1 text-[11px] ${human ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'}`}>
        {human ? '사람이 선택 · 저장됨' : !resolution ? '식별 결과 확인 필요' : resolution.decision === 'CONFIRMED' ? '연락처 일치 · 확인 필요' : '인물 확인 필요'}
      </span></div>
    {!resolution ? <p className="text-xs text-amber-800">저장된 식별 결과가 없거나 참석자와 정확히 대응하지 않아요. 임의로 연결하지 않았어요.</p> : <>
      {resolution.mention?.email && <p className="break-all text-xs text-slate-500">{resolution.mention.email}</p>}
      {human && !editing ? <div className="space-y-2"><p className="text-sm text-emerald-800">선택한 인물: {personLabel(people[resolution.personId])}</p>
        <button type="button" className={button} disabled={readOnly || busy || !noteId} onClick={() => setEditing(true)}>선택 변경</button></div> : <>
        {missing && <p className="rounded-lg bg-amber-50 p-2 text-xs text-amber-800">정보 부족 — 확인 필요. 새 사람으로 등록하기 전에 단서를 확인해 주세요.</p>}
        {resolution.decision === 'NEW' && !missing && <p className="text-xs text-slate-600">일치하는 후보가 없어요. 직접 검색하거나 새 사람인지 확인해 주세요.</p>}
        {notices.map(notice => <p key={notice} className="rounded-lg bg-amber-50 p-2 text-xs text-amber-800">{notice}</p>)}
        {candidates.map(candidate => <PersonOption name={resolution.resolutionId} key={candidate.personId} person={people[candidate.personId]}
          reasons={candidate.reasons} checked={selected?.personId === candidate.personId} disabled={disabled}
          onChange={() => setSelected(people[candidate.personId])} />)}
        <div className="flex flex-wrap gap-2"><button type="button" className={button} disabled={disabled} onClick={() => setSearchOpen(value => !value)}>인물 대장 직접 검색</button>
          {!missing && <button type="button" className={button} disabled={disabled} onClick={() => setCreating(value => !value)}>새 사람으로 등록</button>}</div>
        {searchOpen && <div className="space-y-2 rounded-lg bg-slate-50 p-2">
          <form className="flex gap-2" onSubmit={event => search(event)}><label className="flex-1 text-xs text-slate-600">이름·별칭·소속·연락처 검색
            <input className={input} value={query} onChange={event => setQuery(event.target.value)} disabled={disabled || searching} maxLength={200} /></label>
            <button className={button} disabled={disabled || searching}>{searching ? '검색 중…' : '검색'}</button></form>
          {results.map(person => <PersonOption name={resolution.resolutionId} key={person.personId} person={person} checked={selected?.personId === person.personId} disabled={disabled} onChange={() => setSelected(person)} />)}
          {didSearch && results.length === 0 && <p className="text-xs text-slate-600">검색 결과가 없어요.</p>}
          {didSearch && <div className="flex gap-2"><button type="button" className={button} disabled={disabled || searching || page === 0} onClick={event => search(event,page-1)}>이전</button>
            <span className="self-center text-xs">{page+1}쪽</span><button type="button" className={button} disabled={disabled || searching || results.length < 20} onClick={event => search(event,page+1)}>다음</button></div>}
        </div>}
        {selected && <div className="rounded-lg bg-blue-50 p-2"><p className="mb-2 text-xs text-slate-700">선택 대기: {personLabel(selected)}</p>
          <button type="button" className={`${button} border-blue-300 text-blue-700`} disabled={disabled} onClick={() => save({ personId: selected.personId })}>{busy ? '저장 중…' : '이 사람으로 선택 저장'}</button></div>}
        {editing && <button type="button" className={button} disabled={busy} onClick={() => { setSelected(null); setEditing(false); }}>선택 변경 취소</button>}
        {creating && <NewPersonForm form={form} setForm={setForm} disabled={disabled} onSubmit={register} />}
      </>}
    </>}
    {error && <p role="alert" className="text-xs text-red-700">{error}</p>}
    {readOnly && <p className="text-xs text-slate-500">확정·보관했거나 저장 중인 회의록은 여기서 변경할 수 없어요.</p>}
  </section>;
}

function IdentityPanelContent({ noteId, participants = [], readOnly = false, apiClient = identityApi }) {
  const [items, setItems] = useState([]);
  const [people, setPeople] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let live = true;
    async function load() {
      try {
        if (!noteId) throw new Error('회의록을 먼저 불러와 주세요.');
        const loaded = await apiClient.list(noteId, controller.signal);
        const ids = [...new Set(loaded.flatMap(({ resolution: r }) => [r.personId,...(r.candidates || []).map(c => c.personId)]).filter(Boolean))];
        const fetched = await Promise.allSettled(ids.map(id => apiClient.person(id,controller.signal)));
        if (!live) return;
        setItems(loaded); setPeople(Object.fromEntries(fetched.filter(result => result.status === 'fulfilled').map(result => [result.value.personId,result.value])));
        setError(fetched.some(result => result.status === 'rejected') ? '일부 인물 정보를 불러오지 못했어요. 해당 후보는 선택할 수 없어요.' : '');
      } catch (failure) { if(live) setError(identityError(failure)); }
      finally { if(live) setLoading(false); }
    }
    load();
    return () => { live=false; controller.abort(); };
  },[noteId,apiClient,refresh]);

  async function saved(item) {
    setItems(previous => previous.map(old => old.resolution.resolutionId === item.resolution.resolutionId ? item : old));
    try { const person = await apiClient.person(item.resolution.personId); setPeople(previous => ({ ...previous,[person.personId]:person })); }
    catch { setError('선택은 저장됐지만 인물 정보를 다시 불러오지 못했어요. 다시 불러와 확인해 주세요.'); }
    setMessage('인물 선택과 수정 기록을 저장했어요. 회의록·할 일의 상대방은 아직 바뀌지 않았어요.');
  }
  function reload() { setLoading(true); setError(''); setMessage(''); setRefresh(value => value+1); }
  return <div className="space-y-3">
    <div className="flex items-center justify-between gap-2"><h3 className="text-xs font-semibold text-slate-600">참석자 인물 확인</h3>
      <button type="button" className={button} onClick={reload} disabled={loading}>다시 불러오기</button></div>
    {loading ? <p className="text-xs text-slate-500" role="status">저장된 식별 결과를 불러오는 중…</p> : <>
      {participants.length === 0 && <p className="text-xs text-amber-800">참석자 정보가 없어요 · 확인 필요</p>}
      {participants.map((participant,index) => { const item=findReviewItem(participant,items,participants); return <IdentityCandidateRow
        key={`${index}:${item?.resolution.resolutionId || 'missing'}:${item?.etag || ''}`} participant={participant} item={item} people={people}
        readOnly={readOnly} noteId={noteId} apiClient={apiClient} onSaved={saved} />; })}
      {items.length === 0 && <p className="text-xs text-slate-500">실제 식별 연결에서 저장한 결과가 필요해요. 샘플 인물을 대신 표시하지 않아요.</p>}
    </>}
    {error && <p role="alert" className="text-xs text-red-700">{error}</p>}
    {message && <p role="status" className="rounded-lg bg-emerald-50 p-2 text-xs text-emerald-800">{message}</p>}
    <p className="text-[11px] text-slate-500">인물 선택은 저장할 수 있어요. 회의록·할 일에 선택을 반영하는 연결은 아직 준비 중이에요.</p>
  </div>;
}

export function IdentityCandidatesPanel(props) {
  const { noteId } = useParams();
  return <IdentityPanelContent key={noteId || 'none'} {...props} noteId={noteId} />;
}
