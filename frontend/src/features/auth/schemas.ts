import { z } from 'zod';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '@/api/client';

/** Reglas mínimas en el cliente (la validación definitiva la hace el backend: lista de comunes, etc.). */
export const passwordSchema = z
  .string()
  .min(12, 'Usa al menos 12 caracteres. Una frase fácil de recordar funciona bien.')
  .max(128, 'Máximo 128 caracteres.');

export const emailSchema = z.string().trim().min(1, 'Escribe tu correo.').email('Escribe un correo válido.');

export const registerSchema = z.object({
  displayName: z.string().trim().min(1, 'Escribe tu nombre.').max(80, 'Máximo 80 caracteres.'),
  email: emailSchema,
  password: passwordSchema,
  acceptTerms: z.literal(true, { error: 'Debes aceptar los términos de uso.' }),
  acceptPrivacy: z.literal(true, { error: 'Debes aceptar el aviso de privacidad.' }),
});
export type RegisterForm = z.infer<typeof registerSchema>;

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'Escribe tu contraseña.'),
});
export type LoginForm = z.infer<typeof loginSchema>;

/** Copia los errores por campo del backend (VALIDATION_ERROR) a react-hook-form; devuelve si aplicó alguno. */
export function applyFieldErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>): boolean {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) return false;
  for (const fe of error.fieldErrors) {
    setError(fe.field as Path<T>, { type: 'server', message: fe.message });
  }
  return true;
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Ocurrió un error inesperado.';
}
