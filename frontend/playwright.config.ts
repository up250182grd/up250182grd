import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas E2E contra el sistema real: frontend (Vite) + backend (Spring Boot, perfil dev) + MySQL + Mailpit.
 * Requisitos antes de ejecutar (ver README):
 *   docker compose up -d mysql mailpit
 *   backend en http://localhost:8080 con ADMIN_BOOTSTRAP_* = valores de E2E_ADMIN_*
 *   npm run dev (o deja que Playwright lo arranque)
 */
const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'off',
    launchOptions: executablePath ? { executablePath } : {},
  },
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: 'npm run dev', url: 'http://localhost:5173', reuseExistingServer: true, timeout: 60_000 },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] }, testIgnore: /responsive\.spec\.ts/ },
    { name: 'mobile', use: { ...devices['Pixel 7'] }, testMatch: /responsive\.spec\.ts/ },
  ],
});
