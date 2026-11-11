import { expect, test } from '@playwright/test';
import { login, PASSWORD, register, uniqueEmail } from './support';

test('los documentos legales se pueden leer antes de aceptarlos', async ({ page }) => {
  await page.goto('/register');
  const [legal] = await Promise.all([
    page.context().waitForEvent('page'),
    page.getByRole('link', { name: 'aviso de privacidad' }).click(),
  ]);
  await expect(legal.getByRole('heading', { name: 'Aviso de privacidad', level: 1 })).toBeVisible();
  await expect(legal.getByText('El audio de las llamadas nunca se guarda.')).toBeVisible();
  await expect(legal.getByText(/BORRADOR — NO VIGENTE/)).toBeVisible();
  await legal.getByRole('link', { name: 'Política comercial y de precios' }).click();
  await expect(legal.getByText(/30 % de servicio/).first()).toBeVisible();
});

test('alumno: descarga sus datos y elimina su cuenta; ya no puede entrar', async ({ page }) => {
  const email = uniqueEmail('borrar');
  await register(page, email);
  await login(page, email);
  await page.getByRole('link', { name: 'Ajustes' }).first().click();

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('link', { name: 'Descargar mis datos' }).click(),
  ]);
  const json = JSON.parse(
    await (await download.createReadStream())!.toArray().then((c) => Buffer.concat(c).toString('utf8')),
  ) as { identidad: { cuenta: { email: string }[] } };
  expect(json.identidad.cuenta[0]!.email).toBe(email);

  await page.getByRole('button', { name: 'Eliminar mi cuenta…' }).click();
  const confirm = page.getByRole('button', { name: 'Eliminar definitivamente' });
  await page.getByLabel('Tu contraseña').fill(PASSWORD);
  await expect(confirm).toBeDisabled();
  await page.getByLabel('Escribe "ELIMINAR" para confirmar').fill('ELIMINAR');
  await confirm.click();
  await expect(page).toHaveURL(/\/$/);

  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill(PASSWORD);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});
