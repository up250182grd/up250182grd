import { Fragment, useId } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/cn';
import { BLANK, type Answer, type ExerciseType, type Prompt } from './types';

interface InputProps {
  type: ExerciseType;
  prompt: Prompt;
  value: Answer;
  onChange: (next: Answer) => void;
  disabled?: boolean;
}

const choiceBase =
  'flex min-h-12 w-full items-center gap-3 rounded-lg border px-4 py-2 text-left text-base transition-colors disabled:cursor-default';

function choiceClass(selected: boolean) {
  return cn(
    choiceBase,
    selected ? 'border-primary bg-primary/10 font-medium text-text' : 'border-border bg-surface hover:bg-surface-2',
  );
}

function Passage({ text }: { text?: string }) {
  if (!text) return null;
  return (
    <blockquote className="mb-4 rounded-lg border-l-4 border-accent bg-surface-2 p-4 text-base leading-relaxed">
      {text}
    </blockquote>
  );
}

function MultipleChoice({ prompt, value, onChange, disabled }: Omit<InputProps, 'type'>) {
  const name = useId();
  return (
    <div>
      <Passage text={prompt.passage} />
      <fieldset className="flex flex-col gap-3">
      <legend className="mb-3 text-lg font-medium">{prompt.question}</legend>
      <div className="flex flex-col gap-2">
        {prompt.options?.map((o) => (
          <label key={o.id} className={choiceClass(value.optionId === o.id)}>
            <input
              type="radio"
              name={name}
              className="size-4 accent-[var(--color-primary)]"
              checked={value.optionId === o.id}
              disabled={disabled}
              onChange={() => onChange({ optionId: o.id })}
            />
            {o.text}
          </label>
        ))}
      </div>
      </fieldset>
    </div>
  );
}

function TrueFalse({ prompt, value, onChange, disabled }: Omit<InputProps, 'type'>) {
  const name = useId();
  return (
    <div>
      <Passage text={prompt.passage} />
      <fieldset>
      <legend className="mb-3 text-lg font-medium">{prompt.statement}</legend>
      <div className="grid grid-cols-2 gap-2">
        {[
          { v: true, label: 'Verdadero · True' },
          { v: false, label: 'Falso · False' },
        ].map((o) => (
          <label key={String(o.v)} className={cn(choiceClass(value.value === o.v), 'justify-center')}>
            <input
              type="radio"
              name={name}
              className="sr-only"
              checked={value.value === o.v}
              disabled={disabled}
              onChange={() => onChange({ value: o.v })}
            />
            {o.label}
          </label>
        ))}
      </div>
      </fieldset>
    </div>
  );
}

function FillBlank({ prompt, value, onChange, disabled }: Omit<InputProps, 'type'>) {
  const parts = (prompt.text ?? '').split(BLANK);
  const values = value.values ?? Array.from({ length: parts.length - 1 }, () => '');
  return (
    <p className="text-lg leading-[2.6]">
      {parts.map((part, i) => (
        <Fragment key={i}>
          {part}
          {i < parts.length - 1 && (
            <input
              aria-label={`Espacio ${i + 1}`}
              autoCapitalize="off"
              autoCorrect="off"
              spellCheck={false}
              disabled={disabled}
              value={values[i] ?? ''}
              onChange={(e) => {
                const next = [...values];
                next[i] = e.target.value;
                onChange({ values: next });
              }}
              className="mx-1 inline-block min-h-10 w-36 rounded-md border-b-2 border-primary bg-surface-2 px-2 text-center text-base"
            />
          )}
        </Fragment>
      ))}
    </p>
  );
}

/** Ordenar palabras: se trabaja con índices para admitir palabras repetidas. */
function WordOrder({ prompt, value, onChange, disabled }: Omit<InputProps, 'type'>) {
  const tokens = prompt.tokens ?? [];
  const chosen = (value as Answer & { indices?: number[] }).indices ?? [];
  const update = (indices: number[]) =>
    onChange({ tokens: indices.map((i) => tokens[i]!), indices } as Answer & { indices: number[] });
  return (
    <div className="flex flex-col gap-4">
      <div
        aria-label="Tu oración"
        aria-live="polite"
        className="flex min-h-14 flex-wrap items-center gap-2 rounded-lg border-2 border-dashed border-border p-3"
      >
        {chosen.length === 0 && <span className="text-muted">Toca las palabras en orden…</span>}
        {chosen.map((ti, pos) => (
          <button
            key={`${ti}-${pos}`}
            type="button"
            disabled={disabled}
            onClick={() => update(chosen.filter((_, p) => p !== pos))}
            className="inline-flex min-h-10 items-center gap-1 rounded-md bg-primary px-3 text-on-primary"
            aria-label={`Quitar ${tokens[ti]}`}
          >
            {tokens[ti]}
            {!disabled && <X aria-hidden className="size-3.5" />}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap gap-2" aria-label="Palabras disponibles">
        {tokens.map((t, i) =>
          chosen.includes(i) ? null : (
            <button
              key={i}
              type="button"
              disabled={disabled}
              onClick={() => update([...chosen, i])}
              className="min-h-10 rounded-md border border-border bg-surface px-3 hover:bg-surface-2"
            >
              {t}
            </button>
          ),
        )}
      </div>
    </div>
  );
}

function Matching({ prompt, value, onChange, disabled }: Omit<InputProps, 'type'>) {
  const pairs = value.pairs ?? {};
  return (
    <div className="flex flex-col gap-3">
      {prompt.left?.map((l) => (
        <div key={l.id} className="grid grid-cols-[1fr_auto_1.2fr] items-center gap-3">
          <label htmlFor={`m-${l.id}`} className="font-medium">
            {l.text}
          </label>
          <span aria-hidden className="text-muted">
            →
          </span>
          <select
            id={`m-${l.id}`}
            disabled={disabled}
            value={pairs[l.id] ?? ''}
            onChange={(e) => onChange({ pairs: { ...pairs, [l.id]: e.target.value } })}
            className="min-h-11 rounded-lg border border-border bg-surface px-2"
          >
            <option value="">Elige…</option>
            {prompt.right?.map((r) => (
              <option key={r.id} value={r.id}>
                {r.text}
              </option>
            ))}
          </select>
        </div>
      ))}
    </div>
  );
}

function ErrorSpotting({ prompt, value, onChange, disabled }: Omit<InputProps, 'type'>) {
  return (
    <div role="radiogroup" aria-label="Palabras de la oración" className="flex flex-wrap gap-2 text-lg">
      {prompt.tokens?.map((t, i) => (
        <button
          key={i}
          type="button"
          role="radio"
          aria-checked={value.index === i}
          disabled={disabled}
          onClick={() => onChange({ index: i })}
          className={cn(
            'min-h-11 rounded-md border px-3',
            value.index === i ? 'border-danger bg-danger/10 font-semibold' : 'border-border bg-surface hover:bg-surface-2',
          )}
        >
          {t}
        </button>
      ))}
    </div>
  );
}

export function ExerciseInput(props: InputProps) {
  switch (props.type) {
    case 'MULTIPLE_CHOICE':
      return <MultipleChoice {...props} />;
    case 'TRUE_FALSE':
      return <TrueFalse {...props} />;
    case 'FILL_BLANK':
      return <FillBlank {...props} />;
    case 'WORD_ORDER':
      return <WordOrder {...props} />;
    case 'MATCHING':
      return <Matching {...props} />;
    case 'ERROR_SPOTTING':
      return <ErrorSpotting {...props} />;
  }
}
