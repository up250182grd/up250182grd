import createClient, { type Middleware } from 'openapi-fetch';
import type { components, paths } from './schema';

export type Schemas = components['schemas'];

/** Error de la API con el `code` estable del backend (RFC 9457 Problem Details). */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(status: number, code: string, message: string, fieldErrors: { field: string; message: string }[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

let csrfRequest: Promise<void> | null = null;

/** Obtiene la cookie XSRF-TOKEN si aún no existe (una sola petición aunque haya varias en paralelo). */
async function ensureCsrfCookie(): Promise<void> {
  if (readCookie(CSRF_COOKIE)) return;
  csrfRequest ??= fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
    .then(() => undefined)
    .finally(() => {
      csrfRequest = null;
    });
  await csrfRequest;
}

const csrfMiddleware: Middleware = {
  async onRequest({ request }) {
    if (SAFE_METHODS.has(request.method)) return request;
    await ensureCsrfCookie();
    const token = readCookie(CSRF_COOKIE);
    if (token) request.headers.set(CSRF_HEADER, decodeURIComponent(token));
    return request;
  },
};

export const api = createClient<paths>({
  baseUrl: typeof window === 'undefined' ? '' : window.location.origin,
  credentials: 'same-origin',
  // Se resuelve en cada llamada (no al crear el cliente) para poder sustituir fetch en pruebas.
  fetch: (request) => globalThis.fetch(request),
});
api.use(csrfMiddleware);

/**
 * POST cuya respuesta es Server-Sent Events (p. ej. el chat con el profesor). Los errores previos al flujo llegan
 * como Problem Details y se lanzan como ApiError; luego cada evento se entrega a {@code onEvent}.
 */
export async function streamPost(
  path: string,
  body: unknown,
  onEvent: (event: string, data: unknown) => void,
  signal?: AbortSignal,
): Promise<void> {
  await ensureCsrfCookie();
  const token = readCookie(CSRF_COOKIE);
  let response: Response;
  try {
    response = await fetch(path, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { [CSRF_HEADER]: decodeURIComponent(token) } : {}),
      },
      body: JSON.stringify(body),
      signal,
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'No se pudo conectar con el servidor. Revisa tu conexión.');
  }
  if (!response.ok || !response.body) {
    const problem = (await response.json().catch(() => ({}))) as ProblemBody;
    throw new ApiError(
      response.status,
      problem.code ?? `HTTP_${response.status}`,
      problem.detail ?? 'Ocurrió un error inesperado.',
      problem.errors ?? [],
    );
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
    let sep: number;
    while ((sep = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      let event = 'message';
      const data: string[] = [];
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data.push(line.slice(5));
      }
      if (data.length) onEvent(event, JSON.parse(data.join('\n')));
    }
    if (done) break;
  }
}

/** Cierra la sesión. El endpoint lo gestiona Spring Security, por eso no forma parte del OpenAPI. */
export async function logoutRequest(): Promise<void> {
  await ensureCsrfCookie();
  const token = readCookie(CSRF_COOKIE);
  await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: token ? { [CSRF_HEADER]: decodeURIComponent(token) } : {},
  });
}

interface ProblemBody {
  code?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}

/**
 * Convierte la respuesta de openapi-fetch en datos o lanza ApiError. Así los componentes trabajan con
 * promesas normales (TanStack Query) y con mensajes de error en español que vienen del backend.
 */
export async function unwrap<T>(
  promise: Promise<{ data?: T; error?: unknown; response: Response }>,
): Promise<T> {
  let result: { data?: T; error?: unknown; response: Response };
  try {
    result = await promise;
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'No se pudo conectar con el servidor. Revisa tu conexión.');
  }
  const { data, error, response } = result;
  if (response.ok) return data as T;
  const problem = (error ?? {}) as ProblemBody;
  throw new ApiError(
    response.status,
    problem.code ?? `HTTP_${response.status}`,
    problem.detail ?? 'Ocurrió un error inesperado.',
    problem.errors ?? [],
  );
}
