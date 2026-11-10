import { expect, test } from '@playwright/test';
import { login, register, uniqueEmail } from './support';

test('en móvil la navegación usa barra inferior y no hay desplazamiento horizontal', async ({ page }) => {
  const email = uniqueEmail('movil');
  await register(page, email);
  await login(page, email);

  const bottomNav = page.getByRole('navigation', { name: 'Principal' }).last();
  await expect(bottomNav).toBeVisible();
  await expect(bottomNav.getByRole('link', { name: 'Inicio' })).toBeVisible();

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});
