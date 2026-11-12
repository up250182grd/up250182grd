import { useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, unwrap, type Schemas } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { cn } from '@/lib/cn';
import { formatMXN, formatPrecise } from '@/lib/money';
import { IncidentItem, PaymentsTab, ReportsCard } from './PaymentsAdmin';

type Tab = 'summary' | 'payments' | 'pricing' | 'policy' | 'fx' | 'mine';
const tabs: { id: Tab; label: string }[] = [
  { id: 'summary', label: 'Resumen' },
  { id: 'payments', label: 'Recargas' },
  { id: 'pricing', label: 'Modelos y tarifas' },
  { id: 'policy', label: 'Política comercial' },
  { id: 'fx', label: 'Tipo de cambio' },
  { id: 'mine', label: 'Mi consumo' },
];
const date = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
const accountLabels: Record<string, string> = {
  SIMULATED_TOPUP_CLEARING: 'Recargas simuladas (contrapartida)',
  GATEWAY_CLEARING: 'Recargas cobradas por la pasarela (contrapartida)',
  REVENUE_AI_COST_RECOVERY: 'Ingreso: recuperación de costo de IA',
  REVENUE_MARKUP: 'Ingreso: recargo comercial (30 %)',
  TAX_PAYABLE: 'Impuestos por pagar',
  INTERNAL_AI_EXPENSE: 'Gasto interno del administrador',
  PROVIDER_COST_ACCRUED: 'Costo de proveedor por pagar (admin)',
};

function useInvalidate() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: ['admin', 'billing'] });
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 border-t border-border py-2 text-sm first:border-0">
      <span className="text-muted">{label}</span>
      <span className="font-mono">{children}</span>
    </div>
  );
}

function SummaryTab() {
  const summary = useQuery({
    queryKey: ['admin', 'billing', 'summary'],
    queryFn: () => unwrap(api.GET('/api/v1/admin/billing/summary')),
  });
  if (summary.isPending) return <Skeleton className="h-64" />;
  if (summary.isError) return <Alert tone="error">{errorMessage(summary.error)}</Alert>;
  const s = summary.data;
  const student = s.totalsByRole?.find((r) => r.roleCode === 'STUDENT');
  const admin = s.totalsByRole?.find((r) => r.roleCode === 'ADMIN');
  return (
    <div className="flex flex-col gap-4">
      {(s.ledgerProblems?.length ?? 0) > 0 && (
        <Alert tone="error" title="El libro contable no cuadra">
          {s.ledgerProblems?.join(' · ')}
        </Alert>
      )}
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <p className="text-sm text-muted">Cobrado a alumnos ({s.month})</p>
          <p className="mt-1 font-mono text-2xl font-bold">{formatMXN(student?.totalMxn ?? 0)}</p>
          <p className="text-xs text-muted">{student?.settlements ?? 0} sesiones liquidadas</p>
        </Card>
        <Card>
          <p className="text-sm text-muted">Ingreso bruto adicional (recargo)</p>
          <p className="mt-1 font-mono text-2xl font-bold text-success">{formatMXN(student?.markupMxn ?? 0)}</p>
          <p className="text-xs text-muted">No es utilidad neta</p>
        </Card>
        <Card>
          <p className="text-sm text-muted">Mi consumo interno al costo</p>
          <p className="mt-1 font-mono text-2xl font-bold">{formatMXN(admin?.directCostMxn ?? 0)}</p>
          <p className="text-xs text-muted">{admin?.settlements ?? 0} sesiones</p>
        </Card>
      </div>
      <Card>
        <h3 className="mb-2 font-semibold">Desglose de alumnos del mes</h3>
        <Row label="Costo directo de IA">{formatMXN(student?.directCostMxn ?? 0)}</Row>
        <Row label="Recargo comercial">{formatMXN(student?.markupMxn ?? 0)}</Row>
        <Row label="Impuestos">{formatMXN(student?.taxMxn ?? 0)}</Row>
        <Row label="Total cobrado">{formatMXN(student?.totalMxn ?? 0)}</Row>
        <p className="mt-3 text-xs text-muted">{s.note}</p>
      </Card>
      <Card>
        <h3 className="mb-2 font-semibold">Cuentas del negocio (acumulado)</h3>
        {Object.entries(s.businessAccounts ?? {}).map(([code, value]) => (
          <Row key={code} label={accountLabels[code] ?? code}>
            {formatMXN(value)}
          </Row>
        ))}
      </Card>
      <Card>
        <h3 className="mb-2 flex items-center gap-2 font-semibold">
          Incidencias abiertas <Badge tone={(s.openIncidents?.length ?? 0) > 0 ? 'warning' : 'success'}>{s.openIncidents?.length ?? 0}</Badge>
          <Badge>Conciliaciones pendientes: {s.pendingReconciliations ?? 0}</Badge>
        </h3>
        {s.openIncidents?.length === 0 && <p className="text-sm text-muted">Sin incidencias.</p>}
        <ul className="flex flex-col gap-2 text-sm">
          {s.openIncidents?.map((i) => <IncidentItem key={i.id} incident={i} />)}
        </ul>
      </Card>
      <ReportsCard />
    </div>
  );
}

