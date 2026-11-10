import { Link } from 'react-router';
import { BarChart3, Mic, Sparkles, Wallet } from 'lucide-react';
import { Logo } from '@/components/ui/Logo';
import { ThemeToggle } from '@/components/ui/ThemeToggle';

const features = [
  { icon: Mic, title: 'Conversación por voz', text: 'Practica como en una llamada con un profesor de inglés de IA.' },
  { icon: Sparkles, title: 'Plan a tu medida', text: 'Un diagnóstico estima tu nivel por habilidad y arma tu ruta.' },
  { icon: BarChart3, title: 'Progreso medible', text: 'Repasos espaciados, errores recurrentes y avance real.' },
  { icon: Wallet, title: 'Cobro transparente', text: 'Pagas el consumo real de IA, con el detalle de cada sesión.' },
];

export function LandingPage() {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
        <Logo />
        <nav aria-label="Cuenta" className="flex items-center gap-2">
          <ThemeToggle />
          <Link to="/login" className="rounded-lg px-3 py-2 text-sm font-semibold hover:bg-surface-2">
            Entrar
          </Link>
        </nav>
      </header>
      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-4 sm:px-6">
        <section className="py-12 sm:py-20">
          <p className="text-sm font-semibold uppercase tracking-wider text-accent">Tu profesor de inglés con IA</p>
          <h1 className="mt-3 max-w-3xl text-4xl font-bold tracking-tight sm:text-5xl">
            Habla inglés con confianza, a tu ritmo.
          </h1>
          <p className="mt-4 max-w-2xl text-lg text-muted">
            Lecciones estructuradas, práctica por voz en tiempo real y retroalimentación personalizada, de A1 a C2.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/register"
              className="inline-flex min-h-11 items-center rounded-lg bg-primary px-5 font-semibold text-on-primary hover:bg-primary-hover"
            >
              Crear cuenta gratis
            </Link>
            <Link
              to="/login"
              className="inline-flex min-h-11 items-center rounded-lg border border-border px-5 font-semibold hover:bg-surface-2"
            >
              Ya tengo cuenta
            </Link>
          </div>
        </section>
        <section aria-labelledby="features" className="grid gap-4 pb-16 sm:grid-cols-2 lg:grid-cols-4">
          <h2 id="features" className="sr-only">
            Características
          </h2>
          {features.map(({ icon: Icon, title, text }) => (
            <article key={title} className="rounded-xl border border-border bg-surface p-5">
              <Icon aria-hidden className="size-6 text-primary" />
              <h3 className="mt-3 font-semibold">{title}</h3>
              <p className="mt-1 text-sm text-muted">{text}</p>
            </article>
          ))}
        </section>
      </main>
      <footer className="border-t border-border py-6 text-center text-xs text-muted">
        EnglishOS · Proyecto en desarrollo. Los niveles mostrados son estimaciones, no certificaciones oficiales del MCER.
      </footer>
    </div>
  );
}
