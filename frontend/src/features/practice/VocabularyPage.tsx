import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { cn } from '@/lib/cn';
import { formatInterval, posLabels, practiceKeys, stateLabels } from './api';

const dateFmt = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' });
const filters = [
  { id: 'ALL', label: 'Todas' },
  { id: 'NEW', label: 'Nuevas' },
  { id: 'LEARNING', label: 'Aprendiendo' },
  { id: 'REVIEW', label: 'En repaso' },
  { id: 'RELEARNING', label: 'Olvidadas' },
] as const;

/** "Mis palabras": todo el vocabulario del alumno con su estado de repaso. */
export function VocabularyPage() {
  const words = useQuery({ queryKey: practiceKeys.words, queryFn: () => unwrap(api.GET('/api/v1/vocabulary/cards')) });
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<(typeof filters)[number]['id']>('ALL');

  const shown = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (words.data ?? []).filter(
      (w) =>
        (filter === 'ALL' || w.state === filter) &&
        (!q || w.term?.toLowerCase().includes(q) || w.translation?.toLowerCase().includes(q)),
    );
  }, [words.data, search, filter]);

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Mis palabras</h1>
          <p className="mt-1 text-muted">El vocabulario de tus lecciones y cuándo te tocará repasarlo.</p>
        </div>
        <Link to="/app/practice" className="text-sm font-semibold text-primary hover:underline">
          Ir al repaso del día
        </Link>
      </div>

      <Card className="flex flex-col gap-4">
        <TextField label="Buscar" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Palabra o traducción" />
        <div className="flex flex-wrap gap-2" role="group" aria-label="Filtrar por estado">
          {filters.map((f) => (
            <button
              key={f.id}
              type="button"
              aria-pressed={filter === f.id}
              onClick={() => setFilter(f.id)}
              className={cn(
                'min-h-9 rounded-full border px-3 text-sm',
                filter === f.id ? 'border-primary bg-primary/10 font-semibold text-primary' : 'border-border hover:bg-surface-2',
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
      </Card>

      {words.isPending && <Skeleton className="h-48" />}
      {words.isError && <Alert tone="error">{errorMessage(words.error)}</Alert>}
      {words.data?.length === 0 && (
        <p className="text-muted">Todavía no tienes palabras. Completa una lección para empezar.</p>
      )}
      {words.data && words.data.length > 0 && (
        <Card className="overflow-x-auto p-0">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Vocabulario con su estado de repaso</caption>
            <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
              <tr>
                <th scope="col" className="px-4 py-3">Palabra</th>
                <th scope="col" className="px-4 py-3">Significado</th>
                <th scope="col" className="px-4 py-3">Estado</th>
                <th scope="col" className="hidden px-4 py-3 sm:table-cell">Intervalo</th>
                <th scope="col" className="px-4 py-3">Próximo repaso</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((w) => {
                const st = stateLabels[w.state ?? 'NEW'] ?? stateLabels.NEW!;
                return (
                  <tr key={w.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3">
                      <span lang="en" className="font-semibold">{w.term}</span>
                      <span className="block text-xs text-muted">
                        {w.ipa} {posLabels[w.partOfSpeech ?? ''] ?? w.partOfSpeech}
                      </span>
                    </td>
                    <td className="px-4 py-3">{w.translation}</td>
                    <td className="px-4 py-3">
                      <Badge tone={st.tone}>{st.label}</Badge>
                    </td>
                    <td className="hidden px-4 py-3 text-muted sm:table-cell">
                      {w.state === 'NEW' ? '—' : formatInterval(w.intervalDays)}
                    </td>
                    <td className="px-4 py-3 text-muted">
                      {w.state === 'NEW' ? 'Cuando la estudies' : w.dueAt ? dateFmt.format(new Date(w.dueAt)) : '—'}
                    </td>
                  </tr>
                );
              })}
              {shown.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-muted">
                    Ninguna palabra coincide con el filtro.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}
