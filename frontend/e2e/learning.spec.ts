import { expect, test, type Page } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, login, register, uniqueEmail } from './support';

async function newStudent(page: Page) {
  const email = uniqueEmail('fase3');
  await register(page, email);
  await login(page, email);
}

async function check(page: Page) {
  await page.getByRole('button', { name: 'Comprobar' }).click();
}

test('lección completa: explicación bilingüe, práctica con corrección y resultado', async ({ page }) => {
  await newStudent(page);
  await page.getByRole('link', { name: 'Mi plan' }).first().click();
  await page.getByRole('link', { name: /El verbo to be/ }).click();

  // Explicación en español por defecto y cambio instantáneo a inglés.
  await expect(page.getByRole('heading', { name: 'Explicación' })).toBeVisible();
  await page.getByRole('button', { name: 'English' }).click();
  await expect(page.getByRole('heading', { name: 'Explanation' })).toBeVisible();
  await expect(page.getByText('What is to be?')).toBeVisible();
  await page.getByRole('button', { name: 'Español' }).click();

  await page.getByRole('button', { name: /Empezar práctica/ }).click();

  // 1. Opción múltiple: primero un error para ver la corrección, luego reintento.
  await page.getByLabel('am', { exact: true }).check();
  await check(page);
  await expect(page.getByText('No es correcto')).toBeVisible();
  await expect(page.getByText('Respuesta correcta:')).toBeVisible();
  await page.getByRole('button', { name: 'Intentar de nuevo' }).click();
  await page.getByLabel('is', { exact: true }).check();
  await check(page);
  await expect(page.getByText('¡Correcto!')).toBeVisible();
  await page.getByRole('button', { name: 'Siguiente' }).click();

  // 2. Completar.
  await page.getByLabel('Espacio 1').fill('am');
  await check(page);
  await page.getByRole('button', { name: 'Siguiente' }).click();

  // 3. Completar dos huecos.
  await page.getByLabel('Espacio 1').fill('are');
  await page.getByLabel('Espacio 2').fill('Are');
  await check(page);
  await expect(page.getByText('¡Correcto!')).toBeVisible();
  await page.getByRole('button', { name: 'Siguiente' }).click();

  // 4. Verdadero o falso.
  await page.getByText('Falso · False').click();
  await check(page);
  await page.getByRole('button', { name: 'Siguiente' }).click();

  // 5. Ordenar palabras.
  for (const word of ['Where', 'are', 'you', 'from', '?']) {
    await page.getByLabel('Palabras disponibles').getByRole('button', { name: word, exact: true }).click();
  }
  await check(page);
  await expect(page.getByText('¡Correcto!')).toBeVisible();
  await page.getByRole('button', { name: 'Siguiente' }).click();

  // 6. Identificar el error.
  await page.getByRole('radio', { name: 'is' }).click();
  await check(page);
  await page.getByRole('button', { name: 'Terminar práctica' }).click();

  // El primer intento del ejercicio 1 fue incorrecto: 5 de 6 → 83 %, practicada (no dominada el mismo día).
  await expect(page.getByText('5 de 6 correctos al primer intento')).toBeVisible();
  await expect(page.getByRole('heading', { name: '83 %' })).toBeVisible();
  await expect(page.getByRole('status').getByText('Practicada')).toBeVisible();

  await page.getByRole('link', { name: 'Mi plan' }).first().click();
  await expect(page.getByRole('link', { name: /El verbo to be/ }).getByText('Practicada')).toBeVisible();
});

test('diagnóstico adaptativo de principio a fin', async ({ page }) => {
  await newStudent(page);
  await expect(page.getByRole('link', { name: 'Hacer el diagnóstico' })).toBeVisible();
  await page.getByRole('link', { name: 'Hacer el diagnóstico' }).click();
  await page.getByRole('button', { name: 'Empezar diagnóstico' }).click();

  const resultHeading = page.getByRole('heading', { name: 'Tu nivel estimado' });
  for (let i = 0; i < 30; i++) {
    const counter = page.getByText(/^Pregunta \d+ · máximo/);
    await expect(counter.or(resultHeading)).toBeVisible();
    if (await resultHeading.isVisible()) break;
    const n = Number((await counter.textContent())?.match(/Pregunta (\d+)/)?.[1]);
    const blank = page.getByLabel('Espacio 1');
    if (await blank.count()) {
      await blank.fill('has');
    } else {
      // Se hace clic en la etiqueta visible, como haría una persona (en verdadero/falso el radio está oculto).
      await page.locator('label:has(input[type=radio])').first().click();
    }
    await page.getByRole('button', { name: 'Continuar' }).click();
    // Espera a que llegue la pregunta siguiente (o el resultado) antes de volver a responder.
    await expect(page.getByText(`Pregunta ${n + 1} · máximo`).or(resultHeading)).toBeVisible();
  }

  await expect(page.getByRole('heading', { name: 'Tu nivel estimado' })).toBeVisible();
  for (const skill of ['Vocabulario', 'Gramática', 'Lectura']) {
    await expect(page.getByText(skill, { exact: true }).first()).toBeVisible();
  }
  await expect(page.getByText(/^\d+ de \d+ correctas$/)).toHaveCount(3); // una tarjeta por habilidad
  await expect(page.getByText(/No es una certificación oficial del MCER/)).toBeVisible();

  await page.getByRole('link', { name: 'Ver mi plan' }).click();
  await expect(page.getByText(/Plan personalizado a partir de tu nivel estimado/)).toBeVisible();
  await expect(page.getByText('En tu plan').first()).toBeVisible();
});

test('el administrador revisa contenido semilla y un estudiante no accede al panel', async ({ browser }) => {
  const student = await (await browser.newContext()).newPage();
  await newStudent(student);
  await student.goto('/admin/content');
  await expect(student).toHaveURL(/\/app$/);

  const admin = await (await browser.newContext()).newPage();
  await login(admin, ADMIN_EMAIL, ADMIN_PASSWORD);
  await admin.getByRole('link', { name: 'Contenido', exact: true }).click();
  await expect(admin.getByRole('heading', { name: 'Contenido educativo' })).toBeVisible();

  const row = admin.getByRole('link', { name: /Los artículos a y an/ });
  await row.click();
  await expect(admin.getByRole('heading', { name: 'Los artículos a y an' })).toBeVisible();
  const reviewButton = admin.getByRole('button', { name: 'Marcar como revisada' });
  if (await reviewButton.isVisible()) {
    await reviewButton.click();
  }
  await expect(admin.getByText('Revisada', { exact: true })).toBeVisible();
  await expect(admin.getByText(/Ejercicios \(\d+\)/)).toBeVisible();
});
