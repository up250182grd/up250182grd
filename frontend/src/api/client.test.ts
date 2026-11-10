import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, unwrap } from './client';

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('cliente de la API', () => {
  it('envía la cabecera X-XSRF-TOKEN en solicitudes que modifican estado', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/';
    const fetchMock = vi.fn(async (_req: Request) => jsonResponse(200, { verified: true }));
    vi.stubGlobal('fetch', fetchMock);

    await unwrap(api.POST('/api/v1/auth/verify-email', { body: { token: 't' } }));

    const request = fetchMock.mock.calls[0]?.[0] as Request;
    expect(request.method).toBe('POST');
    expect(request.headers.get('X-XSRF-TOKEN')).toBe('abc123');
  });

  it('pide la cookie CSRF antes del primer POST si aún no existe', async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: Request | string) => {
        const url = typeof input === 'string' ? input : input.url;
        calls.push(url);
        if (url.endsWith('/api/v1/auth/csrf')) {
          document.cookie = 'XSRF-TOKEN=fresh; path=/';
          return jsonResponse(200, { headerName: 'X-XSRF-TOKEN', token: 'fresh' });
        }
        return jsonResponse(202, {});
      }),
    );

    await unwrap(api.POST('/api/v1/auth/resend-verification', { body: { email: 'a@b.mx' } }));

    expect(calls[0]).toMatch(/\/api\/v1\/auth\/csrf$/);
    expect(calls[1]).toMatch(/resend-verification$/);
  });

  it('no envía la cabecera CSRF en GET', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/';
    const fetchMock = vi.fn(async (_req: Request) => jsonResponse(200, { email: 'a@b.mx' }));
    vi.stubGlobal('fetch', fetchMock);

    await unwrap(api.GET('/api/v1/me'));

    expect((fetchMock.mock.calls[0]?.[0] as Request).headers.get('X-XSRF-TOKEN')).toBeNull();
  });

  it('convierte Problem Details del backend en ApiError con código y errores por campo', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse(400, {
          code: 'VALIDATION_ERROR',
          detail: 'Hay datos inválidos en la solicitud.',
          errors: [{ field: 'password', message: 'muy corta' }],
        }),
      ),
    );

    const error = await unwrap(api.GET('/api/v1/me')).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_ERROR',
      message: 'Hay datos inválidos en la solicitud.',
      fieldErrors: [{ field: 'password', message: 'muy corta' }],
    });
  });

  it('reporta errores de red con un mensaje comprensible', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))));
    const error = await unwrap(api.GET('/api/v1/me')).catch((e: unknown) => e);
    expect(error).toMatchObject({ code: 'NETWORK_ERROR', status: 0 });
  });
});
