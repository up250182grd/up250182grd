import { expect, test } from '@playwright/test';
import { completeVerbToBe, login, register, uniqueEmail } from './support';

test('alumno: terminar una lección da XP, cuenta para la racha y otorga su primera insignia', async ({ page }) => {
  const email = uniqueEmail('logros');
  await register(page, email);
  await login(page, email);

  // Sin actividad: racha 0 y ninguna insignia.
  const strip = page.getByTestId('achievements-strip');
  await expect(strip).toContainText('0 días');
  await expect(strip).toContainText('0 de 9');

  await completeVerbToBe(page);

  await page.getByRole('link', { name: 'Inicio' }).first().click();
  await expect(strip).toContainText('1 día');
  await expect(strip).toContainText('Hoy ya cuenta');

  await strip.click();
  await expect(page.getByRole('heading', { name: 'Logros' })).toBeVisible();
  await expect(page.getByTestId('xp-total')).toHaveText('20');
  await expect(page.getByTestId('streak-current')).toHaveText('1 día');
  await expect(page.getByText('¡Hoy ya cuenta para tu racha!')).toBeVisible();
  await expect(page.getByTestId('badge-FIRST_LESSON')).toContainText('Obtenida');
  await expect(page.getByTestId('badge-MASTERED_5')).toContainText('Progreso: 0 / 5');
  await expect(page.getByText('Lección terminada')).toBeVisible();
  // El calendario explica cada día también a lectores de pantalla.
  await expect(page.getByRole('list', { name: 'Últimas 4 semanas' }).getByText(/: Día con estudio/)).toHaveCount(1);
});
