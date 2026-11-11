import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, streamPost } from './client';

afterEach(() => vi.unstubAllGlobals());

function sseResponse(chunks: string[]) {
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
      chunks.forEach((c) => controller.enqueue(encoder.encode(c)));
      controller.close();
    },
  });
  return new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
}

describe('streamPost', () => {
  it('entrega los eventos aunque lleguen partidos entre fragmentos y envía la cabecera CSRF', async () => {
    document.cookie = 'XSRF-TOKEN=tok; path=/';
    const fetchMock = vi.fn(async () =>
      sseResponse(['event:delta\ndata:{"text":"Hel', 'lo"}\n\nevent:delta\ndata:{"text":" there"}\n\n', 'event:done\ndata:{"turns":1}\n\n']),
    );
    vi.stubGlobal('fetch', fetchMock);
    const events: [string, unknown][] = [];

    await streamPost('/api/x', { text: 'hi' }, (e, d) => events.push([e, d]));

    expect(events).toEqual([
      ['delta', { text: 'Hello' }],
      ['delta', { text: ' there' }],
      ['done', { turns: 1 }],
    ]);
    const init = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1];
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok');
  });

  it('los errores previos al flujo llegan como ApiError con su código', async () => {
    document.cookie = 'XSRF-TOKEN=tok; path=/';
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ code: 'INSUFFICIENT_FUNDS', detail: 'Saldo insuficiente' }), { status: 402 }),
      ),
    );
    const err = await streamPost('/api/x', {}, () => undefined).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 402, code: 'INSUFFICIENT_FUNDS' });
  });
});
