import type { ReactNode } from 'react';
import { AlertCircle, CheckCircle2, Info } from 'lucide-react';
import { cn } from '@/lib/cn';

type Tone = 'info' | 'success' | 'error';

const tones: Record<Tone, { cls: string; icon: ReactNode }> = {
  info: { cls: 'border-primary/40 bg-primary/10', icon: <Info aria-hidden className="size-5 text-primary" /> },
  success: {
    cls: 'border-success/40 bg-success/10',
    icon: <CheckCircle2 aria-hidden className="size-5 text-success" />,
  },
  error: { cls: 'border-danger/40 bg-danger/10', icon: <AlertCircle aria-hidden className="size-5 text-danger" /> },
};

/** Mensaje de estado. Los errores se anuncian a lectores de pantalla (role=alert). */
export function Alert({ tone = 'info', title, children }: { tone?: Tone; title?: string; children?: ReactNode }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-lg border p-3 text-sm text-text', tones[tone].cls)}
    >
      <span className="mt-0.5 shrink-0">{tones[tone].icon}</span>
      <div className="flex flex-col gap-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div>{children}</div>}
      </div>
    </div>
  );
}
