import { expect, type Page } from '@playwright/test';

export const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';
export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'owner@englishos.local';
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Local-Sunrise-Phrase-2026';
export const PASSWORD = 'Correct-Horse-Battery-42';

export function uniqueEmail(prefix = 'e2e'): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
}

/** Busca en Mailpit el último correo enviado a `email` y devuelve el enlace con token que contiene. */
export async function linkFromEmail(email: string, path: string): Promise<string> {
  let link: string | undefined;
  await expect
    .poll(
      async () => {
        const search = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
        const { messages } = (await search.json()) as { messages: { ID: string }[] };
        if (!messages.length) return undefined;
        const msg = await fetch(`${MAILPIT}/api/v1/message/${messages[0]!.ID}`);
        const { Text } = (await msg.json()) as { Text: string };
        link = Text.match(new RegExp(`https?://\\S+${path}\\?token=\\S+`))?.[0];
        return link;
      },
      { timeout: 15_000, message: `correo con enlace ${path} para ${email}` },
    )
    .toBeTruthy();
  return link!;
}

export async function register(page: Page, email: string, name = 'Ana E2E') {
  await page.goto('/register');
  await page.getByLabel('Nombre').fill(name);
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('checkbox', { name: /términos de uso/i }).check();
  await page.getByRole('checkbox', { name: /aviso de privacidad/i }).check();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('heading', { name: 'Revisa tu correo' })).toBeVisible();
}

export async function login(page: Page, email: string, password = PASSWORD) {
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page).toHaveURL(/\/app$/);
}