type Modality = 'TEXT' | 'REALTIME' | 'TRANSCRIPTION';

const MODALITY_LABELS: Record<Modality, string> = {
  TEXT: 'Texto (profesor por escrito)',
  REALTIME: 'Voz en tiempo real',
  TRANSCRIPTION: 'Transcripción de voz',
};

/** Unidades que factura cada tipo de modelo (las mismas que valida el servidor). */
const UNITS_BY_MODALITY: Record<Modality, readonly string[]> = {
  TEXT: ['text_in', 'text_in_cached', 'text_out'],
  REALTIME: ['text_in', 'text_in_cached', 'text_out', 'audio_in', 'audio_in_cached', 'audio_out'],
  TRANSCRIPTION: ['audio_microminutes', 'audio_in', 'text_in', 'text_out'],
};

/** Se mide en millonésimas de minuto, así que su tarifa "por millón" es el precio por minuto. */
const PER_MINUTE_UNIT = 'audio_microminutes';

const UNIT_LABELS: Record<string, string> = {
  text_in: 'Texto: entrada',
  text_in_cached: 'Texto: entrada en caché',
  text_out: 'Texto: salida',
  audio_in: 'Audio: entrada',
  audio_in_cached: 'Audio: entrada en caché',
  audio_out: 'Audio: salida',
  [PER_MINUTE_UNIT]: 'Precio por minuto de audio',
};

function modalityOf(model: Schemas['ModelView']): Modality {
  return model.modality === 'REALTIME' || model.modality === 'TRANSCRIPTION' ? model.modality : 'TEXT';
}

/** Orden de presentación de las unidades: el de su tipo de modelo (texto antes que audio, entrada antes que salida). */
function unitOrder(model: Schemas['ModelView'], unit: string) {
  const i = UNITS_BY_MODALITY[modalityOf(model)].indexOf(unit);
  return i < 0 ? Number.MAX_SAFE_INTEGER : i;
}

function describeRate(unit: string, rate: number, currency?: string) {
  const suffix = unit === PER_MINUTE_UNIT ? '/min' : '/1M tokens';
  return `${UNIT_LABELS[unit] ?? unit}: ${formatPrecise(rate)} ${currency ?? ''}${suffix}`;
}

