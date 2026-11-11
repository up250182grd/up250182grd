import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Award, BookOpen, Flame, Lock, Sparkles, Star } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Card, Skeleton } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { cn } from '@/lib/cn';

type Achievements = Schemas['GamificationView'];

export function useAchievements() {
  return useQuery({ queryKey: ['achievements'], queryFn: () => unwrap(api.GET('/api/v1/me/achievements')) });
}

const sourceLabels: Record<string, string> = {
  LESSON_RUN: 'Lección terminada',
  LESSON_MASTERED: 'Lección dominada',
  REVIEW: 'Repaso de vocabulario',
  ERROR_RESOLVED: 'Error superado',
  CONVERSATION: 'Conversación con el profesor',
  VOICE_CALL: 'Llamada con el profesor',
  DIAGNOSTIC: 'Diagnóstico completado',
};

const statusStyle: Record<string, { cls: string; label: string }> = {
  QUALIFIED: { cls: 'bg-primary text-on-primary', label: 'Día con estudio' },
  REST: { cls: 'border-2 border-dashed border-primary text-primary', label: 'Día de descanso (no rompe la racha)' },
  MISSED: { cls: 'bg-surface-2 text-muted line-through', label: 'Sin estudio (la racha se reinició)' },
  PENDING: { cls: 'border-2 border-primary text-text', label: 'Hoy (pendiente)' },
  NONE: { cls: 'bg-surface-2 text-muted', label: 'Sin actividad' },
};

const dayFmt = new Intl.DateTimeFormat('es-MX', { day: 'numeric', timeZone: 'UTC' });
const longFmt = new Intl.DateTimeFormat('es-MX', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });
const dateFmt = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' });
const weekdays = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

function parse(d?: string) {
  return new Date(`${d ?? '1970-01-01'}T00:00:00Z`);
}

/** Franja compacta del tablero: racha, XP e insignias. */
export function AchievementsStrip() {
  const a = useAchievements();
  if (a.isPending) return <Skeleton className="h-20" />;
  if (a.isError || !a.data) return null;
  const s = a.data.streak;
  const items = [
    { icon: Flame, label: 'Racha', value: `${s?.current ?? 0} ${s?.current === 1 ? 'día' : 'días'}`, hint: s?.today?.qualified ? 'Hoy ya cuenta' : 'Estudia hoy para sumar' },
    { icon: Star, label: 'XP', value: (a.data.xpTotal ?? 0).toLocaleString('es-MX'), hint: `+${a.data.xpToday ?? 0} hoy` },
    { icon: Award, label: 'Insignias', value: `${a.data.badgesEarned ?? 0} de ${a.data.badges?.length ?? 0}`, hint: 'Ver logros' },
  ];
  return (
    <Link to="/app/achievements" className="grid grid-cols-3 gap-2 rounded-xl border border-border bg-surface p-3 hover:bg-surface-2" aria-label="Ver mis logros" data-testid="achievements-strip">
      {items.map(({ icon: Icon, label, value, hint }) => (
        <span key={label} className="flex min-w-0 flex-col items-center gap-0.5 text-center sm:flex-row sm:gap-3 sm:text-left">
          <Icon aria-hidden className="size-6 shrink-0 text-accent" />
          <span className="min-w-0">
            <span className="block text-xs text-muted">{label}</span>
            <span className="block truncate font-semibold">{value}</span>
            <span className="hidden text-xs text-muted sm:block">{hint}</span>
          </span>
        </span>
      ))}
    </Link>
  );
}

function StreakCalendar({ data }: { data: Achievements }) {
  const days = data.streak?.last28Days ?? [];
  // Rellena al inicio para que la cuadrícula empiece en lunes.
  const first = parse(days[0]?.date);
  const offset = (first.getUTCDay() + 6) % 7;
  return (
    <div>
      <div className="grid grid-cols-7 gap-1.5 text-center text-xs text-muted" aria-hidden>
        {weekdays.map((d, i) => (
          <span key={i}>{d}</span>
        ))}
      </div>
      <ol className="mt-1 grid grid-cols-7 gap-1.5" aria-label="Últimas 4 semanas">
        {Array.from({ length: offset }, (_, i) => (
          <li key={`pad-${i}`} aria-hidden />
        ))}
        {days.map((d) => {
          const st = statusStyle[d.status ?? 'NONE'] ?? statusStyle.NONE!;
          return (
            <li
              key={d.date}
              className={cn('flex aspect-square items-center justify-center rounded-md text-xs font-semibold', st.cls)}
              title={`${longFmt.format(parse(d.date))}: ${st.label}`}
            >
              <span aria-hidden>{dayFmt.format(parse(d.date))}</span>
              <span className="sr-only">
                {longFmt.format(parse(d.date))}: {st.label}
              </span>
            </li>
          );
        })}
      </ol>
      <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
        {(['QUALIFIED', 'REST', 'PENDING'] as const).map((k) => (
          <li key={k} className="inline-flex items-center gap-1.5">
            <span aria-hidden className={cn('inline-block size-3 rounded-sm', statusStyle[k]!.cls)} />
            {statusStyle[k]!.label}
          </li>
        ))}
      </ul>
    </div>
  );
}

