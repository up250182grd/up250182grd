import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

export type Lang = 'es' | 'en';
export interface Localized {
  es?: string;
  en?: string;
}

const STORAGE_KEY = 'englishos-explanation-lang';

interface LangContextValue {
  lang: Lang;
  setLang: (lang: Lang) => void;
}

const LangContext = createContext<LangContextValue | null>(null);

function readStored(): Lang | null {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === 'es' || v === 'en' ? v : null;
  } catch {
    return null;
  }
}

/**
 * Idioma de las explicaciones. Valor inicial: la última elección en este navegador o, si no hay, la preferencia del
 * perfil. Cambiarlo es instantáneo porque la API devuelve siempre ambos idiomas.
 */
export function LangProvider({ profileLang, children }: { profileLang?: string; children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(() => readStored() ?? (profileLang === 'en' ? 'en' : 'es'));
  const setLang = useCallback((next: Lang) => {
    setLangState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // modo privado: solo dura esta sesión
    }
  }, []);
  const value = useMemo(() => ({ lang, setLang }), [lang, setLang]);
  return <LangContext.Provider value={value}>{children}</LangContext.Provider>;
}

export function useLang(): LangContextValue {
  const ctx = useContext(LangContext);
  if (!ctx) throw new Error('useLang debe usarse dentro de LangProvider');
  return ctx;
}

/** Texto en el idioma elegido, con el otro idioma como respaldo. */
export function pick(text: Localized | undefined | null, lang: Lang): string {
  if (!text) return '';
  return (lang === 'en' ? text.en || text.es : text.es || text.en) ?? '';
}

export function useT() {
  const { lang } = useLang();
  return useCallback((text: Localized | undefined | null) => pick(text, lang), [lang]);
}

/** Selector ES | EN accesible (grupo de botones con estado pulsado). */
export function LanguageToggle({ className }: { className?: string }) {
  const { lang, setLang } = useLang();
  const options: { value: Lang; label: string; full: string }[] = [
    { value: 'es', label: 'ES', full: 'Español' },
    { value: 'en', label: 'EN', full: 'English' },
  ];
  return (
    <div
      role="group"
      aria-label="Idioma de las explicaciones"
      className={cn('inline-flex rounded-lg border border-border bg-surface-2 p-0.5', className)}
    >
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          aria-pressed={lang === o.value}
          title={o.full}
          onClick={() => setLang(o.value)}
          className={cn(
            'min-h-9 min-w-11 rounded-md px-2 text-xs font-bold tracking-wide transition-colors',
            lang === o.value ? 'bg-surface text-primary shadow-sm' : 'text-muted hover:text-text',
          )}
        >
          <span aria-hidden>{o.label}</span>
          <span className="sr-only">{o.full}</span>
        </button>
      ))}
    </div>
  );
}