function AddPricingForm({ model }: { model: Schemas['ModelView'] }) {
  const invalidate = useInvalidate();
  const units = UNITS_BY_MODALITY[modalityOf(model)];
  const [currency, setCurrency] = useState('USD');
  const [rates, setRates] = useState<Record<string, string>>(() => Object.fromEntries(units.map((u) => [u, ''])));
  const [sourceUrl, setSourceUrl] = useState('');
  const [verifiedAt, setVerifiedAt] = useState(new Date().toISOString().slice(0, 10));
  const save = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/api/v1/admin/billing/models/{modelId}/pricing', {
          params: { path: { modelId: model.id ?? 0 } },
          body: {
            currency,
            ratesPerMillion: Object.fromEntries(
              Object.entries(rates)
                .filter(([, v]) => v !== '')
                .map(([k, v]) => [k, Number(v)]),
            ),
            sourceUrl,
            verifiedAt,
          },
        }),
      ),
    onSuccess: invalidate,
  });
  return (
    <form
      className="mt-3 flex flex-col gap-3 rounded-lg bg-surface-2 p-3"
      onSubmit={(e) => {
        e.preventDefault();
        save.mutate();
      }}
    >
      <p className="text-sm font-medium">
        {modalityOf(model) === 'TRANSCRIPTION'
          ? 'Nueva versión de tarifa'
          : 'Nueva versión de tarifa (precio por 1 000 000 de tokens)'}
      </p>
      {modalityOf(model) === 'TRANSCRIPTION' && (
        <p className="text-xs text-muted">
          Si el modelo se cobra por duración (p. ej. «$0.017 / minute»), captura solo el precio por minuto. Si se cobra
          por tokens, captura sus tarifas por 1 000 000 de tokens. Usa la misma moneda que el modelo de voz.
        </p>
      )}
      <div className="grid gap-3 sm:grid-cols-4">
        <div className="flex flex-col gap-1.5">
          <label htmlFor={`cur-${model.id}`} className="text-sm font-medium">
            Moneda
          </label>
          <select id={`cur-${model.id}`} value={currency} onChange={(e) => setCurrency(e.target.value)} className="min-h-11 rounded-lg border border-border bg-surface px-2">
            <option>USD</option>
            <option>MXN</option>
          </select>
        </div>
        {units.map((k) => (
          <TextField
            key={k}
            label={UNIT_LABELS[k] ?? k}
            type="number"
            step="0.0001"
            min={0}
            value={rates[k]}
            onChange={(e) => setRates({ ...rates, [k]: e.target.value })}
          />
        ))}
      </div>
      <TextField
        label="URL oficial de la tarifa (obligatoria)"
        type="url"
        placeholder="https://…"
        value={sourceUrl}
        onChange={(e) => setSourceUrl(e.target.value)}
      />
      <div className="w-48">
        <TextField label="Verificada el" type="date" value={verifiedAt} onChange={(e) => setVerifiedAt(e.target.value)} />
      </div>
      {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
      {save.isSuccess && <Alert tone="success">Tarifa registrada. Aplica a sesiones nuevas.</Alert>}
      <div>
        <Button type="submit" loading={save.isPending}>
          Registrar tarifa
        </Button>
      </div>
    </form>
  );
}

function PricingTab() {
  const invalidate = useInvalidate();
  const models = useQuery({ queryKey: ['admin', 'billing', 'models'], queryFn: () => unwrap(api.GET('/api/v1/admin/billing/models')) });
  const [modelCode, setModelCode] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [modality, setModality] = useState<Modality>('TEXT');
  const create = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/api/v1/admin/billing/models', { body: { provider: 'OPENAI', modelCode, modality, displayName } })),
    onSuccess: invalidate,
  });
  return (
    <div className="flex flex-col gap-4">
      <Alert tone="info">
        Las tarifas no están en el código: captúralas aquí desde la página oficial de precios del proveedor, con su URL y
        fecha de verificación. Cada cambio crea una versión nueva; las sesiones ya iniciadas conservan su tarifa.
      </Alert>
      {models.isError && <Alert tone="error">{errorMessage(models.error)}</Alert>}
      {models.data?.map((m) => (
        <Card key={m.id}>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold">{m.displayName}</h3>
            <Badge>{m.provider}</Badge>
            <span className="font-mono text-xs text-muted">{m.modelCode}</span>
            <Badge>{MODALITY_LABELS[modalityOf(m)]}</Badge>
            {m.simulated && <Badge tone="warning">Simulado</Badge>}
          </div>
          {m.currentPricing ? (
            <div className="mt-2 text-sm">
              <p>
                Tarifa vigente:{' '}
                {Object.entries(m.currentPricing.ratesPerMillion ?? {})
                  .sort(([a], [b]) => unitOrder(m, a) - unitOrder(m, b))
                  .map(([k, v]) => describeRate(k, v, m.currentPricing?.currency))
                  .join(' · ')}
              </p>
              <p className="text-xs text-muted">
                Fuente: {m.currentPricing.sourceUrl} · verificada {m.currentPricing.verifiedAt}
              </p>
            </div>
          ) : (
            <Alert tone="error">Sin tarifa vigente: este modelo no se puede usar todavía.</Alert>
          )}
          {!m.simulated && <AddPricingForm model={m} />}
        </Card>
      ))}
      <Card>
        <h3 className="font-semibold">Registrar modelo de OpenAI</h3>
        <form
          className="mt-3 flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate();
          }}
        >
          <div className="flex flex-col gap-1.5">
            <label htmlFor="new-model-modality" className="text-sm font-medium">
              Tipo
            </label>
            <select
              id="new-model-modality"
              value={modality}
              onChange={(e) => setModality(e.target.value as Modality)}
              className="min-h-11 rounded-lg border border-border bg-surface px-2"
            >
              {(Object.keys(MODALITY_LABELS) as Modality[]).map((k) => (
                <option key={k} value={k}>
                  {MODALITY_LABELS[k]}
                </option>
              ))}
            </select>
          </div>
          <TextField label="Código del modelo" value={modelCode} onChange={(e) => setModelCode(e.target.value)} />
          <TextField label="Nombre visible" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          <Button type="submit" loading={create.isPending}>
            Registrar
          </Button>
        </form>
        {create.isError && <Alert tone="error">{errorMessage(create.error)}</Alert>}
        <p className="mt-2 text-xs text-muted">
          {
            {
              TEXT: 'Para usarlo: AI_PROVIDER=openai, OPENAI_API_KEY y AI_TEXT_MODEL con este código, y registra su tarifa.',
              REALTIME: 'Para usarlo: AI_VOICE_PROVIDER=openai, OPENAI_API_KEY y AI_REALTIME_MODEL con este código, y registra su tarifa.',
              TRANSCRIPTION:
                'La voz real no inicia llamadas sin esta tarifa: usa el código de AI_REALTIME_TRANSCRIPTION_MODEL (por omisión gpt-live-transcribe).',
            }[modality]
          }
        </p>
      </Card>
    </div>
  );
}

