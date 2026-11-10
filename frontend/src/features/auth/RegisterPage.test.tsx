import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderPage } from '@/test/render';
import { RegisterPage } from './RegisterPage';

afterEach(() => vi.unstubAllGlobals());

function stubBackend(handler: (url: string, body: unknown) => Response) {
  document.cookie = 'XSRF-TOKEN=t; path=/';
  const fetchMock = vi.fn(async (req: Request) => handler(req.url, await req.clone().json().catch(() => null)));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function fillValidForm(password = 'Correct-Horse-Battery-42') {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Nombre'), 'Ana');
  await user.type(screen.getByLabelText('Correo electrónico'), 'ana@example.com');
  await user.type(screen.getByLabelText('Contraseña'), password);
  await user.click(screen.getByRole('checkbox', { name: /términos de uso/i }));
  await user.click(screen.getByRole('checkbox', { name: /aviso de privacidad/i }));
  return user;
}

describe('RegisterPage', () => {
  it('valida en el cliente antes de llamar al backend', async () => {
    const fetchMock = stubBackend(() => new Response(null, { status: 500 }));
    renderPage(<RegisterPage />, '/register');
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Contraseña'), 'corta');
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByText('Escribe tu nombre.')).toBeInTheDocument();
    expect(screen.getByText(/al menos 12 caracteres/)).toBeInTheDocument();
    expect(screen.getByText('Debes aceptar los términos de uso.')).toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('aria-invalid', 'true');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('muestra junto al campo el rechazo de contraseña común que decide el backend', async () => {
    stubBackend(
      () =>
        new Response(JSON.stringify({ code: 'WEAK_PASSWORD', detail: 'Esa contraseña es demasiado común. Elige otra.' }), {
          status: 400,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
    );
    renderPage(<RegisterPage />, '/register');
    const user = await fillValidForm('password1234');

    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByText('Esa contraseña es demasiado común. Elige otra.')).toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña')).toHaveAttribute('aria-invalid', 'true');
  });

  it('confirma el registro y pide revisar el correo', async () => {
    const fetchMock = stubBackend(
      () =>
        new Response(JSON.stringify({ publicId: '01J', email: 'ana@example.com', emailVerificationRequired: true }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        }),
    );
    renderPage(<RegisterPage />, '/register');
    const user = await fillValidForm();

    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByRole('heading', { name: 'Revisa tu correo' })).toBeInTheDocument();
    const sent = await (fetchMock.mock.calls[0]?.[0] as Request).clone().json();
    expect(sent).toMatchObject({ email: 'ana@example.com', acceptTerms: true, acceptPrivacy: true });
  });
});
