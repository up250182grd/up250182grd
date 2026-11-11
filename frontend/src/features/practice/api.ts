import { useQuery } from '@tanstack/react-query';
import { api, unwrap, type Schemas } from '@/api/client';

export type QueueItem = Schemas['VocabularyQueueItem'];
export type CardView = Schemas['VocabularyCardView'];
export type ErrorItem = Schemas['ErrorItem'];

export const practiceKeys = {
  queue: ['vocabulary', 'queue'] as const,
  words: ['vocabulary', 'words'] as const,
  errors: ['practice', 'errors'] as const,
};

export function useVocabularyQueue() {
  return useQuery({ queryKey: practiceKeys.queue, queryFn: () => unwrap(api.GET('/api/v1/vocabulary/queue')) });
}

export function useErrorQueue() {
  return useQuery({ queryKey: practiceKeys.errors, queryFn: () => unwrap(api.GET('/api/v1/practice/errors')) });
}

/** Botones de calificación → calidad SM-2 (0–5). */
export const grades: { quality: number; label: string; hint: string; key: string }[] = [
  { quality: 1, label: 'Otra vez', hint: 'No la recordé', key: '1' },
  { quality: 3, label: 'Difícil', hint: 'La recordé con mucho esfuerzo', key: '2' },
  { quality: 4, label: 'Bien', hint: 'La recordé tras pensarlo', key: '3' },
  { quality: 5, label: 'Fácil', hint: 'La recordé al instante', key: '4' },
];

export function formatInterval(days?: number | null): string {
  if (days == null) return '';
  if (days <= 1) return '1 día';
  if (days < 30) return `${days} días`;
  const months = Math.round(days / 30);
  return months === 1 ? '1 mes' : `${months} meses`;
}

export const stateLabels: Record<string, { label: string; tone: 'neutral' | 'primary' | 'success' | 'warning' }> = {
  NEW: { label: 'Nueva', tone: 'neutral' },
  LEARNING: { label: 'Aprendiendo', tone: 'primary' },
  REVIEW: { label: 'En repaso', tone: 'success' },
  RELEARNING: { label: 'Olvidada', tone: 'warning' },
};

export const errorCategoryLabels: Record<string, string> = {
  GRAMMAR: 'Gramática',
  VOCABULARY: 'Vocabulario',
  SPELLING: 'Ortografía',
  WORD_ORDER: 'Orden de palabras',
  OTHER: 'Otro',
};

export const posLabels: Record<string, string> = {
  noun: 'sustantivo',
  verb: 'verbo',
  adjective: 'adjetivo',
  adverb: 'adverbio',
  phrase: 'expresión',
  pronoun: 'pronombre',
  preposition: 'preposición',
};
