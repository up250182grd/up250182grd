import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { topupStatus } from '@/features/wallet/payments';
import { formatMXN } from '@/lib/money';

const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });

function RefundForm({ payment }: { payment: Schemas['AdminPaymentView'] }) {
  const qc = useQueryClient();
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const refund = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/api/v1/admin/payments/{id}/refunds', {
          params: { path: { id: payment.id ?? '' } },
          body: { amount: Number(amount), note },
        }),
      ),
    onSuccess: () => {
      setAmount('');
      setNote('');
      void qc.invalidateQueries({ queryKey: ['admin', 'payments'] });
      void qc.invalidateQueries({ queryKey: ['admin', 'billing'] });
    },
    onSettled: () => void qc.invalidateQueries({ queryKey: ['admin', 'payments'] }),
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    refund.mutate();
  };
  return (
    <form onSubmit={submit} className="mt-2 flex flex-col gap-2 rounded-lg bg-surface-2 p-3">
      <p className="text-xs text-muted">
        Devuelve saldo NO usado al medio de pago del alumno (hasta {formatMXN(payment.refundableMxn)}). Se retira de su
        saldo disponible; si la pasarela rechaza la devolución, el saldo se repone.
      </p>
      <div className="grid gap-2 sm:grid-cols-[8rem_1fr_auto] sm:items-end">
        <TextField label="Monto" type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
        <TextField label="Motivo" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
        <Button type="submit" variant="danger" loading={refund.isPending} disabled={!amount || note.trim().length < 5}>
          Devolver
        </Button>
      </div>
      {refund.isError && <Alert tone="error">{errorMessage(refund.error)}</Alert>}
      {refund.isSuccess && <Alert tone="success">Devolución registrada.</Alert>}
    </form>
  );
}

