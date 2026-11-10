import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Clock, PartyPopper, RotateCcw } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge, ProgressBadge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { RichText } from '@/components/ui/RichText';
import { errorMessage } from '@/features/auth/schemas';
import { ExercisePlayer } from '@/features/exercises/ExercisePlayer';
import { LanguageToggle, useLang, useT } from '@/lib/lang';
import { percent, useLearningSummary, useLesson } from './api';

interface Example {
  en?: string;
  es?: string;
}
interface VocabItem {
  term?: string;
  translation?: string;
  partOfSpeech?: string;
  ipa?: string;
}

type Phase = { kind: 'study' } | { kind: 'practice'; runId: string } | { kind: 'done'; result: Schemas['RunCompleted'] };

export function LessonPage() {
  const { lessonId = '' } = useParams();
  const lesson = useLesson(lessonId);
  const t = useT();
  const { lang } = useLang();
  const qc = useQueryClient();
  const [phase, setPhase] = useState<Phase>({ kind: 'study' });

  const start = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/lessons/{publicId}/runs', { params: { path: { publicId: lessonId } } })),
    onSuccess: (run) => {
      setPhase({ kind: 'practice', runId: run.runId ?? '' });
      window.scrollTo({ top: 0 });
    },
  });

  const complete = useMutation({
    mutationFn: (runId: string) =>
      unwrap(api.POST('/api/v1/lesson-runs/{runId}/complete', { params: { path: { runId } } })),
    onSuccess: (result) => {
      setPhase({ kind: 'done', result });
      qc.invalidateQueries({ queryKey: ['learning'] });
    },
  });

  if (lesson.isPending) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-4" aria-busy="true" aria-label="Cargando lección">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-40" />
      </div>
    );
  }
  if (lesson.isError) {
    return (
      <div className="mx-auto max-w-3xl">
        <Alert tone="error">{errorMessage(lesson.error)}</Alert>
      </div>
    );
  }

  const l = lesson.data;
  const examples = (l.examples ?? []) as unknown as Example[];
  const vocabulary = (l.vocabulary ?? []) as unknown as VocabItem[];
  const exercises = l.exercises ?? [];

  return (
    <article className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link to="/app/plan" className="inline-flex items-center gap-1 text-sm text-muted hover:text-text">
          <ArrowLeft aria-hidden className="size-4" /> Mi plan
        </Link>
        <LanguageToggle />
      </div>

      <header>
        <p className="text-sm text-muted">
          {t(l.courseTitle)} · {t(l.moduleTitle)}
        </p>
        <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl" lang={lang}>
          {t(l.title)}
        </h1>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Badge tone="accent">{l.level}</Badge>
          <Badge>
            <Clock aria-hidden className="size-3.5" /> {l.estimatedMinutes} min
          </Badge>
          <ProgressBadge state={l.progress?.state} />
          {l.progress?.bestScore != null && <Badge>Mejor resultado: {percent(l.progress.bestScore)}</Badge>}
        </div>
      </header>

      {phase.kind === 'study' && (
        <>
          <Card>
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">Objetivo</h2>
            <p className="mt-1" lang={lang}>
              {t(l.objective)}
            </p>
            {(l.prerequisites ?? []).length > 0 && (
              <div className="mt-3 text-sm">
                <span className="text-muted">Conviene haber estudiado: </span>
                {l.prerequisites!.map((p, i) => (
                  <span key={p.publicId}>
                    {i > 0 && ', '}
                    <Link to={`/app/lessons/${p.publicId}`} className="text-primary underline-offset-4 hover:underline">
                      {t(p.title)}
                    </Link>{' '}
                    <ProgressBadge state={p.state} />
                  </span>
                ))}
              </div>
            )}
          </Card>

          <Card>
            <h2 className="mb-2 text-lg font-semibold">{lang === 'en' ? 'Explanation' : 'Explicación'}</h2>
            <div lang={lang}>
              <RichText source={t(l.explanation)} />
            </div>
          </Card>

          {examples.length > 0 && (
            <Card>
              <h2 className="mb-3 text-lg font-semibold">{lang === 'en' ? 'Examples' : 'Ejemplos'}</h2>
              <ul className="flex flex-col gap-3">
                {examples.map((ex, i) => (
                  <li key={i} className="border-l-4 border-primary/40 pl-3">
                    <p lang="en" className="font-medium">
                      {ex.en}
                    </p>
                    {lang === 'es' && (
                      <p lang="es" className="text-sm text-muted">
                        {ex.es}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {vocabulary.length > 0 && (
            <Card className="overflow-x-auto">
              <h2 className="mb-3 text-lg font-semibold">{lang === 'en' ? 'Vocabulary' : 'Vocabulario'}</h2>
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase tracking-wide text-muted">
                  <tr>
                    <th scope="col" className="py-2 pr-3">Inglés</th>
                    <th scope="col" className="py-2 pr-3">Pronunciación</th>
                    <th scope="col" className="py-2">Español</th>
                  </tr>
                </thead>
                <tbody>
                  {vocabulary.map((v) => (
                    <tr key={v.term} className="border-t border-border">
                      <td lang="en" className="py-2 pr-3 font-medium">
                        {v.term}
                      </td>
                      <td className="py-2 pr-3 font-mono text-muted">{v.ipa}</td>
                      <td lang="es" className="py-2">
                        {v.translation}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          )}

          {start.isError && <Alert tone="error">{errorMessage(start.error)}</Alert>}
          <div className="sticky bottom-20 z-10 lg:bottom-4">
            <Button fullWidth loading={start.isPending} onClick={() => start.mutate()} disabled={exercises.length === 0}>
              {l.progress?.completedRuns ? 'Practicar de nuevo' : 'Empezar práctica'} ({exercises.length} ejercicios)
            </Button>
          </div>
        </>
      )}

      {phase.kind === 'practice' && (
        <Card>
          <ExercisePlayer
            exercises={exercises}
            runId={phase.runId}
            onFinished={() => complete.mutate(phase.runId)}
          />
          {complete.isError && (
            <div className="mt-4">
              <Alert tone="error">{errorMessage(complete.error)}</Alert>
            </div>
          )}
        </Card>
      )}

      {phase.kind === 'done' && (
        <LessonResult result={phase.result} currentId={lessonId} onRepeat={() => setPhase({ kind: 'study' })} />
      )}
    </article>
  );
}

function LessonResult({
  result,
  currentId,
  onRepeat,
}: {
  result: Schemas['RunCompleted'];
  currentId: string;
  onRepeat: () => void;
}) {
  const summary = useLearningSummary();
  // Si la recomendación sigue siendo esta misma lección, el botón "Repasar" ya cubre ese caso.
  const next = summary.data?.recommended?.publicId === currentId ? undefined : summary.data?.recommended;
  const t = useT();
  const state = result.state ?? 'IN_PROGRESS';
  const messages: Record<string, string> = {
    MASTERED: '¡Lección dominada! La repasarás más adelante para que no se olvide.',
    PRACTICED:
      'Buen trabajo. Para dominarla, vuelve a practicarla otro día con 80 % o más: la práctica espaciada fija lo aprendido.',
    IN_PROGRESS: 'Sigue practicando: repasa la explicación e inténtalo de nuevo. Los errores son parte del aprendizaje.',
  };
  return (
    <Card className="flex flex-col items-center gap-4 py-8 text-center" role="status" aria-live="polite">
      {result.newlyMastered && <PartyPopper aria-hidden className="size-12 text-accent" />}
      <h2 className="text-2xl font-bold">{percent(result.score)}</h2>
      <p className="text-muted">
        {result.correctCount} de {result.totalExercises} correctos al primer intento
      </p>
      <ProgressBadge state={state} />
      <p className="max-w-md">{messages[state]}</p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        <Button variant="secondary" icon={<RotateCcw aria-hidden className="size-4" />} onClick={onRepeat}>
          Repasar esta lección
        </Button>
        {next && (
          <Link
            to={`/app/lessons/${next.publicId}`}
            className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 font-semibold text-on-primary hover:bg-primary-hover"
          >
            Siguiente: {t(next.title)}
          </Link>
        )}
      </div>
    </Card>
  );
}
