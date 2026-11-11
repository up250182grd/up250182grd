import { expect, test } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, completeVerbToBe, login, register, uniqueEmail } from './support';


test('alumno: recomendaciones reales, vocabulario de la lección al repaso SM-2 y progreso', async ({ page }) => {
  const email = uniqueEmail('repaso');
  await register(page, email);
  await login(page, email);

  // Alumno nuevo: el motor de reglas pone primero el diagnóstico; el repaso aún está vacío.
  const recs = page.getByTestId('recommendations');
  await expect(recs.getByRole('link').first()).toContainText('Haz tu diagnóstico');
  await page.getByRole('link', { name: 'Repaso' }).first().click();
  await expect(page.getByText('Aún no tienes palabras para repasar')).toBeVisible();

  await completeVerbToBe(page);

  // Las palabras de la lección llegan como tarjetas nuevas.
  await page.getByRole('link', { name: 'Repaso' }).first().click();
  const card = page.getByTestId('flashcard');
  await expect(card).toBeVisible();
  await expect(page.getByText(/Tarjeta 1 de 4/)).toBeVisible();
  await page.getByRole('button', { name: 'Mostrar significado' }).click();
  await expect(page.getByTestId('flashcard-answer')).not.toBeEmpty();
  await page.getByRole('group', { name: '¿Qué tan bien la recordaste?' }).getByRole('button', { name: /Bien/ }).click();
  await expect(page.getByText(/Tarjeta 2 de 4/)).toBeVisible();
  // Atajos de teclado: espacio muestra, 4 = fácil.
  await page.keyboard.press(' ');
  await page.keyboard.press('4');
  await expect(page.getByText(/Tarjeta 3 de 4/)).toBeVisible();

  // Mis palabras: dos repasadas (aprendiendo) y dos nuevas.
  await page.getByRole('link', { name: 'Ver mis palabras' }).click();
  await expect(page.getByRole('cell', { name: /teacher/ })).toBeVisible();
  await page.getByRole('button', { name: 'Aprendiendo' }).click();
  await expect(page.getByRole('row')).toHaveCount(3); // encabezado + 2

  // Errores: sin conversaciones con el profesor, no hay pendientes.
  await page.goto('/app/practice?tab=errors');
  await expect(page.getByText('No tienes errores pendientes')).toBeVisible();

  // Tablero con datos reales: palabras pendientes recomendadas, gráfica y estadísticas.
  await page.getByRole('link', { name: 'Inicio' }).first().click();
  await expect(recs).toContainText('palabras nuevas');
  await expect(page.getByRole('img', { name: /Minutos de estudio por día/ })).toBeVisible();
  await expect(page.getByTestId('vocabulary-stats')).toContainText('4');
  await page.getByText('Ver como tabla').click();
  await expect(page.getByRole('row')).toHaveCount(15);
});

test('administrador: pesos del recomendador versionados con motivo', async ({ page }) => {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await page.getByRole('link', { name: 'Recomendaciones' }).first().click();
  await expect(page.getByText('Vigente')).toBeVisible();
  const save = page.getByRole('button', { name: 'Guardar nueva versión' });
  await expect(save).toBeDisabled(); // el motivo es obligatorio
  await page.getByLabel('Motivo del cambio').fill('Prueba E2E: guardar sin cambios');
  await save.click();
  await expect(page.getByText('Pesos guardados como versión nueva')).toBeVisible();
  await expect(page.getByText('Prueba E2E: guardar sin cambios').first()).toBeVisible();
});
