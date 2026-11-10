import { expect, test } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, linkFromEmail, login, register, uniqueEmail } from './support';

test('un estudiante se registra, confirma su correo, inicia sesión, edita su perfil y sale', async ({ page }) => {
  const email = uniqueEmail('alumno');
  await register(page, email, 'Ana E2E');

  // El enlace del correo apunta a APP_BASE_URL; se abre en el mismo origen de la prueba.
  const link = new URL(await linkFromEmail(email, '/verify-email'));
  await page.goto(`/verify-email${link.search}`);
  await expect(page.getByText('¡Correo confirmado!')).toBeVisible();

  await login(page, email);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Ana E2E');
  await expect(page.getByText('Confirma tu correo')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Tu nivel estimado' })).toBeVisible();

  await page.getByRole('link', { name: 'Ajustes' }).click();
  await page.getByLabel('Idioma de las explicaciones').selectOption('en');
  await page.getByLabel('Meta diaria (minutos)').fill('35');
  await page.getByRole('button', { name: 'Guardar cambios' }).click();
  await expect(page.getByText('Cambios guardados.')).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Meta diaria (minutos)')).toHaveValue('35');

  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/app');
  await expect(page).toHaveURL(/\/login$/);
});

test('credenciales incorrectas muestran un mensaje claro', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(uniqueEmail('nadie'));
  await page.getByLabel('Contraseña').fill('Wrong-Password-1234');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('alert')).toContainText('Correo o contraseña incorrectos.');
});

test('recuperación de contraseña de punta a punta', async ({ page }) => {
  const email = uniqueEmail('olvido');
  await register(page, email);

  await page.goto('/forgot-password');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByRole('button', { name: 'Enviar enlace' }).click();
  await expect(page.getByText('Revisa tu correo')).toBeVisible();

  const link = new URL(await linkFromEmail(email, '/reset-password'));
  await page.goto(`/reset-password${link.search}`);
  const newPassword = 'Another-Long-Phrase-2026';
  await page.getByLabel('Nueva contraseña').fill(newPassword);
  await page.getByLabel('Repite la contraseña').fill(newPassword);
  await page.getByRole('button', { name: 'Guardar contraseña' }).click();
  await expect(page.getByText('Contraseña actualizada')).toBeVisible();

  await login(page, email, newPassword);
});

test('el administrador gestiona usuarios y un estudiante no puede entrar al panel', async ({ browser }) => {
  const studentEmail = uniqueEmail('suspendible');
  const studentPage = await (await browser.newContext()).newPage();
  await register(studentPage, studentEmail);
  await login(studentPage, studentEmail);
  await expect(studentPage.getByRole('link', { name: 'Usuarios' })).toHaveCount(0);
  await studentPage.goto('/admin/users');
  await expect(studentPage).toHaveURL(/\/app$/);

  const adminPage = await (await browser.newContext()).newPage();
  await login(adminPage, ADMIN_EMAIL, ADMIN_PASSWORD);
  await adminPage.getByRole('link', { name: 'Usuarios' }).click();
  await adminPage.getByLabel('Buscar por correo').fill(studentEmail);
  await adminPage.getByRole('button', { name: 'Buscar' }).click();
  const row = adminPage.getByRole('row', { name: new RegExp(studentEmail) });
  await expect(row).toBeVisible();

  adminPage.once('dialog', (d) => d.accept());
  await row.getByRole('button', { name: 'Suspender' }).click();
  await expect(row.getByText('Suspendida')).toBeVisible();

  // La sesión del estudiante se revoca en el servidor de inmediato.
  await studentPage.reload();
  await expect(studentPage).toHaveURL(/\/login$/);

  adminPage.once('dialog', (d) => d.accept());
  await row.getByRole('button', { name: 'Reactivar' }).click();
  await expect(row.getByText('Activa')).toBeVisible();
});
