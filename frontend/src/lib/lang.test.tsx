import { afterEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LangProvider, LanguageToggle, pick, useT } from './lang';

const text = { es: 'Explicación en español', en: 'Explanation in English' };

function Sample() {
  const t = useT();
  return <p>{t(text)}</p>;
}

afterEach(() => localStorage.clear());

describe('idioma de las explicaciones', () => {
  it('empieza con el idioma del perfil y se cambia al instante', async () => {
    const user = userEvent.setup();
    render(
      <LangProvider profileLang="es">
        <LanguageToggle />
        <Sample />
      </LangProvider>,
    );
    expect(screen.getByText('Explicación en español')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Español' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(screen.getByRole('button', { name: 'English' }));
    expect(screen.getByText('Explanation in English')).toBeInTheDocument();
    expect(localStorage.getItem('englishos-explanation-lang')).toBe('en');
  });

  it('recuerda la última elección en este navegador por encima del perfil', () => {
    localStorage.setItem('englishos-explanation-lang', 'en');
    render(
      <LangProvider profileLang="es">
        <Sample />
      </LangProvider>,
    );
    expect(screen.getByText('Explanation in English')).toBeInTheDocument();
  });

  it('usa el otro idioma como respaldo si falta una traducción', () => {
    expect(pick({ es: 'solo español' }, 'en')).toBe('solo español');
    expect(pick(undefined, 'es')).toBe('');
  });
});