export function PaymentsTab() {
  const qc = useQueryClient();
  const config = useQuery({ queryKey: ['payments', 'config'], queryFn: () => unwrap(api.GET('/api/v1/payments/config')) });
  const list = useQuery({ queryKey: ['admin', 'payments'], queryFn: () => unwrap(api.GET('/api/v1/admin/payments')) });
  const [open, setOpen] = useState<string | null>(null);
  const reconcile = useMutation({
    mutationFn: (id: string) => unwrap(api.POST('/api/v1/admin/payments/{id}/reconcile', { params: { path: { id } } })),
    onSettled: () => void qc.invalidateQueries({ queryKey: ['admin'] }),
  });

  return (
    <div className="flex flex-col gap-4">
      {config.data && !config.data.enabled && (
        <Alert tone="info">
          La pasarela no está configurada en este entorno (PAYMENTS_PROVIDER=none). Para probar Mercado Pago en modo prueba
          ve a docs/DESARROLLO.md → Fase 7.
        </Alert>
      )}
      {config.data?.enabled && config.data.testMode && (
        <Alert tone="info">Mercado Pago en modo prueba: ningún cobro es real.</Alert>
      )}
      {reconcile.isError && <Alert tone="error">{errorMessage(reconcile.error)}</Alert>}
      {list.isPending && <Skeleton className="h-40" />}
      {list.isError && <Alert tone="error">{errorMessage(list.error)}</Alert>}
      {list.data?.length === 0 && <p className="text-muted">Aún no hay recargas con pasarela.</p>}
      <ul className="flex flex-col gap-2">
        {list.data?.map((p) => {
          const st = topupStatus[p.status ?? ''] ?? { label: p.status ?? '', tone: 'neutral' as const };
          return (
            <li key={p.id}>
              <Card className="flex flex-col gap-2 p-4">
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="font-mono font-semibold">{formatMXN(p.amountMxn)}</span>
                  <Badge tone={st.tone}>{st.label}</Badge>
                  <span className="text-muted">usuario #{p.userId}</span>
                  <span className="text-muted">pago {p.providerPaymentId ?? '—'} ({p.providerStatus ?? 'sin noticia'})</span>
                  <span className="flex-1 text-right text-xs text-muted">{p.createdAt ? dateTime.format(new Date(p.createdAt)) : ''}</span>
                </div>
                {(p.refunds?.length ?? 0) > 0 && (
                  <ul className="text-xs text-muted">
                    {p.refunds?.map((r) => (
                      <li key={r.id}>
                        Devolución {formatMXN(r.amountMxn)} · {r.status} · {r.note}
                        {r.failure && ` · ${r.failure}`}
                      </li>
                    ))}
                  </ul>
                )}
                <div className="flex flex-wrap gap-2">
                  {['CREATED', 'PENDING'].includes(p.status ?? '') && (
                    <Button
                      variant="secondary"
                      loading={reconcile.isPending && reconcile.variables === p.id}
                      onClick={() => reconcile.mutate(p.id ?? '')}
                    >
                      Conciliar con la pasarela
                    </Button>
                  )}
                  {(p.refundableMxn ?? 0) > 0 && (
                    <Button variant="secondary" onClick={() => setOpen(open === p.id ? null : (p.id ?? null))} aria-expanded={open === p.id}>
                      Devolución…
                    </Button>
                  )}
                </div>
                {open === p.id && <RefundForm payment={p} />}
              </Card>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export function IncidentItem({ incident }: { incident: Schemas['IncidentView'] }) {
  const qc = useQueryClient();
  const [note, setNote] = useState('');
  const [open, setOpen] = useState(false);
  const resolve = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/api/v1/admin/billing/incidents/{id}/resolve', {
          params: { path: { id: incident.id ?? 0 } },
          body: { note },
        }),
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin', 'billing'] }),
  });
  return (
    <li className="rounded-lg border border-border p-2 text-sm">
      <div className="flex flex-wrap items-start gap-2">
        <Badge tone="warning">{incident.type}</Badge>
        <span className="flex-1">{incident.description}</span>
        <button type="button" className="text-sm font-semibold text-primary hover:underline" onClick={() => setOpen(!open)}>
          Resolver
        </button>
      </div>
      {open && (
        <form
          className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            resolve.mutate();
          }}
        >
          <div className="flex-1">
            <TextField label="¿Cómo se resolvió?" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
          </div>
          <Button type="submit" loading={resolve.isPending} disabled={note.trim().length < 5}>
            Marcar resuelta
          </Button>
        </form>
      )}
      {resolve.isError && <Alert tone="error">{errorMessage(resolve.error)}</Alert>}
    </li>
  );
}

/** Descargas CSV del mes (se abren con la sesión del administrador; sin datos en la URL más que el mes). */
export function ReportsCard() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const links = [
    { href: '/api/v1/admin/reports/settlements.csv', label: 'Liquidaciones' },
    { href: '/api/v1/admin/reports/ledger.csv', label: 'Libro mayor' },
    { href: '/api/v1/admin/reports/payments.csv', label: 'Recargas' },
  ];
  return (
    <Card className="flex flex-col gap-3">
      <h3 className="font-semibold">Reportes (CSV)</h3>
      <label className="flex flex-col gap-1 text-sm sm:w-48">
        <span className="font-medium">Mes</span>
        <input
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          className="min-h-11 rounded-lg border border-border bg-surface px-3"
        />
      </label>
      <div className="flex flex-wrap gap-2">
        {links.map((l) => (
          <a
            key={l.href}
            href={`${l.href}?month=${encodeURIComponent(month)}`}
            download
            className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-border px-3 text-sm font-semibold hover:bg-surface-2"
          >
            <Download aria-hidden className="size-4" /> {l.label}
          </a>
        ))}
      </div>
      <p className="text-xs text-muted">
        Cada asiento del libro mayor suma cero; los totales de liquidaciones coinciden con este resumen. Abre en Excel o
        Google Sheets (UTF-8).
      </p>
    </Card>
  );
}
