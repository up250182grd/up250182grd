import { useState } from 'react';
import { Moon, Sun } from 'lucide-react';

const STORAGE_KEY = 'englishos-theme';

function currentIsDark() {
  return document.documentElement.classList.contains('dark');
}

/** Alterna modo claro/oscuro y recuerda la preferencia en este navegador. */
export function ThemeToggle() {
  const [dark, setDark] = useState(currentIsDark);

  const toggle = () => {
    const next = !dark;
    document.documentElement.classList.toggle('dark', next);
    try {
      localStorage.setItem(STORAGE_KEY, next ? 'dark' : 'light');
    } catch {
      // Almacenamiento no disponible (modo privado): el tema solo dura esta sesión.
    }
    setDark(next);
  };

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={dark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
      className="inline-flex size-11 items-center justify-center rounded-lg text-muted hover:bg-surface-2 hover:text-text"
    >
      {dark ? <Sun aria-hidden className="size-5" /> : <Moon aria-hidden className="size-5" />}
    </button>
  );
}
