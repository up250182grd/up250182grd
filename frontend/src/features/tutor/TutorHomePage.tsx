import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import { MessageSquarePlus, Wallet } from 'lucide-react';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { isAdmin, useMe } from '@/features/auth/useSession';
import { isInsufficientFunds, useWallet } from '@/features/wallet/WalletPage';
import { formatMXN } from '@/lib/money';

type Mode = 'FREE' | 'GUIDED' | 'ROLEPLAY';

const modes: { value: Mode; title: string; text: string; placeholder: string }[] = [
  { value: 'FREE', title: 'Conversación libre', text: 'Habla de lo que quieras; el profesor corrige al final de cada mensaje.', placeholder: 'Ej.: mi fin de semana' },
  { value: 'GUIDED', title: 'Práctica guiada', text: 'Practica un tema gramatical concreto con preguntas dirigidas.', placeholder: 'Ej.: present perfect' },
  { value: 'ROLEPLAY', title: 'Roleplay', text: 'Simula una situación real: restaurante, aeropuerto, entrevista…', placeholder: 'Ej.: pedir comida en un restaurante' },
];

export const modeLabels: Record<string, string> = { FREE: 'Libre', GUIDED: 'Guiada', ROLEPLAY: 'Roleplay' };
const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });

export function TutorHomePage() {
  const navigate = useNavigate();
  const { data: me } = useMe();
  const admin = isAdmin(me);
  const wallet = useWallet();
  const [mode, setMode] = useState<Mode>('FREE');
  const [topic, setTopic] = useState('');
  const conversations = useQuery({
    queryKey: ['tutor', 'list'],
    queryFn: () => unwrap(api.GET('/api/v1/tutor/conversations')),
  });
  const start = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/tutor/conversations', { body: { mode, topic: topic || undefined } })),
    onSuccess: (c) => navigate(`/app/tutor/${c.id}`),
  });

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Profesor IA</h1>
        <p className="mt-1 text-muted">Practica inglés escribiendo. La conversación por voz llegará en la Fase 5.</p>
      </div>

      <Card className="flex flex-col gap-4">
        <fieldset>
          <legend className="mb-2 font-semibold">¿Cómo quieres practicar?</legend>
          <div className="grid gap-3 sm:grid-cols-3">
            {modes.map((m) => (
              <label
                key={m.value}
                className={`flex cursor-pointer flex-col gap-1 rounded-lg border p-3 ${
                  mode === m.value ? 'border-primary bg-primary/10' : 'border-border hover:bg-surface-2'
                }`}
              >
                <span className="flex items-center gap-2 font-medium">
                  <input
                    type="radio"
                    name="mode"
                    value={m.value}
                    checked={mode === m.value}
                    onChange={() => setMode(m.value)}
                    className="accent-[var(--color-primary)]"
                  />
                  {m.title}
                </span>
                <span className="text-sm text-muted">{m.text}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <TextField
          label="Tema o situación (opcional)"
          value={topic}
          maxLength={200}
          onChange={(e) => setTopic(e.target.value)}
          placeholder={modes.find((m) => m.value === mode)?.placeholder}
        />
        <p className="text-sm text-muted">
          {admin
            ? 'Como administrador, tu consumo se registra al costo (sin recargo) como gasto interno.'
            : `Al empezar se apartan ${formatMXN(5)} de tu saldo; al terminar se cobra solo lo consumido y se libera el resto. Saldo disponible: ${formatMXN(wallet.data?.available)}.`}
        </p>
        {start.isError && (
          <Alert tone="error">
            {errorMessage(start.error)}
            {isInsufficientFunds(start.error) && (
              <Link to="/app/wallet" className="ml-1 font-semibold text-primary underline-offset-4 hover:underline">
                Ir a la billetera
              </Link>
            )}
          </Alert>
        )}
        <div>
          <Button loading={start.isPending} onClick={() => start.mutate()} icon={<MessageSquarePlus aria-hidden className="size-4" />}>
            Empezar conversación
          </Button>
        </div>
      </Card>

      <section aria-labelledby="recent">
        <h2 id="recent" className="mb-3 text-lg font-semibold">
          Conversaciones recientes
        </h2>
        {conversations.data?.length === 0 && <p className="text-muted">Todavía no tienes conversaciones.</p>}
        <ul className="flex flex-col gap-2">
          {conversations.data?.map((c) => (
            <li key={c.id}>
              <Link
                to={`/app/tutor/${c.id}`}
                className="flex min-h-14 flex-wrap items-center gap-2 rounded-lg border border-border bg-surface px-4 py-2 hover:bg-surface-2"
              >
                <Badge tone="primary">{modeLabels[c.mode ?? ''] ?? c.mode}</Badge>
                <span className="flex-1 font-medium">{c.topic ?? 'Sin tema'}</span>
                <span className="text-sm text-muted">{c.turns} mensajes</span>
                <Badge tone={c.status === 'OPEN' ? 'success' : 'neutral'}>{c.status === 'OPEN' ? 'Abierta' : 'Cerrada'}</Badge>
                <span className="w-full text-xs text-muted sm:w-auto">
                  {c.startedAt ? dateTime.format(new Date(c.startedAt)) : ''}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <p className="flex items-center gap-2 text-sm text-muted">
        <Wallet aria-hidden className="size-4" />
        <Link to="/app/wallet" className="text-primary underline-offset-4 hover:underline">
          Ver mi billetera y el consumo de cada sesión
        </Link>
      </p>
    </div>
  );
}
