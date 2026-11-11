import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD, login, verifiedStudent } from './support';

/**
 * Auditoría automática de accesibilidad (RNF-8, objetivo WCAG 2.2 AA) con axe-core, en tema claro y oscuro.
 * Falla con violaciones "serious" o "critical". axe no sustituye la revisión manual (lector de pantalla, teclado).
 */
async function audit(page: Page, path: string, ready: () => Promise<unknown>) {
  const problems: string[] = [];
  for (const scheme of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto(path);
    await ready();
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
      .analyze();
    for (const v of results.violations.filter((x) => x.impact === 'serious' || x.impact === 'critical')) {
      problems.push(
        `[${scheme}] ${path} · ${v.id} (${v.impact}): ${v.help}\n    ${v.nodes
          .slice(0, 3)
          .map((n) => n.target.join(' ') + ' → ' + (n.failureSummary ?? '').split('\n').slice(1, 2).join(''))
          .join('\n    ')}`,
      );
    }
  }
  return problems;
}

test('páginas públicas sin violaciones serias de accesibilidad', async ({ page }) => {
  const problems = [
    ...(await audit(page, '/', () => page.getByRole('heading', { level: 1 }).first().waitFor())),
    ...(await audit(page, '/login', () => page.getByRole('button', { name: 'Entrar' }).waitFor())),
    ...(await audit(page, '/register', () => page.getByRole('button', { name: 'Crear cuenta' }).waitFor())),
  ];
  expect(problems, problems.join('\n')).toEqual([]);
});

test('páginas del alumno sin violaciones serias de accesibilidad', async ({ page }) => {
  await verifiedStudent(page, 'a11y');
  const h1 = () => page.getByRole('heading', { level: 1 }).first().waitFor();
  const problems: string[] = [];
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
    problems.push(...(await audit(page, path, h1)));
  }
  expect(problems, problems.join('\n')).toEqual([]);
});

/** Escanea la página actual (sin navegar) en el tema activo. */
async function scanHere(page: Page, label: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();
  return results.violations
    .filter((x) => x.impact === 'serious' || x.impact === 'critical')
    .map((v) => `${label} · ${v.id}: ${v.help} → ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`);
}

test('pantallas con contenido (lección, ejercicio, chat, repaso, llamada) sin violaciones serias', async ({ page }) => {
  await verifiedStudent(page, 'a11y-contenido');
  const problems: string[] = [];

  await page.getByRole('link', { name: 'Mi plan' }).first().click();
  await page.getByRole('link', { name: /El verbo to be/ }).click();
  await page.getByRole('heading', { name: 'Explicación' }).waitFor();
  problems.push(...(await scanHere(page, 'lección')));
  await page.getByRole('button', { name: /Empezar práctica/ }).click();
  await page.getByLabel('am', { exact: true }).check();
  await page.getByRole('button', { name: 'Comprobar' }).click();
  await page.getByText('No es correcto').waitFor();
  problems.push(...(await scanHere(page, 'ejercicio con corrección')));

  await page.goto('/app/wallet');
  await page.getByLabel('Monto (MXN)').fill('200');
  await page.getByRole('button', { name: 'Recargar' }).click();
  await page.getByText('Saldo de prueba acreditado.').waitFor();
  await page.goto('/app/tutor');
  await page.getByRole('button', { name: 'Empezar conversación' }).click();
  await page.getByLabel('Tu mensaje en inglés').fill('Hello! She have a cat.');
  await page.getByRole('button', { name: 'Enviar' }).click();
  await page.getByText(/Correcciones \(/).waitFor();
  problems.push(...(await scanHere(page, 'chat con correcciones')));

  await page.goto('/app/voice');
  await page.getByRole('button', { name: 'Iniciar llamada' }).click();
  await page.getByText(/simulated call/).first().waitFor({ timeout: 15_000 });
  problems.push(...(await scanHere(page, 'llamada en curso')));
  await page.getByRole('button', { name: 'Colgar' }).click();
  await page.getByRole('heading', { name: 'Resumen de la llamada' }).waitFor({ timeout: 20_000 });
  problems.push(...(await scanHere(page, 'resumen de llamada')));

  await page.goto('/app/practice?tab=errors');
  await page.getByTestId('error-exercise').waitFor();
  problems.push(...(await scanHere(page, 'práctica de errores')));

  expect(problems, problems.join('\n')).toEqual([]);
});

test('páginas de administración sin violaciones serias de accesibilidad', async ({ page }) => {
  await login(page, ADMIN_EMAIL, ADMIN_PASSWORD);
  const h1 = () => page.getByRole('heading', { level: 1 }).first().waitFor();
  const problems: string[] = [];
  for (const path of ['/admin/users', '/admin/content', '/admin/billing', '/admin/recommendations', '/admin/audit']) {
    problems.push(...(await audit(page, path, h1)));
  }
  expect(problems, problems.join('\n')).toEqual([]);
});
