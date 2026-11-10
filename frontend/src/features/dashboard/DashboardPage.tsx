import { useMutation } from '@tanstack/react-query';
import { BookOpen, GraduationCap, MessagesSquare, Wallet, type LucideIcon } from 'lucide-react';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { useMe } from '@/features/auth/useSession';

function greeting(date = new Date()): string {
  const h = date.getHours();
  if (h < 12) return 'Buenos días';
  if (h < 19) return 'Buenas tardes';
  return 'Buenas noches';
}

interface Upcoming {
  icon: LucideIcon;
  title: string;
  text: string;
  phase: string;
}

/** Las tarjetas describen lo que vendrá; no muestran datos inventados (requisito: diferenciar lo pendiente). */
const upcoming: Upcoming[] = [
  {
    icon: GraduationCap,
    title: 'Diagnóstico y plan',
    text: 'Una evaluación adaptativa estimará tu nivel por habilidad y creará tu plan.',
    phase: 'Fase 3',
  },
  {
    icon: BookOpen,
    title: 'Lecciones y ejercicios',
    text: 'Clases por nivel con ejercicios corregidos al instante.',
    phase: 'Fase 3',
  },
  {
    icon: MessagesSquare,
    title: 'Profesor IA',
    text: 'Conversación por texto y, después, por voz en tiempo real.',
    phase: 'Fases 4–5',
  },
  {
    icon: Wallet,
    title: 'Billetera',
    text: 'Saldo en MXN y detalle del consumo de cada sesión.',
    phase: 'Fase 4',
  },
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
        <p className="mt-1 text-muted">Este es tu panel. Aquí verás tu nivel, tu plan y tu progreso.</p>
      </div>

      {!me.emailVerified && me.email && <VerifyEmailBanner email={me.email} />}

      <Card className="flex flex-col gap-2 border-dashed">
        <h2 className="font-semibold">Tu nivel estimado</h2>
        <p className="text-sm text-muted">
          Aún no tienes un diagnóstico. Cuando esté disponible, verás aquí tu nivel estimado por habilidad (no es una
          certificación oficial del MCER).
        </p>
      </Card>

      <section aria-labelledby="upcoming">
        <h2 id="upcoming" className="mb-3 text-lg font-semibold">
          Próximamente en EnglishOS
        </h2>
        <ul className="grid gap-4 sm:grid-cols-2">
          {upcoming.map(({ icon: Icon, title, text, phase }) => (
            <li key={title}>
              <Card className="flex h-full gap-4">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                  <Icon aria-hidden className="size-5 text-primary" />
                </span>
                <div>
                  <h3 className="flex flex-wrap items-center gap-2 font-semibold">
                    {title}
                    <span className="rounded bg-surface-2 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-muted">
                      {phase}
                    </span>
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
