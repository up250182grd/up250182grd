import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2, Clock, CreditCard, XCircle } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { formatMXN } from '@/lib/money';

type Topup = Schemas['TopupView'];

export function usePaymentsConfig() {
  return useQuery({ queryKey: ['payments', 'config'], queryFn: () => unwrap(api.GET('/api/v1/payments/config')) });
}

export const topupStatus: Record<string, { label: string; tone: 'neutral' | 'primary' | 'success' | 'warning' | 'danger' }> = {
  CREATED: { label: 'Sin pagar', tone: 'neutral' },
  PENDING: { label: 'Pago en proceso', tone: 'warning' },
  APPROVED: { label: 'Acreditada', tone: 'success' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  CANCELLED: { label: 'Cancelada', tone: 'neutral' },
  REVIEW: { label: 'En revisión', tone: 'warning' },
  REFUNDED: { label: 'Devuelta', tone: 'neutral' },
  PARTIALLY_REFUNDED: { label: 'Devuelta en parte', tone: 'primary' },
};

const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });

function newKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** Recarga con Mercado Pago: el servidor crea el cobro y redirige a la pasarela. Nunca se pide la tarjeta aquí. */
export function GatewayTopupCard({ min, max, testMode }: { min: number; max: number; testMode: boolean }) {
  const [amount, setAmount] = useState('200');
  const [key, setKey] = useState(newKey);
  const create = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/api/v1/me/wallet/topups', { body: { amount: Number(amount), idempotencyKey: key } })),
    onSuccess: (t) => {
      if (t.checkoutUrl) window.location.assign(t.checkoutUrl);
    },
    onError: () => setKey(newKey()),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    create.mutate();
  };
  return (
    <Card>
      <form onSubmit={submit} className="flex flex-col gap-3">
        <h2 className="flex flex-wrap items-center gap-2 font-semibold">
          <CreditCard aria-hidden className="size-5 text-primary" /> Recargar saldo
          {testMode && <Badge tone="warning">Modo prueba: sin cobros reales</Badge>}
        </h2>
        <TextField
          label="Monto (MXN)"
          type="number"
          inputMode="decimal"
          min={min}
          max={max}
          step="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          hint={`De ${formatMXN(min)} a ${formatMXN(max)}.`}
        />
        <p className="text-sm text-muted">
          Te llevaremos a Mercado Pago para pagar con tarjeta, OXXO u otros medios. Tu saldo se acredita cuando Mercado
          Pago nos confirma el pago, no al volver a esta página.
        </p>
        {create.isError && <Alert tone="error">{errorMessage(create.error)}</Alert>}
        <div>
          <Button type="submit" loading={create.isPending}>
            Pagar con Mercado Pago
          </Button>
        </div>
      </form>
    </Card>
  );
}

export function MyTopups() {
  const topups = useQuery({ queryKey: ['payments', 'mine'], queryFn: () => unwrap(api.GET('/api/v1/me/wallet/topups')) });
  if (!topups.data || topups.data.length === 0) return null;
  return (
    <Card>
      <h2 className="mb-3 font-semibold">Mis recargas</h2>
      <ul>
        {topups.data.map((t) => {
          const st = topupStatus[t.status ?? ''] ?? { label: t.status ?? '', tone: 'neutral' as const };
          return (
            <li key={t.id} className="flex flex-wrap items-center gap-2 border-t border-border py-2 text-sm first:border-0">
              <Link to={`/app/wallet/topups/${t.id}`} className="font-mono font-semibold hover:underline">
                {formatMXN(t.amountMxn)}
              </Link>
              <Badge tone={st.tone}>{st.label}</Badge>
              <span className="flex-1 text-xs text-muted">{t.createdAt ? dateTime.format(new Date(t.createdAt)) : ''}</span>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

function StatusIcon({ t }: { t: Topup }) {
  if (t.credited) return <CheckCircle2 aria-hidden className="size-10 text-success" />;
  if (t.status === 'REJECTED' || t.status === 'CANCELLED') return <XCircle aria-hidden className="size-10 text-danger" />;
  return <Clock aria-hidden className="size-10 text-warning" />;
}

/**
 * Regreso desde la pasarela. Solo CONSULTA el estado (cada 3 s mientras esté abierto): el crédito lo hace el servidor
 * al recibir la notificación verificada, así que cerrar esta página no afecta el pago.
 */
export function TopupReturnPage() {
  const { topupId = '' } = useParams();
  const topup = useQuery({
    queryKey: ['payments', 'topup', topupId],
    queryFn: () => unwrap(api.GET('/api/v1/me/wallet/topups/{id}', { params: { path: { id: topupId } } })),
    refetchInterval: (q) =>
      q.state.data && ['CREATED', 'PENDING'].includes(q.state.data.status ?? '') && q.state.dataUpdateCount < 60 ? 3000 : false,
  });
  const t = topup.data;
  return (
    <div className="mx-auto flex max-w-xl flex-col gap-6">
      <Link to="/app/wallet" className="inline-flex w-fit items-center gap-1 text-sm text-primary hover:underline">
        <ArrowLeft aria-hidden className="size-4" /> Volver a la billetera
      </Link>
      {topup.isPending && <Skeleton className="h-48" />}
      {topup.isError && <Alert tone="error">{errorMessage(topup.error)}</Alert>}
      {t && (
        <Card className="flex flex-col items-center gap-3 py-8 text-center" role="status" aria-live="polite">
          <StatusIcon t={t} />
          <h1 className="text-xl font-semibold">
            {t.credited
              ? `¡Listo! Acreditamos ${formatMXN(t.amountMxn)} a tu saldo.`
              : t.status === 'REJECTED'
                ? 'El pago fue rechazado.'
                : t.status === 'CANCELLED'
                  ? 'El pago se canceló.'
                  : t.status === 'REVIEW'
                    ? 'Estamos revisando tu pago.'
                    : 'Esperando la confirmación de Mercado Pago…'}
          </h1>
          <p className="max-w-md text-sm text-muted">
            {t.credited
              ? 'Ya puedes usar tu saldo con el profesor IA.'
              : t.status === 'REJECTED' || t.status === 'CANCELLED'
                ? 'No se hizo ningún cargo a tu saldo. Puedes intentarlo de nuevo con otro medio de pago.'
                : t.status === 'REVIEW'
                  ? 'El monto recibido no coincide con la recarga. Nuestro equipo lo revisará; no tienes que hacer nada.'
                  : 'Los pagos con tarjeta suelen confirmarse en segundos; en efectivo (OXXO) puede tardar hasta que pagues. Puedes cerrar esta página.'}
          </p>
          <Badge tone={(topupStatus[t.status ?? ''] ?? topupStatus.CREATED!).tone}>
            {(topupStatus[t.status ?? ''] ?? topupStatus.CREATED!).label}
          </Badge>
        </Card>
      )}
    </div>
  );
}
