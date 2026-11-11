import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { renderPage } from '@/test/render';
import { ActivityChart } from '@/features/dashboard/ActivityChart';
import { formatInterval } from './api';
import { PracticePage } from './PracticePage';

afterEach(() => vi.unstubAllGlobals());

describe('formatInterval', () => {
  it('muestra días y meses', () => {
    expect(formatInterval(1)).toBe('1 día');
    expect(formatInterval(6)).toBe('6 días');
    expect(formatInterval(38)).toBe('1 mes');
    expect(formatInterval(95)).toBe('3 meses');
  });
});

const queue = {
  items: [
    {
      card: { id: 'c1', term: 'teacher', partOfSpeech: 'noun', ipa: '/ˈtiːtʃər/', translation: 'maestro', level: 'A1', state: 'NEW' },
      previews: [
        { quality: 1, intervalDays: 1 },
        { quality: 3, intervalDays: 1 },
        { quality: 4, intervalDays: 1 },
        { quality: 5, intervalDays: 1 },
      ],
    },
  ],
  dueCount: 0,
  newCount: 1,
  newAvailableToday: 10,
  stats: { total: 1 },
};

describe('PracticePage', () => {
  it('muestra la tarjeta, revela el significado y envía la calificación con un id de repaso', async () => {
    const fetchMock = vi.fn(async (input: Request | string) => {
      if (typeof input === 'string') {
        // Petición del token CSRF (URL relativa): el servidor real deja la cookie XSRF-TOKEN.
        document.cookie = 'XSRF-TOKEN=token-de-prueba';
        return new Response(null, { status: 204 });
      }
      const req = input;
      if (req.url.endsWith('/api/v1/vocabulary/queue')) {
        return new Response(JSON.stringify(queue), { headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ card: queue.items[0]!.card, quality: 4, replayed: false }), {
        headers: { 'Content-Type': 'application/json' },
      });
    });
    vi.stubGlobal('fetch', fetchMock);
    renderPage(<PracticePage />, '/');

    expect(await screen.findByText('teacher')).toBeInTheDocument();
    expect(screen.queryByText('maestro')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Mostrar significado' }));
    expect(screen.getByTestId('flashcard-answer')).toHaveTextContent('maestro');

    const group = screen.getByRole('group', { name: '¿Qué tan bien la recordaste?' });
    fireEvent.click(within(group).getByRole('button', { name: /Bien/ }));
    expect(await screen.findByText(/Repasaste 1 palabra/)).toBeInTheDocument();

    const post = fetchMock.mock.calls
      .map(([i]) => i)
      .find((r): r is Request => r instanceof Request && r.method === 'POST')!;
    expect(post.url).toContain('/api/v1/vocabulary/cards/c1/reviews');
    const body = (await post.clone().json()) as { quality: number; reviewId: string };
    expect(body.quality).toBe(4);
    expect(body.reviewId).toMatch(/.{8,}/);
  });
});

describe('ActivityChart', () => {
  const days = Array.from({ length: 14 }, (_, i) => ({
    date: `2026-11-${String(i + 1).padStart(2, '0')}`,
    exercises: i,
    reviews: 1,
    tutorMessages: 0,
    minutes: i === 13 ? 25 : 0,
  }));

  it('describe la gráfica, incluye una tabla equivalente y muestra el detalle al pasar el cursor', () => {
    renderPage(<ActivityChart days={days} goal={20} />);
    expect(screen.getByRole('img', { name: /meta diaria 20 minutos/ })).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(15); // encabezado + 14 días
    fireEvent.mouseEnter(screen.getByLabelText(/14 de noviembre: 25 minutos/));
    expect(screen.getByRole('tooltip')).toHaveTextContent('25 min (estimado)');
    expect(screen.getByRole('tooltip')).toHaveTextContent('13 ejercicios');
  });
});
