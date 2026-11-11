import { expect, test, type Page } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, login, verifiedStudent } from './support';

function mxn(text: string | null): number {
  return Number((text ?? '').replace(/[^0-9.,-]/g, '').replace(/,/g, ''));
}

async function chat(page: Page, text: string) {
  await page.getByLabel('Tu mensaje en inglés').fill(text);
  await page.getByRole('button', { name: 'Enviar' }).click();
  await expect(page.getByRole('button', { name: 'Enviar' })).not.toHaveAttribute('aria-busy', 'true');
}

test('alumno: recarga simulada, conversación con correcciones y cobro con desglose', async ({ page }) => {
  await verifiedStudent(page, 'tutor');

  // Sin saldo no puede empezar: mensaje claro con enlace a la billetera.
  await page.getByRole('link', { name: 'Profesor IA' }).first().click();
  await page.getByRole('button', { name: 'Empezar conversación' }).click();
  await expect(page.getByRole('alert')).toContainText('Saldo insuficiente');

  await page.getByRole('link', { name: 'Ir a la billetera' }).click();
  await expect(page.getByText('Modo prueba')).toBeVisible();
  await page.getByLabel('Monto (MXN)').fill('200');
  await page.getByRole('button', { name: 'Recargar' }).click();
  await expect(page.getByTestId('wallet-available')).toHaveText(/200\.00/);

  await page.getByRole('link', { name: 'Profesor IA' }).first().click();
  await page.getByText('Roleplay', { exact: true }).click();
  await page.getByLabel('Tema o situación (opcional)').fill('ordering food at a restaurant');
  await page.getByRole('button', { name: 'Empezar conversación' }).click();

  await expect(page.getByText('Profesor simulado')).toBeVisible();
  await chat(page, 'Hello! She have a table for two.');
  // Respuesta en streaming, sin el bloque interno de correcciones.
  await expect(page.getByText(/simulated tutor/).first()).toBeVisible();
  await expect(page.getByText('<<<CORRECTIONS>>>')).toHaveCount(0);
  await expect(page.getByText('“She has”', { exact: true })).toBeVisible();
  await expect(page.getByText('Correcciones (1)')).toBeVisible();
  await expect(page.getByTestId('measured-cost')).not.toHaveText('—');

  await chat(page, 'I would like the soup, please.');
  await page.getByRole('button', { name: 'Terminar conversación' }).click();
  await expect(page.getByRole('heading', { name: 'Resumen de la sesión' })).toBeVisible();
  await expect(page.getByText('Servicio (30 %)')).toBeVisible();
  const total = mxn(await page.getByTestId('settlement-total').textContent());
  expect(total).toBeGreaterThan(0);

  await page.getByRole('link', { name: 'Billetera' }).first().click();
  // Se cobró solo lo consumido y se liberó el resto de la reserva (espera a que la vista se actualice).
  await expect
    .poll(async () => mxn(await page.getByTestId('wallet-available').textContent()))
    .toBeCloseTo(200 - total, 2);
  await expect(page.getByText('Conversación con el profesor IA').first()).toBeVisible();
  await expect(page.getByText('Cargo', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Liberación', { exact: true }).first()).toBeVisible();
});

test('administrador: su consumo se registra al costo y ve las finanzas', async ({ page }) => {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  await page.getByRole('link', { name: 'Profesor IA' }).first().click();
  await expect(page.getByText(/al costo \(sin recargo\)/)).toBeVisible();
  await page.getByRole('button', { name: 'Empezar conversación' }).click();
  await chat(page, 'Can we practise the past simple?');
  await page.getByRole('button', { name: 'Terminar conversación' }).click();
  await expect(page.getByText('Recargo (admin al costo)')).toBeVisible();

  await page.getByRole('link', { name: 'Finanzas' }).first().click();
  await expect(page.getByText('Ingreso bruto adicional (recargo)')).toBeVisible();
  await expect(page.getByText('El libro contable no cuadra')).toHaveCount(0);
  await page.getByRole('tab', { name: 'Mi consumo' }).click();
  await expect(page.getByText('×1.00').first()).toBeVisible();
  await page.getByRole('tab', { name: 'Política comercial' }).click();
  await expect(page.getByText('×1.30')).toBeVisible();
  await page.getByRole('tab', { name: 'Modelos y tarifas' }).click();
  await expect(page.getByText('fake-tutor-1')).toBeVisible();
});
