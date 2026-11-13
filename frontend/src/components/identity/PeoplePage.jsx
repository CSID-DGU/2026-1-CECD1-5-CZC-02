import { useEffect, useRef, useState } from 'react';
import { ArrowRight, ArrowUpRight, Building2, ChevronDown, ChevronLeft, ChevronRight, FileText, ListTodo, RefreshCw, Search, UsersRound } from 'lucide-react';
import { peopleApi } from './peopleApi.js';
import { commitmentRecords, duplicateNames } from './peopleUtils.js';
import { identityError } from './identityUtils.js';
import './people.css';

const tabs = ['타임라인', '약속 기록', '할 일', '회의록'];
const date = value => value ? value.replace('T', ' ') + ' (KST)' : '미팅 시각 미기록';
const subtitle = person => [person.company, person.department, person.title].filter(Boolean).join(' · ') || '소속 정보 미등록';

export function PersonDetails({ person }) {
  const show = value => value || '미등록';
  return <section className="people-profile" aria-label="인물 상세">
    <div className="people-profile-heading">
      <span className="people-avatar" aria-hidden="true">{person.name.trim().slice(0, 1)}</span>
      <div className="people-profile-copy">
        <div className="people-profile-name"><h2>{person.name}</h2>{person.isSynthetic && <span className="people-badge blue">가상 인물</span>}</div>
        <p>{subtitle(person)}{person.emails?.length > 0 && <> · {person.emails.join(' · ')}</>}</p>
        {person.companyHistory?.length > 0 && <p className="people-history-summary">이전: {person.companyHistory.map(h => `${h.company}${h.title ? ` ${h.title}` : ''}${h.until ? ` (${h.until}까지)` : ''}`).join(' · ')}</p>}
      </div>
      <button className="people-button people-next-meeting" disabled title="다음 미팅 준비 기능은 아직 연결되지 않았어요.">다음 미팅 준비 보기 <ArrowUpRight size={15} /></button>
    </div>
    <details className="people-profile-details"><summary>연락처·상세 정보 <ChevronDown size={14} /></summary>
      <dl className="people-info-grid">
        {[['인물 ID', person.personId], ['현재 소속', person.company], ['부서', person.department], ['직함', person.title],
          ['등록 별칭', (person.nameVariants || []).join(' · ')], ['이메일', (person.emails || []).join(' · ')],
          ['전화번호', (person.phones || []).join(' · ')], ['접촉 채널', (person.channels || []).join(' · ')]].map(([label, value]) =>
          <div key={label}><dt>{label}</dt><dd>{show(value)}</dd></div>)}
      </dl>
      <div className="people-old-company"><h3>이전 소속</h3>{person.companyHistory?.length ? <ul>
        {person.companyHistory.map((item, i) => <li key={i}>{item.company} · {show(item.title)} · {item.until ? `${item.until}까지` : '종료일 미기록'}</li>)}
      </ul> : <p>등록된 이력 없음</p>}</div>
    </details>
  </section>;
}

function EmptyState({ icon: Icon = FileText, title, children, compact = false }) {
  return <div className={`people-empty${compact ? ' compact' : ''}`}>
    <span className="people-empty-icon"><Icon size={compact ? 23 : 30} strokeWidth={1.5} /></span>
    <h3>{title}</h3>{children && <div className="people-empty-copy">{children}</div>}
  </div>;
}

