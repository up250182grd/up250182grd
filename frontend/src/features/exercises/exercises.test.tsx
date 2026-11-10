import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExerciseInput } from './ExerciseInput';
import { describeSolution, isComplete, toPayload, type Answer, type ExerciseType, type Prompt } from './types';

function Harness({ type, prompt, onAnswer }: { type: ExerciseType; prompt: Prompt; onAnswer: (a: Answer) => void }) {
  const [value, setValue] = useState<Answer>({});
  return (
    <ExerciseInput
      type={type}
      prompt={prompt}
      value={value}
      onChange={(a) => {
        setValue(a);
        onAnswer(a);
      }}
    />
  );
}

describe('lógica de ejercicios', () => {
  it('detecta respuestas incompletas', () => {
    expect(isComplete('FILL_BLANK', { text: 'a ___ b ___' }, { values: ['x', ''] })).toBe(false);
    expect(isComplete('FILL_BLANK', { text: 'a ___ b ___' }, { values: ['x', 'y'] })).toBe(true);
    expect(isComplete('TRUE_FALSE', {}, { value: false })).toBe(true);
    expect(isComplete('MATCHING', { left: [{ id: 'l1', text: 'a' }] }, { pairs: {} })).toBe(false);
  });

  it('envía solo los campos que espera el backend', () => {
    const answer = { tokens: ['I', 'am'], indices: [1, 0] } as Answer;
    expect(toPayload('WORD_ORDER', answer)).toEqual({ tokens: ['I', 'am'] });
  });

  it('describe la solución de forma legible', () => {
    expect(
      describeSolution('FILL_BLANK', { text: 'She ___ and I ___.' }, { answers: [['studies'], ['work']] }),
    ).toEqual(['She studies and I work.']);
    expect(
      describeSolution('WORD_ORDER', { tokens: [] }, { sentences: [['Where', 'are', 'you', 'from', '?']] }),
    ).toEqual(['Where are you from?']);
    expect(describeSolution('ERROR_SPOTTING', { tokens: ['My', 'is'] }, { errorIndex: 1, correction: 'are' })).toEqual([
      'is → are',
    ]);
  });
});

describe('ExerciseInput', () => {
  it('ordenar palabras: construye la oración tocando palabras y permite quitarlas', async () => {
    const user = userEvent.setup();
    let last: Answer = {};
    render(<Harness type="WORD_ORDER" prompt={{ tokens: ['am', 'I', 'here'] }} onAnswer={(a) => (last = a)} />);

    await user.click(screen.getByRole('button', { name: 'I' }));
    await user.click(screen.getByRole('button', { name: 'am' }));
    await user.click(screen.getByRole('button', { name: 'here' }));
    expect(last.tokens).toEqual(['I', 'am', 'here']);

    await user.click(screen.getByRole('button', { name: 'Quitar am' }));
    expect(last.tokens).toEqual(['I', 'here']);
  });

  it('completar: un campo accesible por hueco', async () => {
    const user = userEvent.setup();
    let last: Answer = {};
    render(<Harness type="FILL_BLANK" prompt={{ text: 'We ___ friends and they ___ happy.' }} onAnswer={(a) => (last = a)} />);
    await user.type(screen.getByLabelText('Espacio 1'), 'are');
    await user.type(screen.getByLabelText('Espacio 2'), 'are');
    expect(last.values).toEqual(['are', 'are']);
  });

  it('relacionar: un selector por elemento', async () => {
    const user = userEvent.setup();
    let last: Answer = {};
    render(
      <Harness
        type="MATCHING"
        prompt={{ left: [{ id: 'l1', text: 'dog' }], right: [{ id: 'r1', text: 'gato' }, { id: 'r2', text: 'perro' }] }}
        onAnswer={(a) => (last = a)}
      />,
    );
    await user.selectOptions(screen.getByLabelText('dog'), 'perro');
    expect(last.pairs).toEqual({ l1: 'r2' });
  });

  it('identificar error: se elige una palabra (radio)', async () => {
    const user = userEvent.setup();
    let last: Answer = {};
    render(<Harness type="ERROR_SPOTTING" prompt={{ tokens: ['She', 'are', 'here'] }} onAnswer={(a) => (last = a)} />);
    await user.click(screen.getByRole('radio', { name: 'are' }));
    expect(last.index).toBe(1);
    expect(screen.getByRole('radio', { name: 'are' })).toHaveAttribute('aria-checked', 'true');
  });
});
