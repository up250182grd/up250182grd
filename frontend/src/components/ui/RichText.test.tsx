import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RichText } from './RichText';

describe('RichText', () => {
  it('interpreta títulos, listas y negritas', () => {
    render(<RichText source={'## Las formas\n- **I am**\n- you are\n\nTexto normal.'} />);
    expect(screen.getByRole('heading', { name: 'Las formas' })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
    expect(screen.getByText('I am').tagName).toBe('STRONG');
    expect(screen.getByText('Texto normal.')).toBeInTheDocument();
  });

  it('nunca interpreta HTML del contenido (protección contra XSS)', () => {
    const { container } = render(<RichText source={'<img src=x onerror="alert(1)"> <script>alert(2)</script>'} />);
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('script')).toBeNull();
    expect(container.textContent).toContain('<img src=x');
  });
});
