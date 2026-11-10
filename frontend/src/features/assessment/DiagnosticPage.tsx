import { useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ClipboardCheck, Info } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge, ProgressBar } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { meQueryKey } from '@/features/auth/useSession';
import { ExerciseInput } from '@/features/exercises/ExerciseInput';
import { isComplete, toPayload, type Answer, type ExerciseType, type Prompt } from '@/features/exercises/types';
import { confidenceLabels, skillLabels } from '@/features/learning/api';
import { LanguageToggle, useLang, useT } from '@/lib/lang';

type Question = Schemas['NextQuestion'];
type Result = Schemas['Result'];

type Phase =
  | { kind: 'intro' }
  | { kind: 'question'; sessionId: string; question: Question }
  | { kind: 'result'; result: Result };

/** Diagnóstico adaptativo (RF-ASM). No muestra si cada respuesta fue correcta para no sesgar las siguientes. */
export function DiagnosticPage() {
  const [phase, setPhase] = useState<Phase>({ kind: 'intro' });
  const [answer, setAnswer] = useState<Answer>({});
  const qc = useQueryClient();
  const t = useT();

  const loadNext = async (sessionId: string) => {
    const question = await unwrap(
      api.POST('/api/v1/assessments/{sessionId}/next', { params: { path: { sessionId } } }),
    );
    if (question.done) {
      const result = await unwrap(
        api.GET('/api/v1/assessments/{sessionId}/result', { params: { path: { sessionId } } }),
      );
      qc.invalidateQueries({ queryKey: ['learning'] });
      qc.invalidateQueries({ queryKey: meQueryKey });
      setPhase({ kind: 'result', result });
    } else {
      setAnswer({});
      setPhase({ kind: 'question', sessionId, question });
    }
  };

  const start = useMutation({
    mutationFn: async () => {
      const session = await unwrap(api.POST('/api/v1/assessments'));
      await loadNext(session.sessionId ?? '');
    },
  });

  const respond = useMutation({
    mutationFn: async ({ sessionId, itemId, type }: { sessionId: string; itemId: string; type: ExerciseType }) => {
      await unwrap(
        api.POST('/api/v1/assessments/{sessionId}/responses', {
          params: { path: { sessionId } },
          body: { itemId, answer: toPayload(type, answer) as unknown as Record<string, never> },
        }),
      );
      await loadNext(sessionId);
    },
  });

  if (phase.kind === 'intro') {
    return (
      <div className="mx-auto flex max-w-2xl flex-col gap-6">
        <div className="flex items-start justify-between gap-3">
          <h1 className="text-2xl font-bold tracking-tight">Diagnóstico de nivel</h1>
          <LanguageToggle />
        </div>
        <Card className="flex flex-col gap-4">
          <ClipboardCheck aria-hidden className="size-10 text-primary" />
          <p>
            Responderás preguntas de <strong>vocabulario, gramática y lectura</strong>. La dificultad se adapta a tus
            respuestas: si aciertas, sube; si fallas, baja. Son como máximo 24 preguntas y no hay límite de tiempo.
          </p>
          <ul className="list-disc pl-5 text-sm text-muted">
            <li>No verás si cada respuesta es correcta: así el resultado es más fiel.</li>
            <li>Si no sabes una respuesta, elige la que te parezca más probable.</li>
            <li>Puedes salir y continuar después: tu avance se guarda.</li>
          </ul>
          <Alert tone="info">
            El resultado es un <strong>nivel estimado</strong> para orientar tu plan. No es una certificación oficial del
            MCER.
          </Alert>
          {start.isError && <Alert tone="error">{errorMessage(start.error)}</Alert>}
          <Button onClick={() => start.mutate()} loading={start.isPending}>
            Empezar diagnóstico
          </Button>
        </Card>
      </div>
    );
  }

  if (phase.kind === 'result') {
    return <DiagnosticResult result={phase.result} />;
  }

  const { question, sessionId } = phase;
  const item = question.item!;
  const type = item.type as ExerciseType;
  const prompt = (item.prompt ?? {}) as unknown as Prompt;

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm text-muted">
          Pregunta {(question.answered ?? 0) + 1} · máximo {question.maxQuestions}
        </span>
        <LanguageToggle />
      </div>
      <ProgressBar value={question.answered ?? 0} max={question.maxQuestions ?? 24} label="Avance del diagnóstico" />
      <Card className="flex flex-col gap-5">
        <div className="flex items-center gap-2">
          <Badge tone="primary">{t(skillLabels[item.skill ?? ''])}</Badge>
        </div>
        <p className="font-medium text-muted">{t(item.instructions)}</p>
        <ExerciseInput key={item.publicId} type={type} prompt={prompt} value={answer} onChange={setAnswer} />
        {respond.isError && <Alert tone="error">{errorMessage(respond.error)}</Alert>}
        <Button
          onClick={() => respond.mutate({ sessionId, itemId: item.publicId ?? '', type })}
          disabled={!isComplete(type, prompt, answer)}
          loading={respond.isPending}
        >
          Continuar
        </Button>
      </Card>
    </div>
  );
}

function DiagnosticResult({ result }: { result: Result }) {
  const { lang } = useLang();
  const t = useT();
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-2xl font-bold tracking-tight">Tu nivel estimado</h1>
      <div className="grid gap-3 sm:grid-cols-3">
        {result.skills?.map((s) => (
          <Card key={s.skill} className="flex flex-col items-start gap-1">
            <span className="text-sm text-muted">{t(skillLabels[s.skill ?? ''])}</span>
            <span className="text-3xl font-bold text-primary">{s.level}</span>
            <span className="text-xs text-muted">{confidenceLabels[s.confidence ?? 'LOW']}</span>
            <span className="text-xs text-muted">
              {s.correct} de {s.evidence} correctas
            </span>
          </Card>
        ))}
      </div>
      <p className="flex items-start gap-2 text-sm text-muted">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        {lang === 'en' ? result.disclaimerEn : result.disclaimerEs}
      </p>
      <Card>
        <p>
          Preparamos tu plan a partir del nivel <strong>{result.planStartLevel}</strong> (el más bajo entre vocabulario y
          gramática, que son la base de todo lo demás).
        </p>
        <Link
          to="/app/plan"
          className="mt-4 inline-flex min-h-11 items-center rounded-lg bg-primary px-4 font-semibold text-on-primary hover:bg-primary-hover"
        >
          Ver mi plan
        </Link>
      </Card>
    </div>
  );
}
