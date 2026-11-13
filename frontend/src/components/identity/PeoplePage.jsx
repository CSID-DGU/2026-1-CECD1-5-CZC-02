import { useEffect, useRef, useState } from 'react';
import { peopleApi } from './peopleApi.js';
import { commitmentRecords, duplicateNames } from './peopleUtils.js';
import { identityError } from './identityUtils.js';

const button = 'rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm disabled:opacity-40';
const tabs = ['타임라인', '약속 기록', '할 일', '회의록'];
const date = value => value ? value.replace('T', ' ') + ' (KST)' : '미팅 시각 미기록';

export function PersonDetails({ person }) {
  const show = value => value || '미등록';
  return <section className="space-y-3" aria-label="인물 상세">
    <div><h2 className="text-xl font-bold">{person.name}</h2><p className="text-sm text-slate-500">{person.personId} · {person.isSynthetic ? '가상 테스트 인물' : '등록 인물'}</p></div>
    <dl className="grid gap-2 text-sm sm:grid-cols-2">
      {[['현재 소속', person.company], ['부서', person.department], ['직함', person.title],
        ['등록 별칭', (person.nameVariants || []).join(' · ')], ['이메일', (person.emails || []).join(' · ')],
        ['전화번호', (person.phones || []).join(' · ')], ['접촉 채널', (person.channels || []).join(' · ')]].map(([label, value]) =>
        <div key={label} className="rounded-lg bg-slate-50 p-3"><dt className="text-xs text-slate-500">{label}</dt><dd className="break-words">{show(value)}</dd></div>)}
    </dl>
    <div><h3 className="text-sm font-semibold">이전 소속</h3>{(person.companyHistory || []).length ? <ul className="mt-1 space-y-1 text-sm">
      {person.companyHistory.map((item, i) => <li key={i}>{item.company} · {show(item.title)} · {item.until ? `${item.until}까지` : '종료일 미기록'}</li>)}
    </ul> : <p className="text-sm text-slate-500">등록된 이력 없음</p>}</div>
  </section>;
}

export function MeetingHistory({ meetings, personId, tab = '타임라인' }) {
  const records = tab === '약속 기록' ? commitmentRecords(meetings, personId) : null;
  if (records) return <div className="space-y-3"><p className="text-xs text-amber-800">이행 상태는 확인되지 않았어요. 열린 약속으로 자동 판정하지 않아요. 현재 불러온 회의록 기준이에요.</p>
    {records.length ? records.map((item, index) => <article key={`${item.noteId}:${index}`} className="rounded-xl border p-3">
      <p className="text-xs text-slate-500">{item.scope} · {date(item.occurredAt)}</p><p>{item.text}</p>
      <blockquote className="mt-2 border-l-2 pl-2 text-sm text-slate-600">원문 근거: {item.evidence}</blockquote>
      <a className="text-sm text-blue-600 underline" href={`/meeting-notes/${encodeURIComponent(item.noteId)}`}>출처 회의록 {item.noteId}</a>
    </article>) : <p className="text-sm text-slate-500">불러온 회의록에 약속 기록이 없어요.</p>}</div>;
  return <div className="space-y-3">{meetings.length ? meetings.map(note => <article key={note.noteId} className="rounded-xl border p-3">
    <p className="text-xs text-slate-500">{date(note.occurredAt)} · {note.mode} · 확정 회의록</p>
    <a className="text-blue-600 underline" href={`/meeting-notes/${encodeURIComponent(note.noteId)}`}>{note.noteId} · {note.location || '장소 미기록'}</a>
    {tab === '타임라인' && <p className="mt-1 text-sm text-slate-600">저장된 personId로 연결된 미팅이에요. 논의 내용은 출처 회의록에서 확인하세요.</p>}
  </article>) : <p className="text-sm text-slate-500">연결된 확정 회의록이 없어요. A 선택이 B 회의록에 반영되지 않은 기록은 여기 나타나지 않아요.</p>}</div>;
}