function TodayCard({ data }: { data: Achievements }) {
  const t = data.streak?.today;
  if (!t) return null;
  const goals = [
    { label: 'Minutos de estudio', value: t.minutes ?? 0, goal: t.minMinutes ?? 5 },
    { label: 'Lecciones terminadas', value: t.lessons ?? 0, goal: t.minLessons ?? 1 },
    { label: 'Repasos de vocabulario', value: t.reviews ?? 0, goal: t.minReviews ?? 10 },
  ];
  return (
    <Card>
      <h2 className="font-semibold">{t.qualified ? '¡Hoy ya cuenta para tu racha!' : 'Para que hoy cuente, cumple una:'}</h2>
      <ul className="mt-3 flex flex-col gap-3">
        {goals.map((g) => {
          const done = g.value >= g.goal;
          return (
            <li key={g.label} className="text-sm">
              <div className="flex justify-between">
                <span>{g.label}</span>
                <span className="font-mono">
                  {Math.min(g.value, g.goal)} / {g.goal}
                </span>
              </div>
              <div
                className="mt-1 h-2 overflow-hidden rounded-full bg-primary/15"
                role="progressbar"
                aria-label={g.label}
                aria-valuemin={0}
                aria-valuemax={g.goal}
                aria-valuenow={Math.min(g.value, g.goal)}
              >
                <div className={cn('h-full rounded-full', done ? 'bg-success' : 'bg-primary')} style={{ width: `${Math.min(100, (g.value / g.goal) * 100)}%` }} />
              </div>
            </li>
          );
        })}
      </ul>
      <p className="mt-3 text-xs text-muted">
        Tienes 1 día de descanso por semana sin perder la racha
        {data.streak?.restUsedThisWeek ? ' (ya lo usaste esta semana).' : '.'} Equivocarte nunca te quita puntos.
      </p>
    </Card>
  );
}

export function AchievementsPage() {
  const a = useAchievements();
  if (a.isPending) return <Skeleton className="mx-auto h-96 max-w-4xl" />;
  if (a.isError) return <Alert tone="error">{errorMessage(a.error)}</Alert>;
  const data = a.data;
  const s = data.streak;
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Logros</h1>
        <p className="mt-1 text-muted">
          Ganas XP al terminar actividades, no por clics. El XP no cambia tu nivel de inglés: eso lo mide tu progreso real.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card className="flex items-center gap-3">
          <Flame aria-hidden className="size-8 text-accent" />
          <div>
            <p className="text-sm text-muted">Racha actual</p>
            <p className="text-2xl font-bold" data-testid="streak-current">
              {s?.current ?? 0} {s?.current === 1 ? 'día' : 'días'}
            </p>
            <p className="text-xs text-muted">Mejor: {s?.best ?? 0}</p>
          </div>
        </Card>
        <Card className="flex items-center gap-3">
          <Star aria-hidden className="size-8 text-accent" />
          <div>
            <p className="text-sm text-muted">XP total</p>
            <p className="text-2xl font-bold" data-testid="xp-total">
              {(data.xpTotal ?? 0).toLocaleString('es-MX')}
            </p>
            <p className="text-xs text-muted">+{data.xpToday ?? 0} hoy</p>
          </div>
        </Card>
        <Card className="flex items-center gap-3">
          <Award aria-hidden className="size-8 text-accent" />
          <div>
            <p className="text-sm text-muted">Insignias</p>
            <p className="text-2xl font-bold">
              {data.badgesEarned ?? 0} de {data.badges?.length ?? 0}
            </p>
          </div>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <h2 className="mb-3 font-semibold">Tus últimas 4 semanas</h2>
          <StreakCalendar data={data} />
        </Card>
        <TodayCard data={data} />
      </div>

      <section aria-labelledby="badges-title">
        <h2 id="badges-title" className="mb-3 text-lg font-semibold">
          Insignias
        </h2>
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {data.badges?.map((b) => (
            <li key={b.code}>
              <Card className={cn('flex h-full gap-3 p-4', !b.earned && 'border-dashed')} data-testid={`badge-${b.code}`}>
                {b.earned ? (
                  <Award aria-hidden className="size-8 shrink-0 text-accent" />
                ) : (
                  <Lock aria-hidden className="size-8 shrink-0 text-muted" />
                )}
                <div className="min-w-0 flex-1">
                  <h3 className="font-semibold">
                    {b.title}
                    <span className="sr-only">{b.earned ? ' (obtenida)' : ' (bloqueada)'}</span>
                  </h3>
                  <p className="text-sm text-muted">{b.description}</p>
                  {b.earned ? (
                    <p className="mt-1 text-xs text-success">Obtenida {b.awardedAt ? dateFmt.format(new Date(b.awardedAt)) : ''}</p>
                  ) : (
                    (b.goal ?? 1) > 1 && (
                      <p className="mt-1 text-xs text-muted">
                        Progreso: {b.progress} / {b.goal}
                      </p>
                    )
                  )}
                </div>
              </Card>
            </li>
          ))}
        </ul>
      </section>

      <Card>
        <h2 className="mb-3 font-semibold">XP reciente</h2>
        {(data.recentXp?.length ?? 0) === 0 ? (
          <p className="flex items-center gap-2 text-sm text-muted">
            <Sparkles aria-hidden className="size-4" /> Termina una lección o un repaso para ganar tus primeros XP.
            <Link to="/app/practice" className="font-semibold text-primary hover:underline">
              <BookOpen aria-hidden className="mr-1 inline size-4" />
              Ir al repaso
            </Link>
          </p>
        ) : (
          <ul className="text-sm">
            {data.recentXp?.map((x, i) => (
              <li key={i} className="flex justify-between border-t border-border py-2 first:border-0">
                <span>{sourceLabels[x.source ?? ''] ?? x.source}</span>
                <span className="font-mono font-semibold text-accent">+{x.xp} XP</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
