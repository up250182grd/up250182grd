import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';

const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'medium' });

function State({ label, json }: { label: string; json?: string | null }) {
  if (!json) return null;
  let pretty = json;
  try {
    pretty = JSON.stringify(JSON.parse(json), null, 1);
  } catch {
    // se muestra tal cual
  }
  return (
    <div className="min-w-0 flex-1">
      <p className="text-xs font-semibold text-muted">{label}</p>
      <pre className="overflow-x-auto whitespace-pre-wrap break-all rounded bg-surface-2 p-2 text-xs">{pretty}</pre>
    </div>
  );
}

/** Bitácora de auditoría (solo lectura): quién hizo qué, cuándo, y el antes/después. */
export function AuditPage() {
  const [action, setAction] = useState('');
  const [targetId, setTargetId] = useState('');
  const [filters, setFilters] = useState({ action: '', targetId: '' });
  const log = useInfiniteQuery({
    queryKey: ['admin', 'audit', filters],
    initialPageParam: undefined as number | undefined,
    queryFn: ({ pageParam }) =>
      unwrap(
        api.GET('/api/v1/admin/audit', {
          params: {
            query: {
              action: filters.action || undefined,
              targetId: filters.targetId || undefined,
              before: pageParam,
              limit: 30,
            },
          },
        }),
      ),
    getNextPageParam: (last) => last.nextBefore ?? undefined,
  });
  const actions = log.data?.pages[0]?.actions ?? [];
  const items = log.data?.pages.flatMap((p) => p.items ?? []) ?? [];

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Auditoría</h1>
        <p className="mt-1 text-muted">Registro inmutable de las acciones sensibles: precios, pagos, devoluciones, roles…</p>
      </div>
      <Card>
        <form
          className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            setFilters({ action, targetId: targetId.trim() });
          }}
        >
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">Acción</span>
            <select
              value={action}
              onChange={(e) => setAction(e.target.value)}
              className="min-h-11 rounded-lg border border-border bg-surface px-3"
            >
              <option value="">Todas</option>
              {actions.map((a) => (
                <option key={a} value={a}>
                  {a}
                </option>
              ))}
            </select>
          </label>
          <TextField label="Id del objeto" value={targetId} onChange={(e) => setTargetId(e.target.value)} placeholder="Ej.: id de una recarga" />
          <Button type="submit">Filtrar</Button>
        </form>
      </Card>
      {log.isPending && <Skeleton className="h-64" />}
      {log.isError && <Alert tone="error">{errorMessage(log.error)}</Alert>}
      {log.data && items.length === 0 && <p className="text-muted">Sin registros con esos filtros.</p>}
      <ol className="flex flex-col gap-2" aria-label="Registros de auditoría">
        {items.map((a) => (
          <li key={a.id}>
            <Card className="flex flex-col gap-2 p-4">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <Badge tone="primary">{a.action}</Badge>
                <span className="font-mono text-xs">
                  {a.targetType} {a.targetId}
                </span>
                <span className="text-muted">{a.actorUserId ? `por usuario #${a.actorUserId}` : 'por el sistema'}</span>
                <span className="flex-1 text-right text-xs text-muted">{a.createdAt ? dateTime.format(new Date(a.createdAt)) : ''}</span>
              </div>
              {(a.beforeState || a.afterState) && (
                <div className="flex flex-col gap-2 sm:flex-row">
                  <State label="Antes" json={a.beforeState} />
                  <State label="Después" json={a.afterState} />
                </div>
              )}
            </Card>
          </li>
        ))}
      </ol>
      {log.hasNextPage && (
        <div>
          <Button variant="secondary" loading={log.isFetchingNextPage} onClick={() => void log.fetchNextPage()}>
            Cargar más
          </Button>
        </div>
      )}
    </div>
  );
}
