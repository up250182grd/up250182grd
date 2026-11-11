import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, FlaskConical, Plus } from 'lucide-react';
import { api, ApiError, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { formatMXN } from '@/lib/money';
import { GatewayTopupCard, MyTopups, usePaymentsConfig } from './payments';

export const walletKeys = {
  summary: ['wallet', 'summary'] as const,
  movements: ['wallet', 'movements'] as const,
  usage: ['wallet', 'usage'] as const,
};

export function useWallet() {
  return useQuery({ queryKey: walletKeys.summary, queryFn: () => unwrap(api.GET('/api/v1/me/wallet')) });
}

const movementLabels: Record<string, string> = {
  TOPUP: 'Recarga',
  RESERVE: 'Reserva',
  CAPTURE: 'Cargo',
  RELEASE: 'Liberación',
  REFUND: 'Devolución',
  ADJUSTMENT: 'Ajuste',
};
const subjectLabels: Record<string, string> = {
  TEXT_CONVERSATION: 'Conversación con el profesor IA',
  VOICE_SESSION: 'Llamada con el profesor IA',
};
const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });

function TopupCard({ min, max }: { min: number; max: number }) {
  const qc = useQueryClient();
  const [amount, setAmount] = useState(String(Math.max(min, 200)));
  const topup = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/api/v1/me/wallet/simulated-topups', {
          body: { amount: Number(amount), idempotencyKey: crypto.randomUUID() },
        }),
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['wallet'] }),
  });
  return (
    <Card className="flex flex-col gap-3 border-dashed">
      <h2 className="flex items-center gap-2 font-semibold">
        <FlaskConical aria-hidden className="size-5 text-accent" /> Recarga simulada
        <Badge tone="warning">Modo prueba</Badge>
      </h2>
      <p className="text-sm text-muted">
        Acredita saldo de prueba <strong>sin cobro real</strong>. Las recargas con pago real (Mercado Pago) llegarán en la
        Fase 7.
      </p>
      <form
        className="flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          topup.mutate();
        }}
      >
        <div className="w-40">
          <TextField
            label="Monto (MXN)"
            type="number"
            inputMode="decimal"
            min={min}
            max={max}
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            hint={`De ${formatMXN(min)} a ${formatMXN(max)}`}
          />
        </div>
        <Button type="submit" loading={topup.isPending} icon={<Plus aria-hidden className="size-4" />}>
          Recargar
        </Button>
      </form>
      {topup.isError && <Alert tone="error">{errorMessage(topup.error)}</Alert>}
      {topup.isSuccess && <Alert tone="success">Saldo de prueba acreditado.</Alert>}
    </Card>
  );
}

function LimitsCard({ monthlyLimit, lowBalance }: { monthlyLimit?: number; lowBalance?: number }) {
  const qc = useQueryClient();
  const [limit, setLimit] = useState(monthlyLimit == null ? '' : String(monthlyLimit));
  const [threshold, setThreshold] = useState(String(lowBalance ?? 30));
  const save = useMutation({
    mutationFn: () =>
      unwrap(
        api.PUT('/api/v1/me/wallet/preferences', {
          body: { monthlyLimit: limit === '' ? undefined : Number(limit), lowBalanceThreshold: Number(threshold) },
        }),
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: walletKeys.summary }),
  });
  return (
    <Card>
      <h2 className="font-semibold">Límites de gasto</h2>
      <form
        className="mt-3 flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <TextField
          label="Límite mensual (MXN)"
          type="number"
          inputMode="decimal"
          min={0}
          step="0.01"
          value={limit}
          onChange={(e) => setLimit(e.target.value)}
          hint="Déjalo vacío para no tener límite. Se revisa antes de cada sesión con IA."
        />
        <TextField
          label="Avisarme si mi saldo baja de (MXN)"
          type="number"
          inputMode="decimal"
          min={0}
          step="0.01"
          value={threshold}
          onChange={(e) => setThreshold(e.target.value)}
        />
        {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        {save.isSuccess && <Alert tone="success">Límites guardados.</Alert>}
        <div>
          <Button type="submit" variant="secondary" loading={save.isPending}>
            Guardar límites
          </Button>
        </div>
      </form>
    </Card>
  );
}

