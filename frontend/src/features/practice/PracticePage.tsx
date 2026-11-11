import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { BookOpen, CheckCircle2, ListChecks, RotateCcw, Sparkles } from 'lucide-react';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { LanguageToggle, useLang } from '@/lib/lang';
import { cn } from '@/lib/cn';
import {
  errorCategoryLabels,
  formatInterval,
  grades,
  posLabels,
  practiceKeys,
  useErrorQueue,
  useVocabularyQueue,
  type ErrorItem,
  type QueueItem,
} from './api';

function newId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

// ---- Tarjetas ---------------------------------------------------------------------------------

function Flashcard({ item, onDone }: { item: QueueItem; onDone: () => void }) {
  const queryClient = useQueryClient();
  const card = item.card!;
  const [revealed, setRevealed] = useState(false);
  const shownAt = useRef(0);
  const reviewId = useRef(newId());

  useEffect(() => {
    shownAt.current = Date.now();
  }, []);

  const review = useMutation({
    mutationFn: (quality: number) =>
      unwrap(
        api.POST('/api/v1/vocabulary/cards/{id}/reviews', {
          params: { path: { id: card.id ?? '' } },
          body: { quality, reviewId: reviewId.current, durationMs: Date.now() - shownAt.current },
        }),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['vocabulary', 'words'] });
      onDone();
    },
  });

  // Atajos: espacio para mostrar, 1–4 para calificar.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || review.isPending) return;
      if (!revealed && (e.key === ' ' || e.key === 'Enter')) {
        e.preventDefault();
        setRevealed(true);
      } else if (revealed) {
        const g = grades.find((x) => x.key === e.key);
        if (g) review.mutate(g.quality);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [revealed, review]);

  const preview = (q: number) => item.previews?.find((p) => p.quality === q)?.intervalDays;

  return (
    <Card className="flex flex-col items-center gap-4 py-8 text-center" data-testid="flashcard">
      <div className="flex flex-wrap justify-center gap-2">
        {card.state === 'NEW' && <Badge tone="accent">Nueva</Badge>}
        {card.state === 'RELEARNING' && <Badge tone="warning">La olvidaste la última vez</Badge>}
        <Badge>{card.level}</Badge>
      </div>
      <p lang="en" className="text-3xl font-bold tracking-tight sm:text-4xl">
        {card.term}
      </p>
      <p className="text-muted">
        {card.ipa && <span className="font-mono">{card.ipa}</span>}
        {card.ipa && ' · '}
        {posLabels[card.partOfSpeech ?? ''] ?? card.partOfSpeech}
      </p>
      {revealed ? (
        <>
          <p className="text-xl font-semibold text-primary" data-testid="flashcard-answer">
            {card.translation}
          </p>
          <div className="mt-2 grid w-full max-w-xl grid-cols-2 gap-2 sm:grid-cols-4" role="group" aria-label="¿Qué tan bien la recordaste?">
            {grades.map((g) => (
              <button
                key={g.quality}
                type="button"
                disabled={review.isPending}
                onClick={() => review.mutate(g.quality)}
                title={`${g.hint} (tecla ${g.key})`}
                className={cn(
                  'flex min-h-14 flex-col items-center justify-center rounded-lg border px-2 py-1 text-sm font-semibold disabled:opacity-60',
                  g.quality === 1
                    ? 'border-danger/40 text-danger hover:bg-danger/10'
                    : 'border-border hover:bg-surface-2',
                )}
              >
                {g.label}
                <span className="text-xs font-normal text-muted">{formatInterval(preview(g.quality))}</span>
              </button>
            ))}
          </div>
          <p className="text-xs text-muted">Debajo de cada botón ves cuándo volverá a aparecer la palabra.</p>
        </>
      ) : (
        <Button onClick={() => setRevealed(true)}>Mostrar significado</Button>
      )}
      {review.isError && <Alert tone="error">{errorMessage(review.error)}</Alert>}
    </Card>
  );
}

