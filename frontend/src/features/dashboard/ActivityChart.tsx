import { useState } from 'react';
import type { Schemas } from '@/api/client';

type Day = Schemas['ActivityDay'];

const dayFmt = new Intl.DateTimeFormat('es-MX', { weekday: 'short', day: 'numeric', timeZone: 'UTC' });
const longFmt = new Intl.DateTimeFormat('es-MX', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });

function parseDay(iso?: string): Date {
  return new Date(`${iso ?? '1970-01-01'}T00:00:00Z`);
}

/** Paso "limpio" para el eje: 1, 2, 5, 10, 15, 20, 30, 60… */
function niceMax(v: number): number {
  for (const step of [5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240]) if (v <= step) return step;
  return Math.ceil(v / 60) * 60;
}

/**
 * Minutos de estudio estimados por día (una sola serie: sin leyenda, el título la nombra). Columnas de ≤ 24 px con
 * extremo redondeado de 4 px, cuadrícula recesiva, línea de meta, información al pasar el cursor y una tabla
 * equivalente para lectores de pantalla y para quien prefiera los números.
 */
export function ActivityChart({ days, goal }: { days: Day[]; goal: number }) {
  const [hover, setHover] = useState<number | null>(null);
  const W = 640;
  const H = 200;
  const pad = { top: 12, right: 8, bottom: 28, left: 32 };
  const max = niceMax(Math.max(goal, ...days.map((d) => d.minutes ?? 0), 1));
  const plotW = W - pad.left - pad.right;
  const plotH = H - pad.top - pad.bottom;
  const band = plotW / Math.max(days.length, 1);
  const barW = Math.min(24, band * 0.6);
  const y = (v: number) => pad.top + plotH - (v / max) * plotH;
  const ticks = [0, max / 2, max];
  const active = hover != null ? days[hover] : null;

  return (
    <div className="flex flex-col gap-2">
      <div className="relative">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          className="h-auto w-full"
          role="img"
          aria-label={`Minutos de estudio por día en los últimos ${days.length} días; meta diaria ${goal} minutos.`}
          onMouseLeave={() => setHover(null)}
        >
          {ticks.map((t) => (
            <g key={t}>
              <line x1={pad.left} x2={W - pad.right} y1={y(t)} y2={y(t)} className="stroke-border" strokeWidth={1} />
              <text x={pad.left - 6} y={y(t)} dy="0.32em" textAnchor="end" className="fill-muted text-[11px]">
                {t}
              </text>
            </g>
          ))}
          {goal > 0 && (
            <g>
              <line x1={pad.left} x2={W - pad.right} y1={y(goal)} y2={y(goal)} className="stroke-muted" strokeWidth={1} strokeDasharray="4 4" />
              <text x={W - pad.right} y={y(goal) - 4} textAnchor="end" className="fill-muted text-[11px]">
                Meta {goal} min
              </text>
            </g>
          )}
          {days.map((d, i) => {
            const v = d.minutes ?? 0;
            const cx = pad.left + band * i + band / 2;
            const top = y(v);
            const h = pad.top + plotH - top;
            const r = Math.min(4, h);
            const x0 = cx - barW / 2;
            const base = pad.top + plotH;
            const path =
              h > 0
                ? `M${x0},${base} V${top + r} Q${x0},${top} ${x0 + r},${top} H${x0 + barW - r} Q${x0 + barW},${top} ${x0 + barW},${top + r} V${base} Z`
                : '';
            const date = parseDay(d.date);
            return (
              // El detalle para teclado y lectores de pantalla está en la tabla equivalente ("Ver como tabla").
              <g key={d.date} onMouseEnter={() => setHover(i)} data-testid={`activity-day-${d.date}`}>
                {/* Zona de detección más grande que la columna. */}
                <rect x={pad.left + band * i} y={pad.top} width={band} height={plotH} fill="transparent" />
                {path && <path d={path} className={hover === i ? 'fill-primary-hover' : 'fill-primary'} />}
                {(i % 2 === days.length % 2 || i === days.length - 1) && (
                  <text x={cx} y={H - 8} textAnchor="middle" className="fill-muted text-[10px]">
                    {dayFmt.format(date).replace('.', '')}
                  </text>
                )}
              </g>
            );
          })}
          <line x1={pad.left} x2={W - pad.right} y1={pad.top + plotH} y2={pad.top + plotH} className="stroke-muted" strokeWidth={1} />
        </svg>
        {active && hover != null && (
          <div
            role="tooltip"
            className="pointer-events-none absolute top-0 z-10 w-max -translate-x-1/2 rounded-lg border border-border bg-surface px-3 py-2 text-xs shadow-lg"
            style={{ left: `${((pad.left + band * hover + band / 2) / W) * 100}%` }}
          >
            <p className="font-semibold capitalize">{longFmt.format(parseDay(active.date))}</p>
            <p>
              <strong className="font-mono">{active.minutes}</strong> min (estimado)
            </p>
            <p className="text-muted">
              {active.exercises} ejercicios · {active.reviews} repasos · {active.tutorMessages} mensajes
            </p>
          </div>
        )}
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer text-muted hover:text-text">Ver como tabla</summary>
        <table className="mt-2 w-full text-left">
          <thead className="text-xs text-muted">
            <tr>
              <th scope="col" className="py-1">Día</th>
              <th scope="col" className="py-1 text-right">Minutos</th>
              <th scope="col" className="py-1 text-right">Ejercicios</th>
              <th scope="col" className="py-1 text-right">Repasos</th>
              <th scope="col" className="py-1 text-right">Mensajes</th>
            </tr>
          </thead>
          <tbody>
            {days.map((d) => (
              <tr key={d.date} className="border-t border-border">
                <td className="py-1 capitalize">{dayFmt.format(parseDay(d.date))}</td>
                <td className="py-1 text-right font-mono">{d.minutes}</td>
                <td className="py-1 text-right font-mono">{d.exercises}</td>
                <td className="py-1 text-right font-mono">{d.reviews}</td>
                <td className="py-1 text-right font-mono">{d.tutorMessages}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}

/** Barras horizontales de una sola serie con el valor en la punta. */
export function CategoryBars({ data, labels }: { data: Record<string, number>; labels: Record<string, string> }) {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  const max = Math.max(1, ...entries.map(([, v]) => v));
  return (
    <ul className="flex flex-col gap-2" aria-label="Errores abiertos por categoría">
      {entries.map(([k, v]) => (
        <li key={k} className="grid grid-cols-[7.5rem_1fr] items-center gap-3 text-sm">
          <span className="truncate text-muted">{labels[k] ?? k}</span>
          <span className="flex items-center gap-2">
            <span className="h-4 rounded-r bg-primary" style={{ width: `${(v / max) * 85}%`, minWidth: 4 }} />
            <span className="font-mono text-xs">{v}</span>
          </span>
        </li>
      ))}
    </ul>
  );
}