export function WalletPage() {
  const wallet = useWallet();
  const movements = useQuery({
    queryKey: walletKeys.movements,
    queryFn: () => unwrap(api.GET('/api/v1/me/wallet/movements', { params: { query: { size: 30 } } })),
  });
  const usage = useQuery({ queryKey: walletKeys.usage, queryFn: () => unwrap(api.GET('/api/v1/me/usage')) });
  const payments = usePaymentsConfig();

  if (wallet.isPending) return <Skeleton className="mx-auto h-64 max-w-4xl" />;
  if (wallet.isError) return <Alert tone="error">{errorMessage(wallet.error)}</Alert>;
  const w = wallet.data;

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Billetera</h1>
        <p className="mt-1 text-muted">
          Pagas el costo real de la IA que usas más un 30 % de servicio. Cada cargo muestra su desglose.
        </p>
      </div>

      {w.lowBalance && (
        <Alert tone="info" title="Saldo bajo">
          <span className="inline-flex items-center gap-1">
            <AlertTriangle aria-hidden className="size-4" /> Tu saldo está por debajo de {formatMXN(w.lowBalanceThreshold)}.
          </span>
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <p className="text-sm text-muted">Disponible</p>
          <p className="mt-1 font-mono text-2xl font-bold" data-testid="wallet-available">
            {formatMXN(w.available)}
          </p>
        </Card>
        <Card>
          <p className="text-sm text-muted">Reservado</p>
          <p className="mt-1 font-mono text-2xl font-bold">{formatMXN(w.reserved)}</p>
          <p className="text-xs text-muted">Apartado para sesiones abiertas; lo no usado se libera.</p>
        </Card>
        <Card>
          <p className="text-sm text-muted">Gastado este mes</p>
          <p className="mt-1 font-mono text-2xl font-bold">{formatMXN(w.spentThisMonth)}</p>
          {w.monthlyLimit != null && <p className="text-xs text-muted">Límite: {formatMXN(w.monthlyLimit)}</p>}
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {payments.data?.enabled && (
          <GatewayTopupCard min={w.minTopup ?? 100} max={w.maxTopup ?? 2000} testMode={payments.data.testMode ?? true} />
        )}
        {w.simulatedTopupsEnabled && <TopupCard min={w.minTopup ?? 100} max={w.maxTopup ?? 2000} />}
        {payments.data && !payments.data.enabled && !w.simulatedTopupsEnabled && (
          <Card className="border-dashed text-sm text-muted">Las recargas no están disponibles en este entorno.</Card>
        )}
        <LimitsCard monthlyLimit={w.monthlyLimit} lowBalance={w.lowBalanceThreshold} />
      </div>

      <MyTopups />

      <Card className="overflow-x-auto">
        <h2 className="mb-3 font-semibold">Consumo por sesión</h2>
        {usage.isError && <Alert tone="error">{errorMessage(usage.error)}</Alert>}
        {usage.data?.length === 0 && <p className="text-sm text-muted">Aún no has usado funciones con IA.</p>}
        {(usage.data?.length ?? 0) > 0 && (
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="py-2 pr-3">Sesión</th>
                <th scope="col" className="py-2 pr-3 text-right">Costo IA</th>
                <th scope="col" className="py-2 pr-3 text-right">Servicio</th>
                <th scope="col" className="py-2 pr-3 text-right">Impuestos</th>
                <th scope="col" className="py-2 text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {usage.data?.map((u) => (
                <tr key={u.settlementId} className="border-t border-border">
                  <td className="py-2 pr-3">
                    {subjectLabels[u.subjectType ?? ''] ?? u.subjectType}
                    <span className="block text-xs text-muted">
                      {u.settledAt ? dateTime.format(new Date(u.settledAt)) : ''}
                      {u.detail?.status === 'PENDING_RECONCILIATION' && ' · pendiente de conciliación'}
                      {u.detail?.internal && ' · consumo interno al costo'}
                    </span>
                  </td>
                  <td className="py-2 pr-3 text-right font-mono">{formatMXN(u.detail?.directCostMxn)}</td>
                  <td className="py-2 pr-3 text-right font-mono">{formatMXN(u.detail?.markupMxn)}</td>
                  <td className="py-2 pr-3 text-right font-mono">{formatMXN(u.detail?.taxMxn)}</td>
                  <td className="py-2 text-right font-mono font-semibold">{formatMXN(u.detail?.totalMxn)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card className="overflow-x-auto">
        <h2 className="mb-3 font-semibold">Movimientos</h2>
        {movements.isError && <Alert tone="error">{errorMessage(movements.error)}</Alert>}
        {movements.data?.length === 0 && <p className="text-sm text-muted">Sin movimientos todavía.</p>}
        <ul>
          {movements.data?.map((m) => (
            <li key={m.id} className="flex flex-wrap items-center gap-2 border-t border-border py-2 text-sm first:border-0">
              <Badge>{movementLabels[m.type ?? ''] ?? m.type}</Badge>
              <span className="flex-1">{m.description}</span>
              <span className="font-mono">
                {[
                  { label: 'Disponible', value: m.availableChange ?? 0 },
                  { label: 'Reservado', value: m.reservedChange ?? 0 },
                ]
                  .filter((p) => p.value !== 0)
                  .map((p) => `${p.label} ${p.value > 0 ? '+' : ''}${formatMXN(p.value)}`)
                  .join(' · ')}
              </span>
              <span className="w-full text-xs text-muted sm:w-auto">
                {m.createdAt ? dateTime.format(new Date(m.createdAt)) : ''}
              </span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}

export function isInsufficientFunds(e: unknown): boolean {
  return e instanceof ApiError && (e.code === 'INSUFFICIENT_FUNDS' || e.code === 'MONTHLY_LIMIT_REACHED');
}