function PolicyTab() {
  const invalidate = useInvalidate();
  const policies = useQuery({ queryKey: ['admin', 'billing', 'policies'], queryFn: () => unwrap(api.GET('/api/v1/admin/billing/policies')) });
  const [role, setRole] = useState<'STUDENT' | 'ADMIN'>('STUDENT');
  const [factor, setFactor] = useState('1.30');
  const [note, setNote] = useState('');
  const create = useMutation({
    mutationFn: () =>
      unwrap(api.POST('/api/v1/admin/billing/policies', { body: { roleCode: role, factor: Number(factor), taxMode: 'NONE', note } })),
    onSuccess: invalidate,
  });
  return (
    <div className="flex flex-col gap-4">
      <Card className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="py-2 pr-3">Rol</th>
              <th scope="col" className="py-2 pr-3">Factor</th>
              <th scope="col" className="py-2 pr-3">Impuestos</th>
              <th scope="col" className="py-2 pr-3">Vigente desde</th>
              <th scope="col" className="py-2">Nota</th>
            </tr>
          </thead>
          <tbody>
            {policies.data?.map((p) => (
              <tr key={p.id} className="border-t border-border">
                <td className="py-2 pr-3">
                  {p.roleCode === 'ADMIN' ? 'Administrador' : 'Alumno'} {p.current && <Badge tone="success">Vigente</Badge>}
                </td>
                <td className="py-2 pr-3 font-mono">×{Number(p.factor).toFixed(2)}</td>
                <td className="py-2 pr-3">{p.taxMode === 'NONE' ? 'Sin IVA (pendiente D-1)' : `${(p.taxRate ?? 0) * 100} %`}</td>
                <td className="py-2 pr-3">{p.effectiveFrom ? date.format(new Date(p.effectiveFrom)) : ''}</td>
                <td className="py-2 text-muted">{p.note}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
      <Card>
        <h3 className="font-semibold">Nueva versión del factor</h3>
        <p className="mt-1 text-sm text-muted">
          Valores obligatorios iniciales: alumno ×1.30, administrador ×1.00. Un cambio aplica solo a sesiones nuevas y
          queda auditado. No lo cambies sin autorización.
        </p>
        <form
          className="mt-3 flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (window.confirm(`¿Crear una nueva política: ${role === 'ADMIN' ? 'administrador' : 'alumno'} ×${factor}?`)) create.mutate();
          }}
        >
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="pol-role" className="text-sm font-medium">
                Rol
              </label>
              <select id="pol-role" value={role} onChange={(e) => setRole(e.target.value as 'STUDENT' | 'ADMIN')} className="min-h-11 rounded-lg border border-border bg-surface px-2">
                <option value="STUDENT">Alumno</option>
                <option value="ADMIN">Administrador</option>
              </select>
            </div>
            <TextField label="Factor" type="number" step="0.0001" min={1} max={5} value={factor} onChange={(e) => setFactor(e.target.value)} />
          </div>
          <TextField label="Motivo del cambio" value={note} onChange={(e) => setNote(e.target.value)} />
          {create.isError && <Alert tone="error">{errorMessage(create.error)}</Alert>}
          <div>
            <Button type="submit" variant="danger" loading={create.isPending}>
              Crear política
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}

function FxTab() {
  const invalidate = useInvalidate();
  const fx = useQuery({ queryKey: ['admin', 'billing', 'fx'], queryFn: () => unwrap(api.GET('/api/v1/admin/billing/fx-rates')) });
  const [rate, setRate] = useState('');
  const [day, setDay] = useState(new Date().toISOString().slice(0, 10));
  const add = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/admin/billing/fx-rates', { body: { base: 'USD', rate: Number(rate), date: day } })),
    onSuccess: invalidate,
  });
  return (
    <div className="flex flex-col gap-4">
      <Alert tone="info">
        Las tarifas en USD necesitan un tipo de cambio USD/MXN de los últimos 7 días; sin él, las sesiones quedan pendientes
        de conciliación en lugar de inventar un cargo. Fuente recomendada: FIX de Banxico (captura manual por ahora).
      </Alert>
      <Card>
        <form
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            add.mutate();
          }}
        >
          <TextField label="MXN por 1 USD" type="number" step="0.000001" min={0} value={rate} onChange={(e) => setRate(e.target.value)} />
          <TextField label="Fecha" type="date" value={day} onChange={(e) => setDay(e.target.value)} />
          <Button type="submit" loading={add.isPending}>
            Registrar
          </Button>
        </form>
        {add.isError && <div className="mt-2"><Alert tone="error">{errorMessage(add.error)}</Alert></div>}
      </Card>
      <Card>
        {fx.data?.length === 0 && <p className="text-sm text-muted">Sin tipos de cambio registrados.</p>}
        {fx.data?.map((f) => (
          <Row key={f.id} label={`${f.rateDate} · ${f.source}`}>
            {formatPrecise(f.rate)} MXN/USD
          </Row>
        ))}
      </Card>
    </div>
  );
}

