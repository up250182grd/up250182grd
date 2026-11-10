import { useQuery } from '@tanstack/react-query';
import { api, unwrap, type Schemas } from '@/api/client';

export type CourseView = Schemas['CourseView'];
export type LessonView = Schemas['LessonView'];
export type LearningSummary = Schemas['LearningSummary'];
export type SkillLevel = Schemas['SkillLevelView'];

export const learningKeys = {
  courses: ['learning', 'courses'] as const,
  lesson: (id: string) => ['learning', 'lesson', id] as const,
  summary: ['learning', 'summary'] as const,
  skills: ['learning', 'skills'] as const,
};

export function useCourses() {
  return useQuery({ queryKey: learningKeys.courses, queryFn: () => unwrap(api.GET('/api/v1/courses')) });
}

export function useLesson(publicId: string) {
  return useQuery({
    queryKey: learningKeys.lesson(publicId),
    queryFn: () => unwrap(api.GET('/api/v1/lessons/{publicId}', { params: { path: { publicId } } })),
  });
}

export function useLearningSummary() {
  return useQuery({ queryKey: learningKeys.summary, queryFn: () => unwrap(api.GET('/api/v1/me/learning-summary')) });
}

export function useSkillLevels() {
  return useQuery({ queryKey: learningKeys.skills, queryFn: () => unwrap(api.GET('/api/v1/me/skills')) });
}

export const skillLabels: Record<string, { es: string; en: string }> = {
  VOCABULARY: { es: 'Vocabulario', en: 'Vocabulary' },
  GRAMMAR: { es: 'Gramática', en: 'Grammar' },
  READING: { es: 'Lectura', en: 'Reading' },
};

export const confidenceLabels: Record<string, string> = {
  LOW: 'Confianza baja',
  MEDIUM: 'Confianza media',
  HIGH: 'Confianza alta',
};

export function percent(score?: number | null): string {
  return score == null ? '—' : `${Math.round(score * 100)} %`;
}
