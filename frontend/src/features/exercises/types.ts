/** Formas JSON de los enunciados, respuestas y soluciones (espejo de ExerciseEvaluator en el backend). */
export type ExerciseType =
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'FILL_BLANK'
  | 'WORD_ORDER'
  | 'MATCHING'
  | 'ERROR_SPOTTING';

export interface Option {
  id: string;
  text: string;
}

export interface Prompt {
  question?: string;
  statement?: string;
  passage?: string;
  options?: Option[];
  text?: string;
  tokens?: string[];
  left?: Option[];
  right?: Option[];
}

export interface Answer {
  optionId?: string;
  value?: boolean;
  values?: string[];
  tokens?: string[];
  pairs?: Record<string, string>;
  index?: number;
}

export interface Solution {
  correctOptionId?: string;
  value?: boolean;
  answers?: string[][];
  sentences?: string[][];
  pairs?: Record<string, string>;
  errorIndex?: number;
  correction?: string;
}

export const BLANK = '___';

export function blankCount(text = ''): number {
  return text.split(BLANK).length - 1;
}

/** ¿El alumno completó la respuesta lo suficiente para enviarla? */
export function isComplete(type: ExerciseType, prompt: Prompt, answer: Answer): boolean {
  switch (type) {
    case 'MULTIPLE_CHOICE':
      return !!answer.optionId;
    case 'TRUE_FALSE':
      return typeof answer.value === 'boolean';
    case 'FILL_BLANK':
      return (answer.values ?? []).length === blankCount(prompt.text) && (answer.values ?? []).every((v) => v.trim() !== '');
    case 'WORD_ORDER':
      return (answer.tokens ?? []).length === (prompt.tokens ?? []).length;
    case 'MATCHING':
      return (prompt.left ?? []).every((l) => !!answer.pairs?.[l.id]);
    case 'ERROR_SPOTTING':
      return typeof answer.index === 'number';
  }
}

/** Solo los campos que espera el backend para cada tipo (sin estado interno de la interfaz). */
export function toPayload(type: ExerciseType, answer: Answer): Answer {
  switch (type) {
    case 'MULTIPLE_CHOICE':
      return { optionId: answer.optionId };
    case 'TRUE_FALSE':
      return { value: answer.value };
    case 'FILL_BLANK':
      return { values: answer.values ?? [] };
    case 'WORD_ORDER':
      return { tokens: answer.tokens ?? [] };
    case 'MATCHING':
      return { pairs: answer.pairs ?? {} };
    case 'ERROR_SPOTTING':
      return { index: answer.index };
  }
}

/** Texto legible de la solución correcta, para mostrarlo tras responder. */
export function describeSolution(type: ExerciseType, prompt: Prompt, solution: Solution): string[] {
  switch (type) {
    case 'MULTIPLE_CHOICE':
      return [prompt.options?.find((o) => o.id === solution.correctOptionId)?.text ?? ''];
    case 'TRUE_FALSE':
      return [solution.value ? 'Verdadero / True' : 'Falso / False'];
    case 'FILL_BLANK': {
      let i = 0;
      return [(prompt.text ?? '').replace(/___/g, () => solution.answers?.[i++]?.[0] ?? '___')];
    }
    case 'WORD_ORDER':
      return [(solution.sentences?.[0] ?? []).join(' ').replace(/ ([?.!,])/g, '$1')];
    case 'MATCHING':
      return (prompt.left ?? []).map(
        (l) => `${l.text} → ${prompt.right?.find((r) => r.id === solution.pairs?.[l.id])?.text ?? ''}`,
      );
    case 'ERROR_SPOTTING':
      return [`${prompt.tokens?.[solution.errorIndex ?? -1] ?? ''} → ${solution.correction ?? ''}`];
  }
}
