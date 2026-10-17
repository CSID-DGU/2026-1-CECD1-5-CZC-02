import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { confirmMeetingNote, getMeetingNote, updateMeetingNote } from '../../api/meetingNotes';
import { getApiErrorMessage } from '../../api/errors';
import {
  FIELD_DEFS,
  PER_PERSON_SLOTS,
  cloneFields,
  findEvidenceRange,
  formatMeetingDateTime,
  sameJson,
  splitSentences,
} from './meetingNoteUtils';
import { FieldSection, PerPersonCard } from './MeetingNoteItemEditor';
import { ParticipantCandidatesPanel } from './ParticipantCandidatesPanel';
import { TaskDraftPanel } from './TaskDraftPanel';
import { StatusBadge } from './MeetingNotePage';
import taskSamples from './samples/task.example.json';

function Card({ title, subtitle, className = '', children }) {
  return (
    <div className={`h-fit rounded-2xl border border-slate-200 bg-white p-4 text-left shadow-sm ${className}`}>
      <h3 className="mb-3 text-base font-bold text-slate-800" style={{ fontFamily: 'var(--display)' }}>
        {title} {subtitle && <span className="text-xs font-normal text-slate-400">{subtitle}</span>}
      </h3>
      {children}
    </div>
  );
}

/** 원문. 고른 항목의 근거 문장을 강조한다. */
function SourcePanel({ content, evidence }) {
  const markRef = useRef(null);
  const range = findEvidenceRange(content, evidence);

  useEffect(() => {
    markRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }, [evidence]);

  return (
    <>
      <p className="whitespace-pre-wrap break-words text-sm leading-7 text-slate-700">
        {range ? (
          <>
            {content.slice(0, range.start)}
            <mark ref={markRef} className="rounded bg-amber-200/80 px-0.5 text-slate-900 ring-1 ring-amber-400">
              {content.slice(range.start, range.end)}
            </mark>
            {content.slice(range.end)}
          </>
        ) : content}
      </p>
      {evidence && !range && (
        <p className="mt-2 text-xs text-amber-700">고른 항목의 근거를 원문에서 찾지 못했어요.</p>
      )}
      <p className="mt-3 border-t border-slate-100 pt-2 text-[11px] text-slate-400">원본은 지우지 않고 회의록에 연결해 보관해요.</p>
    </>
  );
}

function hasEmptyText(fields, perPerson) {
  const items = [
    ...FIELD_DEFS.flatMap(({ key }) => fields?.[key] || []),
    ...(perPerson || []).flatMap((person) => PER_PERSON_SLOTS.map(({ key }) => person[key]).filter(Boolean)),
  ];
  return items.some((item) => !item.text.trim());
}

