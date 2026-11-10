import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { Logo } from '@/components/ui/Logo';
import { ThemeToggle } from '@/components/ui/ThemeToggle';

/** Diseño centrado para registro, acceso y recuperación de cuenta. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between px-4 py-3 sm:px-6">
        <Link to="/" aria-label="EnglishOS, inicio">
          <Logo />
        </Link>
        <ThemeToggle />
      </header>
      <main className="flex flex-1 items-start justify-center px-4 pb-12 pt-6 sm:items-center sm:pt-0">
        <div className="w-full max-w-md">
          <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
          {subtitle && <p className="mt-1 text-muted">{subtitle}</p>}
          <div className="mt-6 rounded-xl border border-border bg-surface p-5 shadow-sm shadow-black/5 sm:p-6">
            {children}
          </div>
        </div>
      </main>
    </div>
  );
}
