import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';

/** Cada regla del motor (docs: RecommendationEngine, ADR-0009). */
const rules: { key: string; label: string; text: string }[] = [
  { key: 'diagnostic', label: 'Diagnóstico pendiente', text: 'Alumno sin diagnóstico de nivel.' },
  { key: 'reviews', label: 'Repasos vencidos', text: 'Tarjetas de vocabulario vencidas o nuevas.' },
  { key: 'nextLesson', label: 'Siguiente lección', text: 'Próxima lección no dominada del plan.' },
  { key: 'recurringErrors', label: 'Errores recurrentes', text: 'Errores del profesor IA sin superar.' },
  { key: 'weakSkill', label: 'Habilidad más débil', text: 'Diferencia de nivel entre habilidades.' },
  { key: 'conversation', label: 'Conversación', text: 'Practicar con el profesor IA (si tiene saldo).' },
];

const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });

function WeightsForm({ initial, onSaved }: { initial: Record<string, number>; onSaved: (version: number) => void }) {
  const queryClient = useQueryClient();
  const [weights, setWeights] = useState<Record<string, number>>(initial);
  const [note, setNote] = useState('');
  const save = useMutation({
    mutationFn: () => unwrap(api.POST('/api/v1/admin/recommendations/weights', { body: { weights, note } })),
    onSuccess: (saved) => {
      onSaved(saved.id ?? 0);
      setNote('');
      void queryClient.invalidateQueries({ queryKey: ['admin', 'recommendation-weights'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    },
  });
  const submit = (e: FormEvent) => {
    e.preventDefault();
    save.mutate();
  };
  return (
    <Card>
      <form onSubmit={submit} className="flex flex-col gap-5">
        <div className="grid gap-4 sm:grid-cols-2">
          {rules.map((r) => (
            <label key={r.key} className="flex flex-col gap-1">
              <span className="flex items-center justify-between font-medium">
                {r.label}
                <span className="font-mono text-sm">{weights[r.key] ?? 0}</span>
              </span>
              <input
                type="range"
                min={0}
                max={100}
                step={5}
                value={weights[r.key] ?? 0}
                onChange={(e) => setWeights({ ...weights, [r.key]: Number(e.target.value) })}
                className="accent-[var(--color-primary)]"
                aria-describedby={`${r.key}-help`}
              />
              <span id={`${r.key}-help`} className="text-xs text-muted">
                {r.text} 0 = desactivada.
              </span>
            </label>
          ))}
        </div>
        <TextField
          label="Motivo del cambio"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={500}
          placeholder="Ej.: dar más prioridad a los repasos vencidos"
          required
        />
        {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        <div>
          <Button type="submit" loading={save.isPending} disabled={note.trim().length < 5}>
            Guardar nueva versión
          </Button>
        </div>
      </form>
    </Card>
  );
}

export function RecommendationWeightsPage() {
  const [savedVersion, setSavedVersion] = useState<number | null>(null);
  const history = useQuery({
    queryKey: ['admin', 'recommendation-weights'],
    queryFn: () => unwrap(api.GET('/api/v1/admin/recommendations/weights')),
  });
  const current = history.data?.[0];
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Recomendaciones</h1>
        <p className="mt-1 text-muted">
          Pesos (0–100) del motor de reglas que arma la sección "Para hoy". Cada cambio crea una versión nueva y queda en
          la auditoría; no usa IA ni tiene costo.
        </p>
      </div>
      {history.isPending && <Skeleton className="h-64" />}
      {history.isError && <Alert tone="error">{errorMessage(history.error)}</Alert>}
      {savedVersion != null && (
        <Alert tone="success">Pesos guardados como versión nueva (v{savedVersion}).</Alert>
      )}
      {current && <WeightsForm key={current.id} initial={current.weights ?? {}} onSaved={setSavedVersion} />}
      {history.data && (
        <Card>
          <h2 className="mb-3 font-semibold">Historial</h2>
          <ol className="flex flex-col gap-2 text-sm">
            {history.data.map((v, i) => (
              <li key={v.id} className="flex flex-wrap items-center gap-2 border-b border-border pb-2 last:border-0">
                <span className="font-mono text-muted">v{v.id}</span>
                {i === 0 && <Badge tone="success">Vigente</Badge>}
                <span className="flex-1">{v.note}</span>
                <span className="text-xs text-muted">{v.createdAt ? dateTime.format(new Date(v.createdAt)) : ''}</span>
                <span className="w-full font-mono text-xs text-muted">
                  {rules.map((r) => `${r.key}=${v.weights?.[r.key] ?? 0}`).join(' · ')}
                </span>
              </li>
            ))}
          </ol>
        </Card>
      )}
    </div>
  );
}