/** ③ 확인 화면 틀: 원문 | 회의록 | 할 일. 확정 전엔 아무것도 등록하지 않는다. */
export function MeetingNoteReviewPage() {
  const { noteId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  // 초안 만들기 직후에만 있는 A·C 결과 (없으면 각 영역이 샘플을 쓴다)
  const draftExtras = location.state || {};

  const [note, setNote] = useState(null);
  const [fields, setFields] = useState(null);
  const [perPerson, setPerPerson] = useState(null);
  const [selectedEvidence, setSelectedEvidence] = useState(null);
  const [loadMessage, setLoadMessage] = useState('');
  const [actionMessage, setActionMessage] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const applyNote = useCallback((response) => {
    setNote(response);
    setFields(cloneFields(response.meetingNote.fields));
    setPerPerson(response.meetingNote.perPerson ? structuredClone(response.meetingNote.perPerson) : null);
  }, []);

  useEffect(() => {
    let ignore = false;
    getMeetingNote(noteId)
      .then((response) => {
        if (!ignore) {
          applyNote(response);
        }
      })
      .catch((error) => {
        if (!ignore) {
          setLoadMessage(getApiErrorMessage(error));
        }
      });
    return () => {
      ignore = true;
    };
  }, [noteId, applyNote]);

  const meetingNote = note?.meetingNote;
  const content = note?.source?.content || '';
  const sentences = useMemo(() => splitSentences(content), [content]);
  const isLight = meetingNote?.mode === 'LIGHT';
  const readOnly = meetingNote?.status === 'CONFIRMED' || isSaving;

  const changedFieldKeys = useMemo(() => {
    if (!meetingNote || !fields) {
      return [];
    }
    return FIELD_DEFS.map(({ key }) => key).filter((key) => !sameJson(fields[key], meetingNote.fields[key]));
  }, [meetingNote, fields]);
  const perPersonChanged = Boolean(meetingNote && perPerson && !sameJson(perPerson, meetingNote.perPerson));
  const isDirty = changedFieldKeys.length > 0 || perPersonChanged;

  // 고친 문항만 보낸다. 서버가 바뀐 값마다 수정 기록(revision)을 남긴다.
  const saveChanges = async () => {
    if (!isDirty) {
      return note;
    }
    if (hasEmptyText(fields, perPerson)) {
      throw new Error('내용이 빈 항목이 있어요. 채우거나 삭제해 주세요.');
    }
    const payload = isLight
      ? { perPerson }
      : { fields: Object.fromEntries(changedFieldKeys.map((key) => [key, fields[key]])) };
    const saved = await updateMeetingNote(noteId, payload);
    applyNote(saved);
    return saved;
  };

  const runAction = async (action) => {
    setIsSaving(true);
    setActionMessage('');
    try {
      await action();
    } catch (error) {
      setActionMessage(error.response ? getApiErrorMessage(error) : error.message);
    } finally {
      setIsSaving(false);
    }
  };

  const handleLater = () => runAction(async () => {
    await saveChanges();
    navigate('/meeting-notes/inbox');
  });

  const handleConfirm = () => runAction(async () => {
    await saveChanges();
    applyNote(await confirmMeetingNote(noteId));
    setActionMessage('확정했어요.');
    // 메뉴의 확인 대기함 배지를 바로 갱신한다
    window.dispatchEvent(new Event('meeting-notes:changed'));
  });

  if (loadMessage) {
    return (
      <div className="p-6">
        <p className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{loadMessage}</p>
      </div>
    );
  }

  if (!meetingNote) {
    return (
      <div className="flex h-full items-center justify-center gap-2 text-sm text-slate-400">
        <Loader2 className="h-4 w-4 animate-spin" /> 회의록을 불러오는 중…
      </div>
    );
  }

  const droppedItems = draftExtras.droppedItems || [];

  return (
    <div className="flex h-full flex-col">
      <div className="min-h-0 flex-1 overflow-auto p-6">
        <div className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-1 text-left">
          <h2 className="text-[22px] font-bold tracking-tight text-slate-900" style={{ fontFamily: 'var(--display)' }}>
            확인하고 확정하기
          </h2>
          <StatusBadge status={meetingNote.status} />
          <p className="text-sm text-slate-500">
            {formatMeetingDateTime(meetingNote.occurredAt)}
            {meetingNote.location ? ` · ${meetingNote.location}` : ''}
            {readOnly ? '' : ' · 틀린 칸만 고치세요. 항목을 누르면 근거 문장이 표시돼요.'}
          </p>
        </div>

        {note.llmFallbackReason && (
          <p className="mb-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-2 text-left text-xs text-amber-800">
            {note.llmProvider === 'mock'
              ? 'AI 모듈을 부르지 않는 mock 모드라 샘플 회의록이에요.'
              : `AI가 정리하지 못해 빈 회의록을 만들었어요. 직접 채워 주세요. (${note.llmFallbackReason})`}
          </p>
        )}

        <div className="grid grid-cols-1 gap-5 xl:grid-cols-3">
          {/* 아래 문항을 고치는 동안에도 강조된 근거가 보이도록 원문은 붙어 있게 */}
          <Card
            title="원문"
            subtitle={note.source.sourceType === 'MEETING_NOTE' ? '미팅 메모' : note.source.sourceType}
            className="xl:sticky xl:top-0 xl:max-h-[calc(100vh-11rem)] xl:overflow-y-auto"
          >
            <SourcePanel content={content} evidence={selectedEvidence} />
          </Card>

          <Card title="회의록" subtitle={isLight ? '여러 명 방문 · 사람별 3칸' : null}>
            <div className="space-y-4">
              <ParticipantCandidatesPanel
                participants={meetingNote.participants}
                identityResolutions={draftExtras.identityResolutions}
                readOnly={readOnly}
              />

              {isLight ? (
                <div className="space-y-2">
                  {(perPerson || []).length === 0 && (
                    <p className="rounded-xl border border-dashed border-amber-300 bg-amber-50/60 px-3 py-2 text-xs text-amber-700">
                      만난 사람이 없어 사람별 칸을 만들지 못했어요 · 확인 필요
                    </p>
                  )}
                  {(perPerson || []).map((person, index) => (
                    <PerPersonCard
                      key={person.displayName}
                      person={person}
                      slots={PER_PERSON_SLOTS}
                      readOnly={readOnly}
                      sentences={sentences}
                      sourceContent={content}
                      selectedEvidence={selectedEvidence}
                      onSelectEvidence={setSelectedEvidence}
                      onChange={(next) => setPerPerson((prev) => prev.map((current, currentIndex) => (currentIndex === index ? next : current)))}
                    />
                  ))}
                </div>
              ) : (
                FIELD_DEFS.map(({ key, label }) => (
                  <FieldSection
                    key={key}
                    label={label}
                    items={fields[key]}
                    readOnly={readOnly}
                    sentences={sentences}
                    sourceContent={content}
                    selectedEvidence={selectedEvidence}
                    onSelectEvidence={setSelectedEvidence}
                    onChangeItems={(items) => setFields((prev) => ({ ...prev, [key]: items }))}
                  />
                ))
              )}

              {droppedItems.length > 0 && (
                <details className="rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-500">
                  <summary className="cursor-pointer">AI가 근거를 원문에서 못 찾아 뺀 항목 {droppedItems.length}개</summary>
                  <ul className="mt-1 list-disc space-y-0.5 pl-4">
                    {droppedItems.map((item, index) => (
                      <li key={index}>{item.text || item.evidence} — {item.reason}</li>
                    ))}
                  </ul>
                </details>
              )}
            </div>
          </Card>

          <Card title="할 일" subtitle={`${(draftExtras.tasks || taskSamples).length}건 추출 · 샘플`}>
            <TaskDraftPanel
              tasks={draftExtras.tasks}
              selectedEvidence={selectedEvidence}
              readOnly={readOnly}
              onSelectEvidence={setSelectedEvidence}
            />
          </Card>
        </div>
      </div>

      <div className="border-t border-slate-200 bg-white/90 px-6 py-3 backdrop-blur">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="text-left text-sm text-slate-600">
            {meetingNote.status === 'CONFIRMED' ? (
              <span>
                <b className="text-emerald-700">확정된 회의록</b>
                {note.confirmedWithoutEdit ? ' · AI 결과를 고치지 않고 확정' : ' · 고친 내용은 학습용으로 기록됨'}
              </span>
            ) : (
              <span>
                확정하면: <b>할 일</b>은 C로, <b>다음 접촉</b>은 일정(D)으로 넘어가요 · 고친 내용은 <b>학습용으로 기록</b>
                {isDirty && <span className="ml-2 text-amber-700">· 저장 안 한 수정 있음</span>}
              </span>
            )}
            {actionMessage && <p className="mt-1 text-xs text-blue-700">{actionMessage}</p>}
          </div>
          {meetingNote.status === 'CONFIRMED' ? (
            <button
              type="button"
              onClick={() => navigate('/meeting-notes')}
              className="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              미팅 기록으로
            </button>
          ) : (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={handleLater}
                disabled={isSaving}
                className="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-50"
              >
                {isDirty ? '저장하고 나중에' : '나중에'}
              </button>
              <button
                type="button"
                onClick={handleConfirm}
                disabled={isSaving}
                className="inline-flex items-center gap-1.5 rounded-xl bg-blue-600 px-5 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-700 disabled:opacity-50"
              >
                {isSaving && <Loader2 className="h-4 w-4 animate-spin" />}
                확정하기
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