function MineTab() {
  const usage = useQuery({ queryKey: ['admin', 'billing', 'mine'], queryFn: () => unwrap(api.GET('/api/v1/admin/billing/my-usage')) });
  return (
    <Card className="overflow-x-auto">
      <p className="mb-3 text-sm text-muted">Tus sesiones al costo directo del proveedor, sin recargo (factor ×1.00).</p>
      {usage.data?.length === 0 && <p className="text-sm text-muted">Aún no tienes consumo.</p>}
      <table className="w-full text-left text-sm">
        <tbody>
          {usage.data?.map((u) => (
            <tr key={u.settlementId} className="border-t border-border first:border-0">
              <td className="py-2 pr-3">{u.settledAt ? date.format(new Date(u.settledAt)) : ''}</td>
              <td className="py-2 pr-3 font-mono text-xs text-muted">
                {formatPrecise(u.detail?.providerCost, u.detail?.providerCurrency)}
                {u.detail?.fxRate && u.detail.providerCurrency !== 'MXN' ? ` × ${formatPrecise(u.detail.fxRate)}` : ''}
              </td>
              <td className="py-2 pr-3">×{Number(u.detail?.factorApplied ?? 1).toFixed(2)}</td>
              <td className="py-2 text-right font-mono font-semibold">{formatMXN(u.detail?.totalMxn)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

export function AdminBillingPage() {
  const [tab, setTab] = useState<Tab>('summary');
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Finanzas e IA</h1>
        <p className="mt-1 text-muted">Consumo, ingresos, tarifas y política comercial. Todo cambio queda auditado.</p>
      </div>
      <div role="tablist" aria-label="Secciones de finanzas" className="flex flex-wrap gap-1 rounded-lg bg-surface-2 p-1">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              'min-h-10 rounded-md px-3 text-sm font-medium',
              tab === t.id ? 'bg-surface text-primary shadow-sm' : 'text-muted hover:text-text',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel">
        {tab === 'summary' && <SummaryTab />}
        {tab === 'payments' && <PaymentsTab />}
        {tab === 'pricing' && <PricingTab />}
        {tab === 'policy' && <PolicyTab />}
        {tab === 'fx' && <FxTab />}
        {tab === 'mine' && <MineTab />}
      </div>
    </div>
  );
}
