import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderRoutes } from '@/test/render';
import { RequireAdmin, RequireAuth } from './guards';

afterEach(() => vi.unstubAllGlobals());

function stubMe(status: number, body?: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () =>
      body === undefined
        ? new Response(JSON.stringify({ code: 'UNAUTHENTICATED' }), { status })
        : new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }),
    ),
  );
}

const routes = [
  { path: '/login', element: <p>pantalla de login</p> },
  { path: '/app', element: <RequireAuth><p>panel del alumno</p></RequireAuth> },
  {
    path: '/admin',
    element: (
      <RequireAuth>
        <RequireAdmin>
          <p>panel de administración</p>
        </RequireAdmin>
      </RequireAuth>
    ),
  },
];

describe('guardas de ruta', () => {
  it('sin sesión redirige al login', async () => {
    stubMe(401);
    renderRoutes(routes, '/app');
    expect(await screen.findByText('pantalla de login')).toBeInTheDocument();
  });

  it('un estudiante no ve el panel de administración', async () => {
    stubMe(200, { email: 'ana@example.com', roles: ['STUDENT'], permissions: [] });
    renderRoutes(routes, '/admin');
    expect(await screen.findByText('panel del alumno')).toBeInTheDocument();
    expect(screen.queryByText('panel de administración')).not.toBeInTheDocument();
  });

  it('un administrador sí lo ve', async () => {
    stubMe(200, { email: 'owner@example.com', roles: ['ADMIN'], permissions: ['user:read:any'] });
    renderRoutes(routes, '/admin');
    expect(await screen.findByText('panel de administración')).toBeInTheDocument();
  });
});