export function MeetingHistory({ meetings, personId, tab = '타임라인', preview = false }) {
  const records = tab === '약속 기록' ? commitmentRecords(meetings, personId) : null;
  if (records) return <div className="people-records">
    <p className="people-notice">이행 상태는 확인되지 않았어요. 현재 불러온 회의록의 약속 기록이에요.</p>
    {records.length ? records.map((item, index) => <article key={`${item.noteId}:${index}`} className="people-record-card">
      <div className="people-record-top"><span className="people-badge amber">약속</span><time>{date(item.occurredAt)}</time></div>
      <h3>{item.text}</h3><p>{item.scope}</p>
      <blockquote>원문 근거: {item.evidence}</blockquote>
      {!preview && <a className="people-text-link" href={`/meeting-notes/${encodeURIComponent(item.noteId)}`}>출처 회의록 {item.noteId} <ArrowUpRight size={13} /></a>}
    </article>) : <EmptyState compact title="아직 약속 기록이 없어요"><p>이 인물과 연결된 확정 회의록에 약속이 기록되면 여기서 볼 수 있어요.</p></EmptyState>}
  </div>;
  if (!meetings.length) return <EmptyState title="연결된 미팅 기록이 없어요"><p>인물이 연결된 확정 회의록이 여기에 표시돼요.<br />이름이 같다는 이유만으로 다른 사람의 기록을 가져오지 않아요.</p></EmptyState>;
  return <div className="people-timeline">{meetings.map(note => {
    const email = note.sourceRefs?.some(source => source.sourceType === 'EMAIL');
    const topic = note.fields?.discussion?.[0]?.text;
    const title = `${note.occurredAt ? note.occurredAt.slice(5, 10).replace('-', '/') + ' ' : ''}${note.location || (email ? '이메일' : '미팅')} 회의록`;
    const promiseCount = commitmentRecords([note], personId).length;
    return <article key={note.noteId} className="people-timeline-card">
      <span className={`people-badge ${email ? 'cyan' : 'purple'}`}>{email ? '메일' : '미팅'}</span>
      <div className="people-timeline-copy">
        <h3>{preview ? title : <a href={`/meeting-notes/${encodeURIComponent(note.noteId)}`}>{title}</a>}</h3>
        <p>{topic || '논의 내용 미기록'}</p>
        <div className="people-record-meta"><time>{date(note.occurredAt)}</time><span>약속 기록 {promiseCount}건</span><span>확정 회의록</span></div>
        {topic && <details className="people-evidence"><summary>원문 근거 보기</summary><blockquote>{note.fields.discussion[0].evidence}</blockquote></details>}
      </div>
      {!preview && <a className="people-record-arrow" href={`/meeting-notes/${encodeURIComponent(note.noteId)}`} aria-label={`${title} 열기`}><ChevronRight size={17} /></a>}
    </article>;
  })}</div>;
}

