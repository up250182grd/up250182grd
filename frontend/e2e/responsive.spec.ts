import { expect, test } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, login, register, uniqueEmail } from './support';

async function overflowOf(page: import('@playwright/test').Page) {
  return page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
}

test('en móvil la navegación usa barra inferior y no hay desplazamiento horizontal', async ({ page }) => {
  const email = uniqueEmail('movil');
  await register(page, email);
  await login(page, email);

  const bottomNav = page.getByRole('navigation', { name: 'Principal' }).last();
  await expect(bottomNav).toBeVisible();
  await expect(bottomNav.getByRole('link', { name: 'Inicio' })).toBeVisible();

  // Ninguna pantalla del alumno se desborda a lo ancho en un teléfono.
  const wide: string[] = [];
  for (const path of [
    '/app',
    '/app/plan',
    '/app/practice',
    '/app/practice?tab=errors',
    '/app/vocabulary',
    '/app/tutor',
    '/app/voice',
    '/app/wallet',
    '/app/achievements',
    '/app/settings',
  ]) {
    await page.goto(path);
    await page.getByRole('heading', { level: 1 }).first().waitFor();
    const o = await overflowOf(page);
    if (o > 0) wide.push(`${path} (+${o}px)`);
  }
  expect(wide, wide.join(', ')).toEqual([]);
});

test('en móvil las pantallas de administración no se desbordan', async ({ page }) => {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  const wide: string[] = [];
  for (const path of ['/admin/users', '/admin/content', '/admin/billing', '/admin/recommendations', '/admin/audit']) {
    await page.goto(path);
    await page.getByRole('heading', { level: 1 }).first().waitFor();
    const o = await overflowOf(page);
    if (o > 0) wide.push(`${path} (+${o}px)`);
  }
  expect(wide, wide.join(', ')).toEqual([]);
});
