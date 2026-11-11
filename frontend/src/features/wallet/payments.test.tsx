import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderRoutes } from '@/test/render';
import { GatewayTopupCard, TopupReturnPage } from './payments';

afterEach(() => vi.unstubAllGlobals());

function stubFetch(handler: (req: Request) => unknown) {
  const fetchMock = vi.fn(async (input: Request | string) => {
    if (typeof input === 'string') {
      document.cookie = 'XSRF-TOKEN=token-de-prueba';
      return new Response(null, { status: 204 });
    }
    return new Response(JSON.stringify(handler(input)), { headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const topup = (over: Record<string, unknown>) => ({
  id: 'T1',
  amountMxn: 200,
  currency: 'MXN',
  status: 'CREATED',
  credited: false,
  refundedMxn: 0,
  ...over,
});

const route = [{ path: '/app/wallet/topups/:topupId', element: <TopupReturnPage /> }];

describe('TopupReturnPage', () => {
  it('al volver sin confirmación de la pasarela NO anuncia saldo acreditado', async () => {
    stubFetch(() => topup({ status: 'CREATED' }));
    renderRoutes(route, '/app/wallet/topups/T1');
    expect(await screen.findByText('Esperando la confirmación de Mercado Pago…')).toBeInTheDocument();
    expect(screen.queryByText(/Acreditamos/)).not.toBeInTheDocument();
  });

  it('muestra el crédito solo cuando el servidor lo confirma', async () => {
    stubFetch(() => topup({ status: 'APPROVED', credited: true }));
    renderRoutes(route, '/app/wallet/topups/T1');
    expect(await screen.findByText(/Acreditamos \$200\.00 a tu saldo/)).toBeInTheDocument();
  });

  it('explica rechazos y revisiones sin culpar al alumno', async () => {
    stubFetch(() => topup({ status: 'REVIEW' }));
    renderRoutes(route, '/app/wallet/topups/T1');
    expect(await screen.findByText('Estamos revisando tu pago.')).toBeInTheDocument();
    expect(screen.getByText(/no tienes que hacer nada/)).toBeInTheDocument();
  });
});

describe('GatewayTopupCard', () => {
  it('crea la recarga con clave de idempotencia y redirige a la URL de la pasarela', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign });
    const fetchMock = stubFetch(() => topup({ checkoutUrl: 'https://sandbox.mercadopago.com.mx/checkout?pref=1' }));
    renderRoutes([{ path: '/', element: <GatewayTopupCard min={100} max={2000} testMode /> }], '/');
    expect(screen.getByText('Modo prueba: sin cobros reales')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Monto (MXN)'), { target: { value: '250' } });
    fireEvent.click(screen.getByRole('button', { name: 'Pagar con Mercado Pago' }));
    await vi.waitFor(() => expect(assign).toHaveBeenCalledWith('https://sandbox.mercadopago.com.mx/checkout?pref=1'));
    const post = fetchMock.mock.calls.map(([i]) => i).find((r): r is Request => r instanceof Request && r.method === 'POST')!;
    const body = (await post.clone().json()) as { amount: number; idempotencyKey: string };
    expect(body.amount).toBe(250);
    expect(body.idempotencyKey.length).toBeGreaterThanOrEqual(8);
  });
});
