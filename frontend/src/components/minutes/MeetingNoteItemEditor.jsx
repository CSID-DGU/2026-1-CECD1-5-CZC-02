import { useState } from 'react';
import { Check, Plus, Quote, X } from 'lucide-react';
import { findEvidenceRange } from './meetingNoteUtils';

/**
 * 근거 고르기. 근거는 원문 글자 그대로여야 하므로(contracts) 직접 쓰지 않고 원문 문장 중에서 고른다.
 */
function EvidencePicker({ sentences, value, onPick, onClose }) {
  return (
    <div className="mt-1.5 max-h-56 overflow-y-auto rounded-xl border border-blue-200 bg-white p-1.5 shadow-lg">
      <div className="flex items-center justify-between px-2 py-1">
        <span className="text-[11px] font-semibold text-blue-700">원문에서 근거 문장을 고르세요</span>
        <button type="button" onClick={onClose} aria-label="닫기" className="text-slate-400 hover:text-slate-700">
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
      {sentences.length === 0 && <p className="px-2 py-1 text-xs text-slate-400">원문에 고를 문장이 없어요.</p>}
      {sentences.map((sentence) => (
        <button
          key={sentence}
          type="button"
          onClick={() => onPick(sentence)}
          className={`block w-full rounded-lg px-2 py-1.5 text-left text-xs leading-relaxed ${
            sentence === value ? 'bg-blue-50 text-blue-800' : 'text-slate-700 hover:bg-slate-50'
          }`}
        >
          {sentence}
        </button>
      ))}
    </div>
  );
}

function EvidenceLine({ evidence, sourceContent, onSelect, onChangeClick, readOnly }) {
  const found = findEvidenceRange(sourceContent, evidence) !== null;
  return (
    <div className="mt-1 flex items-start gap-1.5 px-1 text-[11px] leading-snug">
      <Quote className="mt-0.5 h-3 w-3 shrink-0 text-slate-300" />
      <button type="button" onClick={onSelect} className="min-w-0 flex-1 text-left text-slate-500 hover:text-blue-700">
        {evidence}
        {!found && <span className="ml-1 text-amber-600">(원문에서 위치를 못 찾음)</span>}
      </button>
      {!readOnly && (
        <button type="button" onClick={onChangeClick} className="shrink-0 text-blue-600 hover:underline">
          근거 바꾸기
        </button>
      )}
    </div>
  );
}

/** 회의록 항목 하나: 내용은 바로 고치고, 누르면 원문의 근거 문장이 강조된다. */
export function ItemRow({ item, selected, readOnly, sentences, sourceContent, onSelect, onChange, onDelete }) {
  const [picking, setPicking] = useState(false);

  return (
    <div
      className={`rounded-xl border p-1.5 transition-colors ${
        selected ? 'border-amber-300 bg-amber-50/70 ring-2 ring-amber-100' : 'border-slate-200 bg-white'
      }`}
    >
      <div className="flex items-center gap-1.5">
        <input
          type="text"
          value={item.text}
          readOnly={readOnly}
          onFocus={onSelect}
          onClick={onSelect}
          onChange={(event) => onChange({ ...item, text: event.target.value })}
          className="min-w-0 flex-1 rounded-lg border-none bg-transparent px-2 py-1 text-sm text-slate-800 focus:outline-none"
        />
        {!readOnly && (
          <button
            type="button"
            onClick={onDelete}
            aria-label="항목 삭제"
            className="shrink-0 rounded-lg p-1 text-slate-300 hover:bg-red-50 hover:text-red-500"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        )}
      </div>
      {!item.text.trim() && <p className="px-2 text-[11px] text-red-600">내용을 비울 수 없어요. 필요 없으면 삭제하세요.</p>}
      <EvidenceLine
        evidence={item.evidence}
        sourceContent={sourceContent}
        onSelect={onSelect}
        onChangeClick={() => setPicking((prev) => !prev)}
        readOnly={readOnly}
      />
      {picking && (
        <EvidencePicker
          sentences={sentences}
          value={item.evidence}
          onClose={() => setPicking(false)}
          onPick={(sentence) => {
            onChange({ ...item, evidence: sentence });
            setPicking(false);
          }}
        />
      )}
    </div>
  );
}

