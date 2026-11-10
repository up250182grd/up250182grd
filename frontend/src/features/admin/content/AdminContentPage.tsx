import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, Plus } from 'lucide-react';
import { api, unwrap } from '@/api/client';
import { Alert } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, Skeleton } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { errorMessage } from '@/features/auth/schemas';
import { useT } from '@/lib/lang';

export const contentKeys = {
  tree: ['admin', 'content', 'tree'] as const,
  lesson: (id: string) => ['admin', 'content', 'lesson', id] as const,
};

export const statusBadge: Record<string, { label: string; tone: 'success' | 'neutral' | 'danger' }> = {
  PUBLISHED: { label: 'Publicada', tone: 'success' },
  DRAFT: { label: 'Borrador', tone: 'neutral' },
  ARCHIVED: { label: 'Archivada', tone: 'danger' },
};

function NewLessonForm({ moduleId, onDone }: { moduleId: string; onDone: () => void }) {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [slug, setSlug] = useState('');
  const [titleEs, setTitleEs] = useState('');
  const [titleEn, setTitleEn] = useState('');
  const create = useMutation({
    mutationFn: () =>
      unwrap(
        api.POST('/api/v1/admin/content/modules/{modulePublicId}/lessons', {
          params: { path: { modulePublicId: moduleId } },
          body: {
            slug,
            level: 'A1',
            title: { es: titleEs, en: titleEn },
            objective: { es: 'Pendiente de redactar.', en: 'To be written.' },
            explanation: { es: 'Pendiente de redactar.', en: 'To be written.' },
            examplesJson: '[]',
            vocabularyJson: '[]',
            estimatedMinutes: 10,
          },
        }),
      ),
    onSuccess: (lesson) => {
      qc.invalidateQueries({ queryKey: contentKeys.tree });
      onDone();
      navigate(`/admin/content/lessons/${lesson.publicId}`);
    },
  });
  return (
    <form
      className="flex flex-col gap-3 border-t border-border bg-surface-2 p-4"
      onSubmit={(e) => {
        e.preventDefault();
        create.mutate();
      }}
    >
      {create.isError && <Alert tone="error">{errorMessage(create.error)}</Alert>}
      <TextField
        label="Identificador (slug)"
        hint="Minúsculas, números y guiones. Ej.: a1-colours"
        value={slug}
        onChange={(e) => setSlug(e.target.value)}
        required
      />
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField label="Título (español)" value={titleEs} onChange={(e) => setTitleEs(e.target.value)} required />
        <TextField label="Título (inglés)" value={titleEn} onChange={(e) => setTitleEn(e.target.value)} required />
      </div>
      <p className="text-xs text-muted">Se crea como borrador: los alumnos no la verán hasta publicarla.</p>
      <div className="flex gap-2">
        <Button type="submit" loading={create.isPending}>
          Crear borrador
        </Button>
        <Button variant="ghost" onClick={onDone}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}

/** RF-ADM-2: árbol de contenido con estado de publicación y de revisión humana. */
export function AdminContentPage() {
  const t = useT();
  const qc = useQueryClient();
  const [creatingIn, setCreatingIn] = useState<string | null>(null);
  const tree = useQuery({
    queryKey: contentKeys.tree,
    queryFn: () => unwrap(api.GET('/api/v1/admin/content/tree')),
  });
  const move = useMutation({
    mutationFn: ({ id, direction }: { id: string; direction: 'UP' | 'DOWN' }) =>
      unwrap(
        api.POST('/api/v1/admin/content/lessons/{publicId}/move', {
          params: { path: { publicId: id } },
          body: { direction },
        }),
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: contentKeys.tree }),
  });

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Contenido educativo</h1>
        <p className="mt-1 text-muted">
          Crea, edita, ordena y publica lecciones. El contenido semilla aparece como <strong>sin revisar</strong> hasta
          que lo apruebes. Cada cambio queda en la auditoría.
        </p>
      </div>
      {tree.isPending && <Skeleton className="h-64" />}
      {tree.isError && <Alert tone="error">{errorMessage(tree.error)}</Alert>}
      {move.isError && <Alert tone="error">{errorMessage(move.error)}</Alert>}

      {tree.data?.map((course) => (
        <section key={course.publicId} className="flex flex-col gap-3">
          <h2 className="flex flex-wrap items-center gap-2 text-xl font-semibold">
            {t(course.title)} <Badge tone={statusBadge[course.status ?? '']?.tone}>{statusBadge[course.status ?? '']?.label}</Badge>
          </h2>
          {course.modules?.map((m) => (
            <Card key={m.publicId} className="p-0">
              <div className="flex items-center justify-between gap-2 border-b border-border px-4 py-3">
                <h3 className="font-semibold">{t(m.title)}</h3>
                <Button
                  variant="ghost"
                  icon={<Plus aria-hidden className="size-4" />}
                  onClick={() => setCreatingIn(creatingIn === m.publicId ? null : (m.publicId ?? null))}
                >
                  Nueva lección
                </Button>
              </div>
              {creatingIn === m.publicId && (
                <NewLessonForm moduleId={m.publicId ?? ''} onDone={() => setCreatingIn(null)} />
              )}
              <ul>
                {m.lessons?.map((l, i) => (
                  <li key={l.publicId} className="flex items-center gap-2 border-b border-border px-4 py-2 last:border-0">
                    <div className="flex flex-col">
                      <button
                        type="button"
                        aria-label={`Subir ${t(l.title)}`}
                        disabled={i === 0 || move.isPending}
                        onClick={() => move.mutate({ id: l.publicId ?? '', direction: 'UP' })}
                        className="rounded p-1 text-muted hover:bg-surface-2 disabled:opacity-30"
                      >
                        <ArrowUp aria-hidden className="size-4" />
                      </button>
                      <button
                        type="button"
                        aria-label={`Bajar ${t(l.title)}`}
                        disabled={i === (m.lessons?.length ?? 0) - 1 || move.isPending}
                        onClick={() => move.mutate({ id: l.publicId ?? '', direction: 'DOWN' })}
                        className="rounded p-1 text-muted hover:bg-surface-2 disabled:opacity-30"
                      >
                        <ArrowDown aria-hidden className="size-4" />
                      </button>
                    </div>
                    <Link
                      to={`/admin/content/lessons/${l.publicId}`}
                      className="flex min-h-12 flex-1 flex-wrap items-center gap-2 rounded-md px-2 hover:bg-surface-2"
                    >
                      <span className="flex-1 font-medium">{t(l.title)}</span>
                      <span className="text-xs text-muted">{l.exerciseCount} ejercicios</span>
                      <Badge tone={statusBadge[l.status ?? '']?.tone}>{statusBadge[l.status ?? '']?.label}</Badge>
                      {l.reviewed ? <Badge tone="success">Revisada</Badge> : <Badge tone="warning">Sin revisar</Badge>}
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </section>
      ))}
    </div>
  );
}
