import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CheckCheck } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { contentKeys, statusBadge } from './AdminContentPage';

type AdminLesson = Schemas['AdminLesson'];
type AdminExercise = Schemas['AdminExercise'];
type Status = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
type ExerciseType = NonNullable<AdminExercise['type']>;

function pretty(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2);
}

function TextArea({
  label,
  value,
  onChange,
  rows = 4,
  mono = false,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  rows?: number;
  mono?: boolean;
}) {
  const id = `ta-${label.replace(/\W+/g, '-')}`;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <textarea
        id={id}
        rows={rows}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        spellCheck={!mono}
        className={`rounded-lg border border-border bg-surface px-3 py-2 ${mono ? 'font-mono text-xs' : 'text-sm'}`}
      />
    </div>
  );
}

function Bilingual({
  label,
  value,
  onChange,
  rows,
}: {
  label: string;
  value: { es: string; en: string };
  onChange: (v: { es: string; en: string }) => void;
  rows?: number;
}) {
  return (
    <div className="grid gap-3 md:grid-cols-2">
      <TextArea label={`${label} (español)`} value={value.es} rows={rows} onChange={(es) => onChange({ ...value, es })} />
      <TextArea label={`${label} (inglés)`} value={value.en} rows={rows} onChange={(en) => onChange({ ...value, en })} />
    </div>
  );
}

function loc(v?: { es?: string; en?: string } | null) {
  return { es: v?.es ?? '', en: v?.en ?? '' };
}

const templates: Record<ExerciseType, { prompt: unknown; solution: unknown }> = {
  MULTIPLE_CHOICE: {
    prompt: { question: 'She ___ a teacher.', options: [{ id: 'a', text: 'am' }, { id: 'b', text: 'is' }] },
    solution: { correctOptionId: 'b' },
  },
  TRUE_FALSE: { prompt: { statement: '...' }, solution: { value: true } },
  FILL_BLANK: { prompt: { text: 'I ___ from Mexico.' }, solution: { answers: [['am']] } },
  WORD_ORDER: { prompt: { tokens: ['you', 'are', 'How', '?'] }, solution: { sentences: [['How', 'are', 'you', '?']] } },
  MATCHING: {
    prompt: { left: [{ id: 'l1', text: 'dog' }, { id: 'l2', text: 'cat' }], right: [{ id: 'r1', text: 'gato' }, { id: 'r2', text: 'perro' }] },
    solution: { pairs: { l1: 'r2', l2: 'r1' } },
  },
  ERROR_SPOTTING: { prompt: { tokens: ['She', 'are', 'here', '.'] }, solution: { errorIndex: 1, correction: 'is' } },
};

