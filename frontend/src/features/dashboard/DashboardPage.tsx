import { Link } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { BookOpen, ChevronRight, ClipboardCheck, MessagesSquare, Wallet, type LucideIcon } from 'lucide-react';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge, ProgressBadge, ProgressBar } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { useMe } from '@/features/auth/useSession';
import { confidenceLabels, skillLabels, useLearningSummary, useSkillLevels } from '@/features/learning/api';
import { useT } from '@/lib/lang';

function greeting(date = new Date()): string {
  const h = date.getHours();
  if (h < 12) return 'Buenos días';
  if (h < 19) return 'Buenas tardes';
  return 'Buenas noches';
}

/** Lo que aún no existe se presenta como "próximamente", sin datos inventados. */
const upcoming: { icon: LucideIcon; title: string; text: string; phase: string }[] = [
  { icon: MessagesSquare, title: 'Profesor IA', text: 'Conversación por texto y, después, por voz en tiempo real.', phase: 'Fases 4–5' },
  { icon: Wallet, title: 'Billetera', text: 'Saldo en MXN y detalle del consumo de cada sesión.', phase: 'Fase 4' },
  { icon: BookOpen, title: 'Repaso espaciado', text: 'Tarjetas de vocabulario y repasos en el momento justo.', phase: 'Fase 6' },
];

function VerifyEmailBanner({ email }: { email: string }) {
  const resend = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/auth/resend-verification', { body: { email } })),
  });
  return (
    <Alert tone="info" title="Confirma tu correo">
      <p>Te enviamos un enlace a {email}. Lo necesitarás para usar las funciones con IA.</p>
      <div className="mt-2">
        {resend.isSuccess ? (
          <span className="font-medium text-success">Enlace reenviado.</span>
        ) : (
          <Button variant="secondary" loading={resend.isPending} onClick={() => resend.mutate()}>
            Reenviar enlace
          </Button>
        )}
        {resend.isError && <p className="mt-1 text-danger">{errorMessage(resend.error)}</p>}
      </div>
    </Alert>
  );
}

function SkillsCard() {
  const skills = useSkillLevels();
  const t = useT();
  if (skills.isPending) return <Skeleton className="h-36" />;
  if (!skills.data || skills.data.length === 0) {
    return (
      <Card className="flex flex-col gap-3 border-dashed">
        <h2 className="font-semibold">Tu nivel estimado</h2>
        <p className="text-sm text-muted">
          Aún no tienes un diagnóstico. En unos 15 minutos estimamos tu nivel por habilidad y armamos tu plan (no es una
          certificación oficial del MCER).
        </p>
        <Link
          to="/app/diagnostic"
          className="inline-flex min-h-11 w-fit items-center gap-2 rounded-lg bg-primary px-4 font-semibold text-on-primary hover:bg-primary-hover"
        >
          <ClipboardCheck aria-hidden className="size-4" /> Hacer el diagnóstico
        </Link>
      </Card>
    );
  }
  return (
    <Card>
      <div className="flex items-center justify-between">
        <h2 className="font-semibold">Tu nivel estimado</h2>
        <Link to="/app/diagnostic" className="text-sm text-primary underline-offset-4 hover:underline">
          Repetir diagnóstico
        </Link>
      </div>
      <ul className="mt-3 grid gap-3 sm:grid-cols-3">
        {skills.data.map((s) => (
          <li key={s.skill} className="rounded-lg bg-surface-2 p-3">
            <span className="block text-sm text-muted">{t(skillLabels[s.skill ?? ''])}</span>
            <span className="text-2xl font-bold text-primary">{s.level}</span>
            <span className="block text-xs text-muted">{confidenceLabels[s.confidence ?? 'LOW']}</span>
          </li>
        ))}
      </ul>
      <p className="mt-3 text-xs text-muted">Estimación orientativa, no certificación oficial del MCER.</p>
    </Card>
  );
}

function NextLessonCard() {
  const summary = useLearningSummary();
  const t = useT();
  if (summary.isPending) return <Skeleton className="h-36" />;
  if (summary.isError) return <Alert tone="error">{errorMessage(summary.error)}</Alert>;
  const s = summary.data;
  if (!s.recommended) {
    return (
      <Card>
        <h2 className="font-semibold">¡Completaste todas las lecciones disponibles!</h2>
        <p className="mt-1 text-sm text-muted">Pronto habrá más contenido. Mientras, repasa tus lecciones.</p>
      </Card>
    );
  }
  return (
    <Card className="flex flex-col gap-4">
      <div>
        <p className="text-sm text-muted">Siguiente lección recomendada</p>
        <h2 className="mt-1 text-xl font-semibold">{t(s.recommended.title)}</h2>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted">
          <Badge tone="accent">{s.recommended.level}</Badge>
          <span>{t(s.recommended.courseTitle)}</span>
          <span>· {s.recommended.estimatedMinutes} min</span>
          <ProgressBadge state={s.recommended.state} />
        </div>
      </div>
      <Link
        to={`/app/lessons/${s.recommended.publicId}`}
        className="inline-flex min-h-11 w-fit items-center gap-2 rounded-lg bg-primary px-4 font-semibold text-on-primary hover:bg-primary-hover"
      >
        {s.recommended.state === 'NOT_STARTED' ? 'Empezar lección' : 'Continuar lección'}
        <ChevronRight aria-hidden className="size-4" />
      </Link>
      {(s.totalLessons ?? 0) > 0 && (
        <div className="flex flex-col gap-1 text-sm">
          <span className="text-muted">
            {s.mastered} de {s.totalLessons} lecciones dominadas en tu plan
          </span>
          <ProgressBar value={s.mastered ?? 0} max={s.totalLessons ?? 0} label="Lecciones dominadas del plan" />
        </div>
      )}
    </Card>
  );
}

export function DashboardPage() {
  const { data: me } = useMe();
  if (!me) return null;
  const name = me.profile?.displayName ?? '';

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">
          {greeting()}, {name} 👋
        </h1>
        <p className="mt-1 text-muted">Tu progreso y tu siguiente paso.</p>
      </div>

      {!me.emailVerified && me.email && <VerifyEmailBanner email={me.email} />}

      <div className="grid gap-4 lg:grid-cols-2">
        <NextLessonCard />
        <SkillsCard />
      </div>

      <section aria-labelledby="upcoming">
        <h2 id="upcoming" className="mb-3 text-lg font-semibold">
          Próximamente
        </h2>
        <ul className="grid gap-4 sm:grid-cols-3">
          {upcoming.map(({ icon: Icon, title, text, phase }) => (
            <li key={title}>
              <Card className="flex h-full gap-3">
                <Icon aria-hidden className="size-5 shrink-0 text-primary" />
                <div>
                  <h3 className="flex flex-wrap items-center gap-2 font-semibold">
                    {title}
                    <Badge>{phase}</Badge>
                  </h3>
                  <p className="mt-1 text-sm text-muted">{text}</p>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