function PersonWorkspace({ id, apiClient, preview }) {
  const [state, setState] = useState({ loading: true, person: null, meetings: [], error: '' });
  const [tab, setTab] = useState('타임라인');
  const [nextPage, setNextPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const generation = useRef(0);
  useEffect(() => {
    generation.current += 1;
    const controller = new AbortController(); let active = true;
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
  if (state.loading) return <div className="people-loading" role="status"><RefreshCw size={18} className="people-spin" />인물 정보를 불러오는 중…</div>;
  const records = commitmentRecords(state.meetings, id).length;
  const count = name => name === '약속 기록' ? records : name === '회의록' ? state.meetings.length : null;
  return <>
    {state.error && <p role="alert" className="people-error">{state.error}</p>}
    {state.person && <><PersonDetails person={state.person} />
      <div className="people-tabs" role="tablist" aria-label="인물 기록">{tabs.map((name, i) => <button key={name} role="tab" id={`people-tab-${i}`} aria-controls={`people-panel-${i}`} aria-selected={tab === name}
        className={tab === name ? 'active' : ''} onClick={() => setTab(name)}>{name}{count(name) !== null && <span title="현재 불러온 회의록 기준">{count(name)}</span>}</button>)}</div>
      <div className="people-tab-content" role="tabpanel" id={`people-panel-${tabs.indexOf(tab)}`} aria-labelledby={`people-tab-${tabs.indexOf(tab)}`}>
        {tab === '할 일' ? <EmptyState icon={ListTodo} title="인물별 할 일은 준비 중이에요"><p>전체 할 일은 보드에서 확인할 수 있어요.</p>{!preview && <a className="people-button" href="/tasks">할 일 보드 열기 <ArrowUpRight size={14} /></a>}</EmptyState>
          : <MeetingHistory meetings={state.meetings} personId={id} tab={tab} preview={preview} />}
      </div>
      {tab !== '할 일' && hasMore && <div className="people-load-more"><button className="people-button" disabled={loadingMore} onClick={more}>{loadingMore ? '불러오는 중…' : '이전 미팅 더 불러오기'}<ChevronDown size={14} /></button></div>}
    </>}
    <div className="people-detail-footer"><span>인물에 연결된 확정 기록만 표시해요.</span><button className="people-icon-button" aria-label="상세 다시 불러오기" title="상세 다시 불러오기" onClick={() => { setLoadingMore(false); setState(old => ({ ...old, loading: true })); setRefresh(n => n + 1); }}><RefreshCw size={14} /></button></div>
  </>;
}

export function PeoplePage({ apiClient = peopleApi, preview = false, onPreviewToggle }) {
  const [query, setQuery] = useState('');
  const [request, setRequest] = useState({ q: '', page: 0, revision: 0 });
  const [state, setState] = useState({ loading: true, people: [], error: '' });
  const [selected, setSelected] = useState(null);
  useEffect(() => {
    const controller = new AbortController(); let active = true;
    async function load() {
      try {
        const people = await apiClient.list(request.q, request.page, controller.signal);
        if (active) { setState({ loading: false, people, error: '' }); setSelected(old => people.some(p => p.personId === old) ? old : people[0]?.personId || null); }
      } catch (error) { if (active) setState({ loading: false, people: [], error: identityError(error) }); }
    }
    load(); return () => { active = false; controller.abort(); };
  }, [request, apiClient]);
  function search(event, page = 0) {
    event?.preventDefault(); setSelected(null); setState({ loading: true, people: [], error: '' });
    setRequest(old => ({ q: event?.type === 'submit' ? query : old.q, page, revision: old.revision + 1 }));
  }
  const duplicate = duplicateNames(state.people);
  return <main className="people-page">
    <header className="people-page-header"><div><h1>사람</h1><p>인물별 미팅·메일·약속을 한곳에서 확인하세요.</p></div>
      {onPreviewToggle && <button className="people-button people-preview-toggle" onClick={onPreviewToggle}>{preview ? '내 인물 대장으로 돌아가기' : '가상 화면 예시'}<ArrowUpRight size={14} /></button>}
    </header>
    {preview && <div className="people-preview-banner" role="status"><span className="people-badge blue">가상 데이터 미리보기</span>화면 구성을 위한 예시예요. 실제 인물 대장에 저장되거나 반영되지 않아요.</div>}
    <div className="people-columns">
      <aside className="people-list-panel" aria-label="사람 목록">
        <div className="people-list-heading"><h2>사람 목록</h2><UsersRound size={17} /></div>
        <form onSubmit={search} className="people-search"><label htmlFor="people-query">검색</label><div><Search size={16} aria-hidden="true" /><input id="people-query" placeholder="이름 · 회사 · 이메일" value={query} onChange={e => setQuery(e.target.value)} maxLength={200} /><button type="submit" disabled={state.loading} aria-label="인물 검색"><ArrowRight size={16} /></button></div></form>
        <div className="people-filters" aria-label="인물 유형"><span className="selected">전체</span><button disabled title="인물 대장에 고객 구분 정보가 없어요.">고객</button><button disabled title="인물 대장에 계약 인력 구분 정보가 없어요.">계약 인력</button></div>
        {state.error && <div className="people-error" role="alert"><p>{state.error}</p><button className="people-text-link" onClick={e => search(e, request.page)}>다시 불러오기 <RefreshCw size={12} /></button></div>}
        {state.loading ? <div className="people-loading" role="status"><RefreshCw size={16} className="people-spin" />목록을 불러오는 중…</div> : <>
          {!state.error && state.people.length === 0 && <EmptyState compact icon={UsersRound} title={request.q ? '검색 결과가 없어요' : '아직 등록된 인물이 없어요'}><p>{request.q ? '다른 이름이나 소속으로 검색해 보세요.' : '등록된 인물이 이곳에 표시돼요.'}</p>{!request.q && !preview && <a className="people-text-link" href="/meeting-notes">미팅 기록 열기 <ArrowUpRight size={12} /></a>}</EmptyState>}
          <ul className="people-list">{state.people.map(person => <li key={person.personId}><button className={`people-person-card${selected === person.personId ? ' selected' : ''}`} aria-pressed={selected === person.personId} onClick={() => setSelected(person.personId)}>
            <div><strong>{person.name}</strong>{duplicate.has(person.name.normalize('NFC').trim()) && <span className="people-badge amber">동명이인</span>}</div>
            <p>{subtitle(person)}</p><span className="people-person-id">{person.personId}</span>
          </button></li>)}</ul>
        </>}
        <div className="people-pagination"><button className="people-icon-button" disabled={state.loading || request.page === 0} onClick={e => search(e, request.page - 1)} aria-label="이전 페이지"><ChevronLeft size={16} /></button><span>{request.page + 1}쪽</span><button className="people-icon-button" disabled={state.loading || state.people.length < 20} onClick={e => search(e, request.page + 1)} aria-label="다음 페이지"><ChevronRight size={16} /></button></div>
        <p className="people-list-footnote"><Building2 size={13} />유형 미등록 · 동명이인 표시는 현재 페이지 기준이에요.</p>
      </aside>
      <section className="people-detail-panel" aria-label="선택한 사람">{selected ? <PersonWorkspace key={selected} id={selected} apiClient={apiClient} preview={preview} /> :
        <EmptyState icon={UsersRound} title="한 사람의 기록을 한눈에"><p>왼쪽 목록에서 인물을 선택하면<br />연락처와 미팅 이력을 확인할 수 있어요.</p></EmptyState>}</section>
    </div>
  </main>;
}