function ExerciseForm({
  initial,
  lessonId,
  onSaved,
  submitLabel,
}: {
  initial?: AdminExercise;
  lessonId: string;
  onSaved: () => void;
  submitLabel: string;
}) {
  const [type, setType] = useState<ExerciseType>(initial?.type ?? 'MULTIPLE_CHOICE');
  const [slug, setSlug] = useState(initial?.slug ?? '');
  const [instructions, setInstructions] = useState(loc(initial?.instructions));
  const [feedback, setFeedback] = useState(loc(initial?.feedback));
  const [hint, setHint] = useState(loc(initial?.hint));
  const [prompt, setPrompt] = useState(pretty(initial?.prompt ?? templates[type].prompt));
  const [solution, setSolution] = useState(pretty(initial?.solution ?? templates[type].solution));
  const [skill, setSkill] = useState(initial?.skill ?? 'GRAMMAR');

  const save = useMutation({
    mutationFn: () => {
      const body = {
        slug,
        type,
        skill,
        level: initial?.level ?? 'A1',
        instructions,
        promptJson: prompt,
        solutionJson: solution,
        feedback,
        hint: hint.es && hint.en ? hint : undefined,
      };
      return initial
        ? unwrap(api.PUT('/api/v1/admin/content/exercises/{publicId}', { params: { path: { publicId: initial.publicId ?? '' } }, body }))
        : unwrap(api.POST('/api/v1/admin/content/lessons/{publicId}/exercises', { params: { path: { publicId: lessonId } }, body }));
    },
    onSuccess: onSaved,
  });

  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
      {save.isSuccess && initial && <Alert tone="success">Ejercicio guardado.</Alert>}
      <div className="grid gap-3 sm:grid-cols-3">
        {!initial && <TextField label="Identificador (slug)" value={slug} onChange={(e) => setSlug(e.target.value)} />}
        <div className="flex flex-col gap-1.5">
          <label htmlFor="ex-type" className="text-sm font-medium">
            Tipo
          </label>
          <select
            id="ex-type"
            value={type}
            onChange={(e) => {
              const next = e.target.value as ExerciseType;
              setType(next);
              if (!initial) {
                setPrompt(pretty(templates[next].prompt));
                setSolution(pretty(templates[next].solution));
              }
            }}
            className="min-h-11 rounded-lg border border-border bg-surface px-2"
          >
            {Object.keys(templates).map((k) => (
              <option key={k}>{k}</option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="ex-skill" className="text-sm font-medium">
            Habilidad
          </label>
          <select
            id="ex-skill"
            value={skill}
            onChange={(e) => setSkill(e.target.value as typeof skill)}
            className="min-h-11 rounded-lg border border-border bg-surface px-2"
          >
            <option>VOCABULARY</option>
            <option>GRAMMAR</option>
            <option>READING</option>
          </select>
        </div>
      </div>
      <Bilingual label="Instrucciones" value={instructions} onChange={setInstructions} rows={2} />
      <div className="grid gap-3 md:grid-cols-2">
        <TextArea label="Enunciado (JSON)" value={prompt} onChange={setPrompt} rows={8} mono />
        <TextArea label="Solución (JSON, nunca se envía al alumno antes de responder)" value={solution} onChange={setSolution} rows={8} mono />
      </div>
      <Bilingual label="Retroalimentación" value={feedback} onChange={setFeedback} rows={2} />
      <Bilingual label="Pista (opcional)" value={hint} onChange={setHint} rows={2} />
      <div>
        <Button type="submit" loading={save.isPending}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

function StatusButtons({ status, onChange, pending }: { status?: string; onChange: (s: Status) => void; pending: boolean }) {
  const buttons: { s: Status; label: string; variant: 'primary' | 'secondary' | 'danger' }[] = [
    { s: 'PUBLISHED', label: 'Publicar', variant: 'primary' },
    { s: 'DRAFT', label: 'Pasar a borrador', variant: 'secondary' },
    { s: 'ARCHIVED', label: 'Archivar', variant: 'danger' },
  ];
  return (
    <div className="flex flex-wrap gap-2">
      {buttons
        .filter((b) => b.s !== status)
        .map((b) => (
          <Button key={b.s} variant={b.variant} disabled={pending} onClick={() => onChange(b.s)}>
            {b.label}
          </Button>
        ))}
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold">{title}</h2>
      {children}
    </Card>
  );
}

function LessonTextsForm({ lesson, onSaved }: { lesson: AdminLesson; onSaved: () => void }) {
  const [title, setTitle] = useState(loc(lesson.title));
  const [objective, setObjective] = useState(loc(lesson.objective));
  const [explanation, setExplanation] = useState(loc(lesson.explanation));
  const [examples, setExamples] = useState(pretty(lesson.examples ?? []));
  const [vocabulary, setVocabulary] = useState(pretty(lesson.vocabulary ?? []));
  const [minutes, setMinutes] = useState(String(lesson.estimatedMinutes ?? 10));
  const save = useMutation({
    mutationFn: () =>
      unwrap(
        api.PUT('/api/v1/admin/content/lessons/{publicId}', {
          params: { path: { publicId: lesson.publicId ?? '' } },
          body: {
            title,
            objective,
            explanation,
            examplesJson: examples,
            vocabularyJson: vocabulary,
            estimatedMinutes: Number(minutes),
          },
        }),
      ),
    onSuccess: onSaved,
  });
  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
      {save.isSuccess && <Alert tone="success">Lección guardada. Quedó marcada como pendiente de revisión.</Alert>}
      <Bilingual label="Título" value={title} onChange={setTitle} rows={1} />
      <Bilingual label="Objetivo" value={objective} onChange={setObjective} rows={2} />
      <Bilingual label="Explicación (## título, - lista, **negrita**)" value={explanation} onChange={setExplanation} rows={12} />
      <div className="grid gap-3 md:grid-cols-2">
        <TextArea label='Ejemplos (JSON: [{"en":"...","es":"..."}])' value={examples} onChange={setExamples} rows={6} mono />
        <TextArea label='Vocabulario (JSON: [{"term","translation","ipa"}])' value={vocabulary} onChange={setVocabulary} rows={6} mono />
      </div>
      <div className="w-40">
        <TextField label="Minutos estimados" type="number" min={1} max={180} value={minutes} onChange={(e) => setMinutes(e.target.value)} />
      </div>
      <div>
        <Button type="submit" loading={save.isPending}>
          Guardar lección
        </Button>
      </div>
    </form>
  );
}

export function AdminLessonEditor() {
  const { lessonId = '' } = useParams();
  const qc = useQueryClient();
  const [adding, setAdding] = useState(false);
  const lesson = useQuery({
    queryKey: contentKeys.lesson(lessonId),
    queryFn: () => unwrap(api.GET('/api/v1/admin/content/lessons/{publicId}', { params: { path: { publicId: lessonId } } })),
  });
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['admin', 'content'] });
    qc.invalidateQueries({ queryKey: ['learning'] });
  };
  const setStatus = useMutation({
    mutationFn: (status: Status) =>
      unwrap(api.POST('/api/v1/admin/content/lessons/{publicId}/status', { params: { path: { publicId: lessonId } }, body: { status } })),
    onSuccess: refresh,
  });
  const review = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/admin/content/lessons/{publicId}/review', { params: { path: { publicId: lessonId } } })),
    onSuccess: refresh,
  });
  const exerciseStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: Status }) =>
      unwrap(api.POST('/api/v1/admin/content/exercises/{publicId}/status', { params: { path: { publicId: id } }, body: { status } })),
    onSuccess: refresh,
  });

  if (lesson.isPending) return <Skeleton className="mx-auto h-96 max-w-5xl" />;
  if (lesson.isError) return <Alert tone="error">{errorMessage(lesson.error)}</Alert>;
  const l = lesson.data;

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <Link to="/admin/content" className="inline-flex items-center gap-1 text-sm text-muted hover:text-text">
        <ArrowLeft aria-hidden className="size-4" /> Contenido
      </Link>
      <header className="flex flex-col gap-3">
        <h1 className="text-2xl font-bold tracking-tight">{l.title?.es}</h1>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Badge tone={statusBadge[l.status ?? '']?.tone}>{statusBadge[l.status ?? '']?.label}</Badge>
          {l.reviewed ? <Badge tone="success">Revisada</Badge> : <Badge tone="warning">Sin revisar</Badge>}
          <span className="text-muted">
            {l.slug} · versión {l.contentVersion}
          </span>
        </div>
        {(setStatus.isError || review.isError) && (
          <Alert tone="error">{errorMessage(setStatus.error ?? review.error)}</Alert>
        )}
        <div className="flex flex-wrap gap-2">
          <StatusButtons status={l.status} onChange={(s) => setStatus.mutate(s)} pending={setStatus.isPending} />
          {!l.reviewed && (
            <Button variant="secondary" icon={<CheckCheck aria-hidden className="size-4" />} loading={review.isPending} onClick={() => review.mutate()}>
              Marcar como revisada
            </Button>
          )}
        </div>
      </header>

      <Section title="Textos de la lección">
        <LessonTextsForm key={`${l.publicId}-${l.contentVersion}`} lesson={l} onSaved={refresh} />
      </Section>

      <Section title={`Ejercicios (${l.exercises?.length ?? 0})`}>
        {exerciseStatus.isError && <Alert tone="error">{errorMessage(exerciseStatus.error)}</Alert>}
        {l.exercises?.map((ex) => (
          <details key={ex.publicId} className="rounded-lg border border-border">
            <summary className="flex min-h-12 cursor-pointer flex-wrap items-center gap-2 px-4 py-2">
              <span className="font-mono text-xs text-muted">{ex.type}</span>
              <span className="flex-1 font-medium">{ex.instructions?.es}</span>
              <Badge tone={statusBadge[ex.status ?? '']?.tone}>{statusBadge[ex.status ?? '']?.label}</Badge>
            </summary>
            <div className="flex flex-col gap-4 border-t border-border p-4">
              <StatusButtons
                status={ex.status}
                pending={exerciseStatus.isPending}
                onChange={(status) => exerciseStatus.mutate({ id: ex.publicId ?? '', status })}
              />
              <ExerciseForm initial={ex} lessonId={lessonId} onSaved={refresh} submitLabel="Guardar ejercicio" />
            </div>
          </details>
        ))}
        {adding ? (
          <div className="rounded-lg border border-primary/40 p-4">
            <h3 className="mb-3 font-semibold">Nuevo ejercicio (se crea como borrador)</h3>
            <ExerciseForm
              lessonId={lessonId}
              submitLabel="Crear ejercicio"
              onSaved={() => {
                setAdding(false);
                refresh();
              }}
            />
          </div>
        ) : (
          <div>
            <Button variant="secondary" onClick={() => setAdding(true)}>
              Añadir ejercicio
            </Button>
          </div>
        )}
      </Section>
    </div>
  );
}
