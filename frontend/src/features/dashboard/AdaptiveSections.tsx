import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { BookOpen, ChevronRight, ClipboardCheck, GraduationCap, ListChecks, MessagesSquare, Target, type LucideIcon } from 'lucide-react';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Card, Skeleton } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { errorCategoryLabels } from '@/features/practice/api';
import { ActivityChart, CategoryBars } from './ActivityChart';

const icons: Record<string, LucideIcon> = {
  DIAGNOSTIC: ClipboardCheck,
  REVIEW_CARDS: BookOpen,
  PRACTICE_ERRORS: ListChecks,
  NEXT_LESSON: GraduationCap,
  WEAK_SKILL: Target,
  CONVERSATION: MessagesSquare,
};

export function useRecommendations() {
  return useQuery({ queryKey: ['recommendations'], queryFn: () => unwrap(api.GET('/api/v1/me/recommendations')) });
}

export function useProgress() {
  return useQuery({ queryKey: ['progress'], queryFn: () => unwrap(api.GET('/api/v1/me/progress')) });
}

/** "Para hoy": sugerencias del motor de reglas, de la más a la menos importante. */
export function RecommendationsCard() {
  const rec = useRecommendations();
  if (rec.isPending) return <Skeleton className="h-40" />;
  if (rec.isError) return <Alert tone="error">{errorMessage(rec.error)}</Alert>;
  const { items = [], minutesToday = 0, dailyGoalMinutes = 0 } = rec.data;
  const pct = dailyGoalMinutes > 0 ? Math.min(100, Math.round((minutesToday / dailyGoalMinutes) * 100)) : 0;
  return (
    <Card className="flex flex-col gap-4" aria-labelledby="today-title">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 id="today-title" className="text-lg font-semibold">
          Para hoy
        </h2>
        <div className="flex min-w-48 flex-col gap-1 text-sm">
          <span className="text-muted">
            Meta diaria: <strong className="font-mono text-text">{minutesToday}</strong> de {dailyGoalMinutes} min (estimado)
          </span>
          <div
            className="h-2 overflow-hidden rounded-full bg-primary/15"
            role="meter"
            aria-label="Avance de la meta diaria"
            aria-valuemin={0}
            aria-valuemax={dailyGoalMinutes}
            aria-valuenow={minutesToday}
          >
            <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
          </div>
        </div>
      </div>
      {items.length === 0 ? (
        <p className="text-sm text-muted">¡Todo al día! No hay nada pendiente por ahora.</p>
      ) : (
        <ol className="flex flex-col gap-2" data-testid="recommendations">
          {items.map((r, i) => {
            const Icon = icons[r.type ?? ''] ?? Target;
            return (
              <li key={r.type}>
                <Link
                  to={r.actionPath ?? '/app'}
                  className={`flex min-h-16 items-center gap-3 rounded-lg border px-4 py-3 hover:bg-surface-2 ${
                    i === 0 ? 'border-primary/50 bg-primary/5' : 'border-border'
                  }`}
                >
                  <Icon aria-hidden className="size-5 shrink-0 text-primary" />
                  <span className="flex-1">
                    <span className="block font-semibold">{r.title}</span>
                    <span className="block text-sm text-muted">{r.reason}</span>
                  </span>
                  <span className="hidden text-sm text-muted sm:block">~{r.estimatedMinutes} min</span>
                  <ChevronRight aria-hidden className="size-4 text-muted" />
                </Link>
              </li>
            );
          })}
        </ol>
      )}
    </Card>
  );
}

function Stat({ label, value, hint }: { label: string; value: number | string; hint?: string }) {
  return (
    <div className="rounded-lg bg-surface-2 p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="text-2xl font-semibold">{value}</p>
      {hint && <p className="text-xs text-muted">{hint}</p>}
    </div>
  );
}

/** Progreso con datos reales: actividad de 14 días, vocabulario y errores abiertos. */
export function ProgressSection() {
  const progress = useProgress();
  if (progress.isPending) return <Skeleton className="h-64" />;
  if (progress.isError) return <Alert tone="error">{errorMessage(progress.error)}</Alert>;
  const p = progress.data;
  const v = p.vocabulary;
  const errors = p.openErrorsByCategory ?? {};
  return (
    <section aria-labelledby="progress-title" className="flex flex-col gap-4">
      <h2 id="progress-title" className="text-lg font-semibold">
        Tu progreso
      </h2>
      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h3 className="font-semibold">Minutos de estudio por día</h3>
          <p className="mb-2 text-sm text-muted">
            Últimos 14 días · {p.activeDaysLast14} {p.activeDaysLast14 === 1 ? 'día activo' : 'días activos'} · estimado a
            partir de tus ejercicios, repasos y mensajes
          </p>
          <ActivityChart days={p.activity ?? []} goal={p.dailyGoalMinutes ?? 0} />
        </Card>
        <div className="flex flex-col gap-4">
          <Card>
            <h3 className="mb-3 flex items-center justify-between font-semibold">
              Vocabulario
              <Link to="/app/vocabulary" className="text-sm font-normal text-primary hover:underline">
                Ver palabras
              </Link>
            </h3>
            <div className="grid grid-cols-2 gap-2" data-testid="vocabulary-stats">
              <Stat label="Palabras" value={v?.total ?? 0} />
              <Stat label="Para repasar hoy" value={(v?.dueNow ?? 0) + (v?.newAvailableToday ?? 0)} />
              <Stat label="Aprendiendo" value={(v?.learning ?? 0) + (v?.relearning ?? 0)} />
              <Stat label="Dominadas" value={v?.mature ?? 0} hint="intervalo ≥ 21 días" />
            </div>
          </Card>
          <Card>
            <h3 className="mb-3 flex items-center justify-between font-semibold">
              Errores por superar
              {Object.keys(errors).length > 0 && (
                <Link to="/app/practice?tab=errors" className="text-sm font-normal text-primary hover:underline">
                  Practicar
                </Link>
              )}
            </h3>
            {Object.keys(errors).length === 0 ? (
              <p className="text-sm text-muted">Sin errores pendientes. Los que te corrija el profesor aparecerán aquí.</p>
            ) : (
              <CategoryBars data={errors} labels={errorCategoryLabels} />
            )}
          </Card>
        </div>
      </div>
    </section>
  );
}
