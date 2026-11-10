import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

type Tone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'accent';

const tones: Record<Tone, string> = {
  neutral: 'bg-surface-2 text-muted',
  primary: 'bg-primary/12 text-primary',
  success: 'bg-success/12 text-success',
  warning: 'bg-warning/15 text-warning',
  danger: 'bg-danger/12 text-danger',
  accent: 'bg-accent/12 text-accent',
};

export function Badge({ tone = 'neutral', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-semibold', tones[tone], className)}>
      {children}
    </span>
  );
}

/** Estados de progreso de una lección, con texto (el color nunca es el único indicador). */
export const progressStates: Record<string, { label: string; tone: Tone }> = {
  NOT_STARTED: { label: 'Pendiente', tone: 'neutral' },
  IN_PROGRESS: { label: 'En curso', tone: 'warning' },
  PRACTICED: { label: 'Practicada', tone: 'primary' },
  MASTERED: { label: 'Dominada', tone: 'success' },
};

export function ProgressBadge({ state }: { state?: string }) {
  const s = progressStates[state ?? 'NOT_STARTED'] ?? progressStates.NOT_STARTED!;
  return <Badge tone={s.tone}>{s.label}</Badge>;
}

export function ProgressBar({ value, max, label }: { value: number; max: number; label: string }) {
  const pct = max > 0 ? Math.round((value / max) * 100) : 0;
  return (
    <div className="flex flex-col gap-1">
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={value}
        aria-valuetext={`${value} de ${max}`}
        className="h-2 w-full overflow-hidden rounded-full bg-surface-2"
      >
        <div className="h-full rounded-full bg-primary transition-[width] duration-300" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}