function PersonWorkspace({ id, apiClient }) {
  const [state, setState] = useState({ loading: true, person: null, meetings: [], error: '' });
  const [tab, setTab] = useState('타임라인');
  const [nextPage, setNextPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const generation = useRef(0);
  useEffect(() => {
    generation.current += 1;
    const controller = new AbortController();
    let active = true;
    async function load() {
      try {
        const [person, meetings] = await Promise.all([apiClient.person(id, controller.signal), apiClient.meetings(id, 0, controller.signal)]);
        if (active) { setState({ loading: false, person, meetings, error: '' }); setNextPage(1); setHasMore(meetings.length === 20); }
      } catch (error) { if (active) setState({ loading: false, person: null, meetings: [], error: identityError(error) }); }
    }
    load();
    return () => { active = false; generation.current += 1; controller.abort(); };
  }, [id, apiClient, refresh]);
  async function more() {
    const version = generation.current;
    setLoadingMore(true);
    try {
      const found = await apiClient.meetings(id, nextPage);
      if (version === generation.current) { setState(old => ({ ...old, meetings: [...new Map([...old.meetings, ...found].map(n => [n.noteId, n])).values()], error: '' })); setNextPage(p => p + 1); setHasMore(found.length === 20); }
    } catch (error) { if (version === generation.current) setState(old => ({ ...old, error: identityError(error) })); }
    finally { if (version === generation.current) setLoadingMore(false); }
  }
  if (state.loading) return <p role="status">상세·미팅 이력을 불러오는 중…</p>;
  return <div className="space-y-5">
    <button className={button} onClick={() => { setLoadingMore(false); setState(old => ({ ...old, loading: true })); setRefresh(n => n + 1); }}>상세 다시 불러오기</button>
    {state.error && <p role="alert" className="text-sm text-red-700">{state.error}</p>}
    {state.person && <><PersonDetails person={state.person} />
      <div className="flex flex-wrap gap-2" role="tablist" aria-label="인물 기록">{tabs.map(name => <button key={name} role="tab" aria-selected={tab === name}
        className={`${button} ${tab === name ? 'border-blue-500 text-blue-700' : ''}`} onClick={() => setTab(name)}>{name}</button>)}</div>
      <div role="tabpanel">{tab === '할 일' ? <div className="space-y-2 text-sm text-slate-600"><p>인물별 할 일 목록은 C의 상대방 연결·보관 정책을 확인한 뒤 연결해요.</p><a href="/tasks" className="text-blue-600 underline">내 할 일 보드 열기</a></div>
        : <MeetingHistory meetings={state.meetings} personId={id} tab={tab} />}</div>
      {tab !== '할 일' && hasMore && <button className={button} disabled={loadingMore} onClick={more}>{loadingMore ? '불러오는 중…' : '이전 미팅 더 불러오기'}</button>}
    </>}
  </div>;
}

export function PeoplePage({ apiClient = peopleApi }) {
  const [query, setQuery] = useState('');
  const [request, setRequest] = useState({ q: '', page: 0, revision: 0 });
  const [state, setState] = useState({ loading: true, people: [], error: '' });
  const [selected, setSelected] = useState(null);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    async function load() {
      try {
        const people = await apiClient.list(request.q, request.page, controller.signal);
        if (active) setState({ loading: false, people, error: '' });
      } catch (error) { if (active) setState({ loading: false, people: [], error: identityError(error) }); }
    }
    load(); return () => { active = false; controller.abort(); };
  }, [request, apiClient]);
  function search(event, page = 0) {
    event?.preventDefault(); setSelected(null); setState({ loading: true, people: [], error: '' });
    setRequest(old => ({ q: event?.type === 'submit' ? query : old.q, page, revision: old.revision + 1 }));
  }
  const duplicate = duplicateNames(state.people);
  return <main className="min-h-screen bg-[#f4f6fb] p-5 text-slate-800 md:p-8">
    <header className="mb-5"><h1 className="text-2xl font-bold">사람</h1><p className="mt-1 text-sm text-slate-500">본인 인물 대장 · 이름으로 이력을 추측하지 않고 저장된 인물 ID로 조회해요.</p></header>
    <div className="grid gap-5 lg:grid-cols-[320px_1fr]">
      <aside className="space-y-3 rounded-2xl bg-white p-4 shadow-sm">
        <form onSubmit={search} className="space-y-2"><label className="block text-sm">이름·별칭·소속·연락처 검색<input className="mt-1 w-full rounded-lg border p-2" value={query} onChange={e => setQuery(e.target.value)} maxLength={200} /></label>
          <button className={button} disabled={state.loading}>검색</button></form>
        {state.error && <p role="alert" className="text-sm text-red-700">{state.error}</p>}
        {state.loading ? <p role="status">인물 목록을 불러오는 중…</p> : <>
          {state.people.length === 0 && <p className="text-sm text-slate-500">등록된 인물 또는 검색 결과가 없어요.</p>}
          <ul className="space-y-2">{state.people.map(person => <li key={person.personId}><button className={`w-full rounded-xl border p-3 text-left ${selected === person.personId ? 'border-blue-500 bg-blue-50' : 'border-slate-200'}`}
            aria-pressed={selected === person.personId} onClick={() => setSelected(person.personId)}><span className="font-semibold">{person.name}</span>
            {duplicate.has(person.name.normalize('NFC').trim()) && <span className="ml-2 text-xs text-amber-800">현재 목록 내 동명이인</span>}
            <span className="block text-xs text-slate-500">{person.company || '소속 미등록'} · {person.personId}</span></button></li>)}</ul>
        </>}
        <div className="flex items-center justify-between gap-2"><button className={button} disabled={state.loading || request.page === 0} onClick={e => search(e, request.page - 1)}>이전</button>
          <span className="text-xs">{request.page + 1}쪽</span><button className={button} disabled={state.loading || state.people.length < 20} onClick={e => search(e, request.page + 1)}>다음</button></div>
        <p className="text-xs text-slate-500">고객·계약 인력 구분은 현재 데이터에 없어 추측하지 않아요. 다른 페이지에도 동명이인이 있을 수 있어요.</p>
      </aside>
      <section className="rounded-2xl bg-white p-5 shadow-sm" aria-label="선택한 사람">
        {selected ? <PersonWorkspace key={selected} id={selected} apiClient={apiClient} /> : <p className="text-sm text-slate-500">왼쪽에서 인물을 선택해 주세요.</p>}
      </section>
    </div>
  </main>;
}
