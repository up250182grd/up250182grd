import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import type { Schemas } from '@/api/client';
import { formatMXN } from '@/lib/money';

export type VoiceSessionView = Schemas['VoiceSessionView'];

export const endReasonLabels: Record<string, string> = {
  USER: 'Terminaste la llamada.',
  RESERVE_LIMIT: 'Se agotó el saldo reservado para la llamada.',
  MAX_DURATION: 'Se alcanzó la duración máxima de la llamada.',
  CLIENT_LOST: 'Se perdió la conexión con tu navegador.',
  SERVER_RESTART: 'La llamada se interrumpió por mantenimiento del servidor.',
  PROVIDER_ERROR: 'No se pudo conectar con el profesor de voz.',
};

export function endReasonLabel(reason?: string | null): string {
  if (!reason) return '';
  if (reason.startsWith('PROVIDER_') && !endReasonLabels[reason]) return 'El proveedor de voz cerró la llamada.';
  return endReasonLabels[reason] ?? reason;
}

export function formatDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export function servicePercent(factor?: number | null): string {
  if (factor == null) return '';
  return `${Math.round((factor - 1) * 100)} %`;
}

export function VoiceSettlement({ session }: { session: VoiceSessionView }) {
  const s = session.settlement;
  if (!s) return null;
  return (
    <Card role="status" className="flex flex-col gap-3">
      <div>
        <h2 className="font-semibold">Resumen de la llamada</h2>
        <p className="text-sm text-muted">
          Duración {formatDuration(session.connectedSeconds ?? 0)} · {session.studentTurns ?? 0} intervenciones tuyas
          {session.endReason ? ` · ${endReasonLabel(session.endReason)}` : ''}
        </p>
      </div>
      {s.status === 'PENDING_RECONCILIATION' ? (
        <Alert tone="info">El cargo está pendiente de conciliación: {s.note}</Alert>
      ) : (
        <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
          <dt className="text-muted">Costo de IA (medido)</dt>
          <dd className="text-right font-mono">{formatMXN(s.directCostMxn)}</dd>
          <dt className="text-muted">
            {s.internal ? 'Recargo (admin al costo)' : `Servicio (${servicePercent(s.factorApplied)})`}
          </dt>
          <dd className="text-right font-mono">{formatMXN(s.markupMxn)}</dd>
          <dt className="text-muted">Impuestos</dt>
          <dd className="text-right font-mono">{formatMXN(s.taxMxn)}</dd>
          <dt className="font-semibold">{s.internal ? 'Gasto interno' : 'Total cobrado'}</dt>
          <dd className="text-right font-mono font-semibold" data-testid="voice-settlement-total">
            {formatMXN(s.totalMxn)}
          </dd>
        </dl>
      )}
      <p className="text-xs text-muted">
        El cobro se basa en el consumo que el proveedor informó al servidor, no en el tiempo de llamada.
      </p>
    </Card>
  );
}

export function Transcript({ lines, stored }: { lines: { role?: string; content?: string; text?: string }[]; stored?: boolean }) {
  if (lines.length === 0) {
    return (
      <p className="text-sm text-muted">
        {stored === false
          ? 'No se guardó la transcripción (puedes cambiarlo en Ajustes). El audio nunca se guarda.'
          : 'Aún no hay transcripción.'}
      </p>
    );
  }
  return (
    <ol className="flex flex-col gap-2" aria-label="Transcripción">
      {lines.map((l, i) => (
        <li key={i} className={l.role === 'USER' ? 'self-end text-right' : 'self-start'}>
          <Badge tone={l.role === 'USER' ? 'primary' : 'neutral'} className="mb-1">
            {l.role === 'USER' ? 'Tú' : 'Profesor'}
          </Badge>
          <p
            className={`max-w-prose rounded-lg px-3 py-2 text-sm ${
              l.role === 'USER' ? 'bg-primary/10' : 'bg-surface-2'
            }`}
          >
            {l.content ?? l.text}
          </p>
        </li>
      ))}
    </ol>
  );
}