/** 새 항목: 내용과 원문 근거를 둘 다 정해야 추가된다 (근거 필수). */
export function AddItemForm({ sentences, onAdd, onCancel }) {
  const [text, setText] = useState('');
  const [evidence, setEvidence] = useState('');
  const [picking, setPicking] = useState(true);

  return (
    <div className="rounded-xl border border-blue-200 bg-blue-50/40 p-2">
      <input
        type="text"
        value={text}
        autoFocus
        onChange={(event) => setText(event.target.value)}
        placeholder="항목 내용 (예: 계약서 초안 금요일까지 송부)"
        className="w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-800 focus:border-blue-400 focus:outline-none"
      />
      <div className="mt-1.5 flex items-start gap-1.5 px-1 text-[11px]">
        <Quote className="mt-0.5 h-3 w-3 shrink-0 text-slate-300" />
        <span className={`min-w-0 flex-1 ${evidence ? 'text-slate-600' : 'text-amber-700'}`}>
          {evidence || '근거 문장을 골라야 추가할 수 있어요'}
        </span>
        <button type="button" onClick={() => setPicking((prev) => !prev)} className="shrink-0 text-blue-600 hover:underline">
          {evidence ? '근거 바꾸기' : '근거 고르기'}
        </button>
      </div>
      {picking && (
        <EvidencePicker
          sentences={sentences}
          value={evidence}
          onClose={() => setPicking(false)}
          onPick={(sentence) => {
            setEvidence(sentence);
            setPicking(false);
          }}
        />
      )}
      <div className="mt-2 flex justify-end gap-1.5">
        <button type="button" onClick={onCancel} className="rounded-lg px-2.5 py-1 text-xs text-slate-500 hover:bg-white">
          취소
        </button>
        <button
          type="button"
          disabled={!text.trim() || !evidence}
          onClick={() => onAdd({ text: text.trim(), evidence })}
          className="inline-flex items-center gap-1 rounded-lg bg-blue-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-40"
        >
          <Check className="h-3 w-3" /> 추가
        </button>
      </div>
    </div>
  );
}

function MissingBox({ children }) {
  return (
    <div className="rounded-xl border border-dashed border-amber-300 bg-amber-50/60 px-3 py-2 text-xs text-amber-700">
      {children}
    </div>
  );
}

/** FULL 회의록 문항 하나 (항목 목록 + 추가). */
export function FieldSection({ label, items, readOnly, sentences, sourceContent, selectedEvidence, onSelectEvidence, onChangeItems }) {
  const [adding, setAdding] = useState(false);

  return (
    <section>
      <h4 className="mb-1.5 text-xs font-semibold text-slate-600">{label}</h4>
      <div className="space-y-1.5">
        {items.length === 0 && !adding && <MissingBox>메모에 없어서 비워 뒀어요 · 확인 필요</MissingBox>}
        {items.map((item, index) => (
          <ItemRow
            // 순서가 바뀌지 않으므로 index를 키로 쓴다 (입력 중 포커스 유지)
            key={index}
            item={item}
            readOnly={readOnly}
            sentences={sentences}
            sourceContent={sourceContent}
            selected={selectedEvidence === item.evidence}
            onSelect={() => onSelectEvidence(item.evidence)}
            onChange={(next) => onChangeItems(items.map((current, currentIndex) => (currentIndex === index ? next : current)))}
            onDelete={() => onChangeItems(items.filter((_, currentIndex) => currentIndex !== index))}
          />
        ))}
        {adding && (
          <AddItemForm
            sentences={sentences}
            onCancel={() => setAdding(false)}
            onAdd={(item) => {
              onChangeItems([...items, item]);
              onSelectEvidence(item.evidence);
              setAdding(false);
            }}
          />
        )}
        {!readOnly && !adding && (
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="inline-flex items-center gap-1 rounded-lg px-1.5 py-0.5 text-xs text-blue-600 hover:bg-blue-50"
          >
            <Plus className="h-3 w-3" /> 항목 추가
          </button>
        )}
      </div>
    </section>
  );
}

/** LIGHT 회의록 사람 한 명의 3칸 (근황·요청·후속). 모르는 칸은 null. */
export function PerPersonCard({ person, slots, readOnly, sentences, sourceContent, selectedEvidence, onSelectEvidence, onChange }) {
  const [addingSlot, setAddingSlot] = useState(null);

  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50/50 p-3">
      <p className="mb-2 text-sm font-semibold text-slate-800">{person.displayName}</p>
      <div className="space-y-2">
        {slots.map(({ key, label }) => {
          const item = person[key];
          return (
            <div key={key}>
              <h5 className="mb-1 text-[11px] font-semibold text-slate-500">{label}</h5>
              {item ? (
                <ItemRow
                  item={item}
                  readOnly={readOnly}
                  sentences={sentences}
                  sourceContent={sourceContent}
                  selected={selectedEvidence === item.evidence}
                  onSelect={() => onSelectEvidence(item.evidence)}
                  onChange={(next) => onChange({ ...person, [key]: next })}
                  onDelete={() => onChange({ ...person, [key]: null })}
                />
              ) : addingSlot === key ? (
                <AddItemForm
                  sentences={sentences}
                  onCancel={() => setAddingSlot(null)}
                  onAdd={(next) => {
                    onChange({ ...person, [key]: next });
                    onSelectEvidence(next.evidence);
                    setAddingSlot(null);
                  }}
                />
              ) : (
                <MissingBox>
                  메모에 없어서 비워 뒀어요 · 확인 필요
                  {!readOnly && (
                    <button type="button" onClick={() => setAddingSlot(key)} className="ml-2 font-semibold text-blue-600 hover:underline">
                      채우기
                    </button>
                  )}
                </MissingBox>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
