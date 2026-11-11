import { expect, test } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, login, verifiedStudent } from './support';

test('alumno: sin pasarela configurada, la billetera lo dice y ofrece solo la recarga de prueba', async ({ page }) => {
  await verifiedStudent(page, 'pagos');
  await page.getByRole('link', { name: 'Billetera' }).first().click();
  await expect(page.getByText('Modo prueba')).toBeVisible(); // recarga simulada (entorno de desarrollo)
  await expect(page.getByRole('button', { name: 'Pagar con Mercado Pago' })).toHaveCount(0);
});

test('administrador: recargas, reportes CSV y auditoría consultable', async ({ page }) => {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await page.getByRole('link', { name: 'Finanzas' }).first().click();

  // Reportes del mes: el libro mayor se descarga como CSV con encabezados en español.
  await expect(page.getByRole('heading', { name: 'Reportes (CSV)' })).toBeVisible();
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('link', { name: 'Libro mayor' }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/^libro-mayor-\d{4}-\d{2}\.csv$/);
  const text = await (await download.createReadStream())!.toArray().then((c) => Buffer.concat(c).toString('utf8'));
  expect(text).toContain('fecha_utc,asiento,tipo');

  await page.getByRole('tab', { name: 'Recargas' }).click();
  await expect(page.getByText(/La pasarela no está configurada/)).toBeVisible();

  // Auditoría: filtrar por una acción conocida.
  await page.getByRole('link', { name: 'Auditoría' }).first().click();
  await expect(page.getByRole('heading', { name: 'Auditoría' })).toBeVisible();
  await page.getByLabel('Acción').selectOption('WALLET_SIMULATED_TOPUP');
  await page.getByRole('button', { name: 'Filtrar' }).click();
  await expect(page.getByRole('list', { name: 'Registros de auditoría' }).getByText('WALLET_SIMULATED_TOPUP').first()).toBeVisible();
});
