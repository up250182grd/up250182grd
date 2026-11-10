import { Link } from 'react-router';
import { ChevronRight, Clock, Target } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Badge, ProgressBadge, ProgressBar } from '@/components/ui/Badge';
import { Card, Skeleton } from '@/components/ui/Card';
import { errorMessage } from '@/features/auth/schemas';
import { LanguageToggle, useT } from '@/lib/lang';
import { useCourses, useLearningSummary } from './api';

/** RF-CRS-5: qué aprendió el alumno, qué está en curso y qué falta. */
export function PlanPage() {
  const courses = useCourses();
  const summary = useLearningSummary();
  const t = useT();

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Mi plan</h1>
          <p className="mt-1 text-muted">
            {summary.data?.planStartLevel
              ? `Plan personalizado a partir de tu nivel estimado (${summary.data.planStartLevel}).`
              : 'Haz el diagnóstico para obtener un plan personalizado. Mientras tanto puedes estudiar cualquier lección.'}
          </p>
        </div>
        <LanguageToggle />
      </div>

      {!summary.data?.planStartLevel && (
        <Link
          to="/app/diagnostic"
          className="flex items-center gap-3 rounded-xl border border-primary/40 bg-primary/10 p-4 font-medium hover:bg-primary/15"
        >
          <Target aria-hidden className="size-6 text-primary" />
          <span className="flex-1">Hacer el diagnóstico de nivel (unos 15 minutos)</span>
          <ChevronRight aria-hidden className="size-5" />
        </Link>
      )}

      {summary.data && summary.data.totalLessons! > 0 && (
        <Card className="flex flex-col gap-3">
          <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
            <span>
              <strong>{summary.data.mastered}</strong> dominadas
            </span>
            <span>
              <strong>{summary.data.practiced}</strong> practicadas
            </span>
            <span>
              <strong>{summary.data.inProgress}</strong> en curso
            </span>
            <span>
              <strong>{summary.data.notStarted}</strong> pendientes
            </span>
          </div>
          <ProgressBar
            value={summary.data.mastered ?? 0}
            max={summary.data.totalLessons ?? 0}
            label="Lecciones dominadas del plan"
          />
        </Card>
      )}

      {courses.isPending && (
        <div className="flex flex-col gap-3" aria-busy="true" aria-label="Cargando cursos">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      )}
      {courses.isError && <Alert tone="error">{errorMessage(courses.error)}</Alert>}
      {courses.data?.length === 0 && <p className="text-muted">Aún no hay cursos publicados.</p>}

      {courses.data?.map((course) => (
        <section key={course.publicId} aria-labelledby={`c-${course.publicId}`} className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id={`c-${course.publicId}`} className="text-xl font-semibold">
              {t(course.title)}
            </h2>
            {course.inPlan && <Badge tone="primary">En tu plan</Badge>}
          </div>
          <p className="text-sm text-muted">{t(course.description)}</p>
          {course.modules?.map((m) => (
            <Card key={m.publicId} className="p-0">
              <div className="border-b border-border px-5 py-3">
                <h3 className="font-semibold">{t(m.title)}</h3>
                <p className="text-sm text-muted">{t(m.objective)}</p>
              </div>
              <ul>
                {m.lessons?.map((l) => (
                  <li key={l.publicId} className="border-b border-border last:border-0">
                    <Link
                      to={`/app/lessons/${l.publicId}`}
                      className="flex min-h-14 items-center gap-3 px-5 py-3 hover:bg-surface-2"
                    >
                      <span className="flex-1">
                        <span className="block font-medium">{t(l.title)}</span>
                        <span className="flex items-center gap-1 text-xs text-muted">
                          <Clock aria-hidden className="size-3" /> {l.estimatedMinutes} min · {l.level}
                        </span>
                      </span>
                      <ProgressBadge state={l.state} />
                      <ChevronRight aria-hidden className="size-4 text-muted" />
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