function CardsSession() {
  const queue = useVocabularyQueue();
  const queryClient = useQueryClient();
  const [index, setIndex] = useState(0);
  const [reviewed, setReviewed] = useState(0);

  if (queue.isPending) return <Skeleton className="h-64" />;
  if (queue.isError) return <Alert tone="error">{errorMessage(queue.error)}</Alert>;
  const items = queue.data.items ?? [];
  const stats = queue.data.stats;

  if (items.length === 0 || index >= items.length) {
    const nothingYet = (stats?.total ?? 0) === 0;
    return (
      <Card className="flex flex-col items-center gap-3 py-10 text-center" role="status">
        {nothingYet ? (
          <>
            <BookOpen aria-hidden className="size-8 text-primary" />
            <h2 className="text-lg font-semibold">Aún no tienes palabras para repasar</h2>
            <p className="max-w-md text-sm text-muted">
              Al completar una lección, su vocabulario se agrega aquí y lo repasarás en el momento justo para no olvidarlo.
            </p>
            <Link to="/app/plan" className="font-semibold text-primary hover:underline">
              Ir a mi plan
            </Link>
          </>
        ) : (
          <>
            <CheckCircle2 aria-hidden className="size-8 text-success" />
            <h2 className="text-lg font-semibold">
              {reviewed > 0 ? `¡Listo! Repasaste ${reviewed} ${reviewed === 1 ? 'palabra' : 'palabras'}.` : 'No tienes repasos pendientes hoy.'}
            </h2>
            <p className="text-sm text-muted">Vuelve mañana: las palabras aparecen justo cuando estás por olvidarlas.</p>
            {reviewed > 0 && (
              <Button
                variant="secondary"
                icon={<RotateCcw aria-hidden className="size-4" />}
                onClick={() => {
                  setIndex(0);
                  setReviewed(0);
                  void queryClient.invalidateQueries({ queryKey: practiceKeys.queue });
                }}
              >
                Buscar más repasos
              </Button>
            )}
          </>
        )}
      </Card>
    );
  }

  const item = items[index]!;
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3 text-sm text-muted" aria-live="polite">
        <span>
          Tarjeta {index + 1} de {items.length}
        </span>
        <span>· {queue.data.dueCount} para repasar</span>
        <span>· {queue.data.newCount} nuevas</span>
      </div>
      <div
        className="h-1.5 overflow-hidden rounded-full bg-surface-2"
        role="progressbar"
        aria-label="Avance del repaso"
        aria-valuemin={0}
        aria-valuemax={items.length}
        aria-valuenow={index}
      >
        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${(index / items.length) * 100}%` }} />
      </div>
      <Flashcard
        key={item.card?.id}
        item={item}
        onDone={() => {
          setReviewed((n) => n + 1);
          setIndex((i) => i + 1);
        }}
      />
      <p className="hidden text-center text-xs text-muted sm:block">Atajos: espacio para mostrar, 1–4 para calificar.</p>
    </div>
  );
}

// ---- Errores -----------------------------------------------------------------------------------

function ErrorExercise({ item, onNext }: { item: ErrorItem; onNext: () => void }) {
  const { lang } = useLang();
  const queryClient = useQueryClient();
  const [answer, setAnswer] = useState('');
  const check = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/api/v1/practice/errors/{id}/answer', { params: { path: { id: item.id ?? '' } }, body: { answer } })),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['recommendations'] }),
  });
  const result = check.data;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (answer.trim()) check.mutate();
  };

  return (
    <Card className="flex flex-col gap-4" data-testid="error-exercise">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="primary">{errorCategoryLabels[item.category ?? ''] ?? item.category}</Badge>
        {(item.categoryOpenCount ?? 0) >= 2 && <Badge tone="warning">Error frecuente ({item.categoryOpenCount})</Badge>}
        <span className="text-xs text-muted">
          Aciertos seguidos: {item.correctStreak} de {item.streakToResolve}
        </span>
      </div>
      <div>
        <p className="text-sm text-muted">El profesor te corrigió esto. Escríbelo bien:</p>
        <p lang="en" className="mt-1 text-xl font-semibold">
          <span className="text-danger line-through decoration-1">“{item.original}”</span>
        </p>
      </div>
      {result ? (
        <div className="flex flex-col gap-3">
          <Alert tone={result.correct ? 'success' : 'error'}>
            {result.correct ? '¡Correcto!' : 'Todavía no.'} La forma correcta es{' '}
            <strong lang="en">“{result.expected}”</strong>.
            {result.resolved && ' Superaste este error 🎉'}
          </Alert>
          <div className="flex items-start gap-2 text-sm">
            <p className="flex-1 text-muted">{lang === 'en' ? result.explanationEn || result.explanationEs : result.explanationEs || result.explanationEn}</p>
            <LanguageToggle />
          </div>
          <div>
            <Button onClick={onNext}>Siguiente</Button>
          </div>
        </div>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <TextField
              label="Tu corrección"
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              lang="en"
              maxLength={500}
            />
          </div>
          <Button type="submit" loading={check.isPending} disabled={!answer.trim()}>
            Comprobar
          </Button>
        </form>
      )}
      {check.isError && <Alert tone="error">{errorMessage(check.error)}</Alert>}
    </Card>
  );
}

function ErrorsSession() {
  const queue = useErrorQueue();
  const queryClient = useQueryClient();
  const [index, setIndex] = useState(0);
  if (queue.isPending) return <Skeleton className="h-48" />;
  if (queue.isError) return <Alert tone="error">{errorMessage(queue.error)}</Alert>;
  const items = queue.data;
  if (items.length === 0 || index >= items.length) {
    return (
      <Card className="flex flex-col items-center gap-3 py-10 text-center" role="status">
        <ListChecks aria-hidden className="size-8 text-primary" />
        <h2 className="text-lg font-semibold">
          {items.length === 0 ? 'No tienes errores pendientes' : 'Terminaste esta ronda'}
        </h2>
        <p className="max-w-md text-sm text-muted">
          Cuando el profesor IA te corrija algo, aparecerá aquí para que lo practiques hasta dominarlo (2 aciertos seguidos).
        </p>
        {items.length > 0 ? (
          <Button
            variant="secondary"
            onClick={() => {
              setIndex(0);
              void queryClient.invalidateQueries({ queryKey: practiceKeys.errors });
            }}
          >
            Otra ronda
          </Button>
        ) : (
          <Link to="/app/tutor" className="font-semibold text-primary hover:underline">
            Conversar con el profesor
          </Link>
        )}
      </Card>
    );
  }
  const item = items[index]!;
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted" aria-live="polite">
        Ejercicio {index + 1} de {items.length}
      </p>
      <ErrorExercise key={item.id} item={item} onNext={() => setIndex((i) => i + 1)} />
    </div>
  );
}

// ---- Página ------------------------------------------------------------------------------------

export function PracticePage() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'errors' ? 'errors' : 'cards';
  const tabs = [
    { id: 'cards', label: 'Palabras', icon: Sparkles },
    { id: 'errors', label: 'Mis errores', icon: ListChecks },
  ] as const;

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Repaso del día</h1>
          <p className="mt-1 text-muted">Repetición espaciada de tu vocabulario y práctica de tus errores. Sin costo.</p>
        </div>
        <Link to="/app/vocabulary" className="text-sm font-semibold text-primary hover:underline">
          Ver mis palabras
        </Link>
      </div>
      <div role="tablist" aria-label="Tipo de repaso" className="flex gap-1 rounded-lg bg-surface-2 p-1">
        {tabs.map((t) => {
          const Icon = t.icon;
          const active = tab === t.id;
          return (
            <button
              key={t.id}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() => setParams(t.id === 'errors' ? { tab: 'errors' } : {}, { replace: true })}
              className={cn(
                'flex min-h-11 flex-1 items-center justify-center gap-2 rounded-md text-sm font-semibold',
                active ? 'bg-surface text-primary shadow-sm' : 'text-muted hover:text-text',
              )}
            >
              <Icon aria-hidden className="size-4" />
              {t.label}
            </button>
          );
        })}
      </div>
      <div role="tabpanel">{tab === 'cards' ? <CardsSession /> : <ErrorsSession />}</div>
    </div>
  );
}
