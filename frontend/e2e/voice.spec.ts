import { expect, test } from '@playwright/test';
import { verifiedStudent } from './support';

function mxn(text: string | null): number {
  return Number((text ?? '').replace(/[^0-9.,-]/g, '').replace(/,/g, ''));
}

test('alumno: llamada de voz simulada con medidor en vivo, colgar y cobro con desglose', async ({ page }) => {
  await verifiedStudent(page, 'voz');

  // Sin saldo: la cotización lo explica y no deja iniciar.
  await page.getByRole('link', { name: 'Voz' }).first().click();
  await expect(page.getByTestId('voice-quote')).toContainText('por minuto');
  await expect(page.getByTestId('voice-quote')).toContainText('Saldo o límite mensual insuficientes');
  await expect(page.getByRole('button', { name: 'Iniciar llamada' })).toBeDisabled();

  await page.getByRole('link', { name: 'Billetera' }).first().click();
  await page.getByLabel('Monto (MXN)').fill('200');
  await page.getByRole('button', { name: 'Recargar' }).click();
  await expect(page.getByTestId('wallet-available')).toHaveText(/200\.00/);

  await page.getByRole('link', { name: 'Voz' }).first().click();
  await expect(page.getByText('Simulado (sin audio)')).toBeVisible();
  await expect(page.getByText('Duración máxima de esta llamada: 15 minutos.')).toBeVisible();
  await page.getByText('Roleplay', { exact: true }).click();
  await page.getByLabel('Tema o situación (opcional)').fill('job interview');
  await page.getByRole('button', { name: 'Iniciar llamada' }).click();

  // Llamada claramente marcada como simulada; transcripción y medidor llegan del servidor.
  await expect(page.getByText('Llamada simulada (sin audio).')).toBeVisible();
  await expect(page.getByTestId('voice-timer')).toContainText('/ 15:00');
  await expect(page.getByText(/simulated call/).first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId('voice-measured')).not.toHaveText('—');

  await page.getByRole('button', { name: 'Colgar' }).click();
  await expect(page.getByRole('heading', { name: 'Resumen de la llamada' })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText('Servicio (30 %)')).toBeVisible();
  const total = mxn(await page.getByTestId('voice-settlement-total').textContent());
  expect(total).toBeGreaterThan(0);
  await expect(page.getByText(/simulated call/).first()).toBeVisible(); // transcripción guardada

  // La llamada aparece en el historial y la billetera refleja solo lo consumido.
  await page.getByRole('button', { name: 'Hacer otra llamada' }).click();
  await expect(page.getByRole('link', { name: /job interview/ })).toBeVisible();
  await page.getByRole('link', { name: 'Billetera' }).first().click();
  await expect
    .poll(async () => mxn(await page.getByTestId('wallet-available').textContent()))
    .toBeCloseTo(200 - total, 2);
  await expect(page.getByText(/llamada con el profesor IA/i).first()).toBeVisible();
});
