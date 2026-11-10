import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { CheckCircle2, Lightbulb, XCircle } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { ProgressBar } from '@/components/ui/Badge';
import { RichText } from '@/components/ui/RichText';
import { errorMessage } from '@/features/auth/schemas';
import { useT } from '@/lib/lang';
import { ExerciseInput } from './ExerciseInput';
import { describeSolution, isComplete, toPayload, type Answer, type ExerciseType, type Prompt, type Solution } from './types';

type Exercise = Schemas['ExerciseView'];
type AttemptResult = Schemas['AttemptResult'];

function newKey(): string {
  return crypto.randomUUID();
}

/**
 * Presenta los ejercicios uno por uno. La corrección la hace el servidor; aquí solo se muestra el resultado.
 * Permite reintentar tras un error (el puntaje de la lección usa el primer intento).
 */
export function ExercisePlayer({
  exercises,
  runId,
  onFinished,
}: {
  exercises: Exercise[];
  runId: string;
  onFinished: () => void;
}) {
  const t = useT();
  const [index, setIndex] = useState(0);
  const [answer, setAnswer] = useState<Answer>({});
  const [result, setResult] = useState<AttemptResult | null>(null);
  const [hintShown, setHintShown] = useState(false);
  const startedAt = useRef<number | null>(null);
  // Una clave por intento: si la red reintenta el mismo envío, el servidor no lo cuenta dos veces.
  const attemptKey = useRef<string | null>(null);

  useEffect(() => {
    startedAt.current = Date.now();
  }, [index]);

  const exercise = exercises[index]!;
  const type = exercise.type as ExerciseType;
  const prompt = (exercise.prompt ?? {}) as unknown as Prompt;

  const submit = useMutation({
    mutationFn: () => {
      attemptKey.current ??= newKey();
      const elapsed = startedAt.current === null ? undefined : Date.now() - startedAt.current;
      return unwrap(
        api.POST('/api/v1/exercises/{publicId}/attempts', {
          params: { path: { publicId: exercise.publicId ?? '' } },
          body: {
            runId,
            answer: toPayload(type, answer) as unknown as Record<string, never>,
            hintsUsed: hintShown ? 1 : 0,
            durationMs: elapsed === undefined ? undefined : Math.min(elapsed, 3_600_000),
            idempotencyKey: attemptKey.current,
          },
        }),
      );
    },
    onSuccess: setResult,
  });

  const resetForNextAttempt = () => {
    attemptKey.current = null;
    startedAt.current = Date.now();
    setResult(null);
    submit.reset();
  };

  const retry = () => {
    resetForNextAttempt();
    setAnswer({});
  };

  const next = () => {
    if (index + 1 >= exercises.length) {
      onFinished();
      return;
    }
    resetForNextAttempt();
    setAnswer({});
    setHintShown(false);
    setIndex(index + 1);
  };

  const answered = result !== null;

  return (
    <section aria-labelledby="exercise-heading" className="flex flex-col gap-5">
      <div className="flex items-center justify-between gap-4 text-sm text-muted">
        <span id="exercise-heading">
          Ejercicio {index + 1} de {exercises.length}
        </span>
        <div className="w-40">
          <ProgressBar value={index + (answered ? 1 : 0)} max={exercises.length} label="Avance de la práctica" />
        </div>
      </div>

      <p className="font-medium text-muted">{t(exercise.instructions)}</p>

      <ExerciseInput type={type} prompt={prompt} value={answer} onChange={setAnswer} disabled={answered} />

      {exercise.hint && !answered && (
        <div>
          {hintShown ? (
            <p className="flex items-start gap-2 rounded-lg bg-warning/10 p-3 text-sm">
              <Lightbulb aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
              {t(exercise.hint)}
            </p>
          ) : (
            <Button variant="ghost" icon={<Lightbulb aria-hidden className="size-4" />} onClick={() => setHintShown(true)}>
              Ver pista
            </Button>
          )}
        </div>
      )}

      {submit.isError && <Alert tone="error">{errorMessage(submit.error)}</Alert>}

      {result && (
        <div
          role="status"
          aria-live="polite"
          className={
            result.correct
              ? 'rounded-xl border border-success/40 bg-success/10 p-4'
              : 'rounded-xl border border-danger/40 bg-danger/10 p-4'
          }
        >
          <p className="flex items-center gap-2 text-lg font-semibold">
            {result.correct ? (
              <>
                <CheckCircle2 aria-hidden className="size-6 text-success" /> ¡Correcto!
              </>
            ) : (
              <>
                <XCircle aria-hidden className="size-6 text-danger" />
                {(result.score ?? 0) > 0 ? `Casi: ${Math.round((result.score ?? 0) * 100)} % correcto` : 'No es correcto'}
              </>
            )}
          </p>
          {!result.correct && (
            <div className="mt-2 text-sm">
              <span className="font-medium">Respuesta correcta: </span>
              {describeSolution(type, prompt, (result.solution ?? {}) as unknown as Solution).map((line) => (
                <span key={line} className="block font-mono">
                  {line}
                </span>
              ))}
            </div>
          )}
          <div className="mt-2 text-sm">
            <RichText source={t(result.feedback)} />
          </div>
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        {!answered ? (
          <Button
            onClick={() => submit.mutate()}
            disabled={!isComplete(type, prompt, answer)}
            loading={submit.isPending}
          >
            Comprobar
          </Button>
        ) : (
          <>
            <Button onClick={next}>{index + 1 >= exercises.length ? 'Terminar práctica' : 'Siguiente'}</Button>
            {!result.correct && (
              <Button variant="secondary" onClick={retry}>
                Intentar de nuevo
              </Button>
            )}
          </>
        )}
      </div>
    </section>
  );
}
