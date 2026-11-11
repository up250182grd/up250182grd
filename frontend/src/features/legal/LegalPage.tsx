import { Link, useNavigate, useParams } from 'react-router';
import { ArrowLeft } from 'lucide-react';
import { Logo } from '@/components/ui/Logo';
import { RichText } from '@/components/ui/RichText';
import comercial from './content/comercial.md?raw';
import privacidad from './content/privacidad.md?raw';
import terminos from './content/terminos.md?raw';

export const legalDocs: Record<string, { title: string; source: string }> = {
  privacidad: { title: 'Aviso de privacidad', source: privacidad },
  terminos: { title: 'Términos de uso', source: terminos },
  comercial: { title: 'Política comercial y de precios', source: comercial },
};

/** Documentos legales públicos (borradores sujetos a revisión legal). */
export function LegalPage() {
  const { doc = '' } = useParams();
  const navigate = useNavigate();
  const d = legalDocs[doc];
  return (
    <div className="min-h-dvh bg-bg px-4 py-6 sm:px-6">
      <div className="mx-auto flex max-w-3xl flex-col gap-6">
        <div className="flex items-center justify-between">
          <Link to="/" aria-label="Inicio de EnglishOS">
            <Logo />
          </Link>
          <button
            type="button"
            onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}
            className="inline-flex min-h-11 items-center gap-1 text-sm text-primary hover:underline"
          >
            <ArrowLeft aria-hidden className="size-4" /> Volver
          </button>
        </div>
        {d ? (
          <article className="rounded-xl border border-border bg-surface p-5 sm:p-8">
            <h1 className="mb-4 text-2xl font-bold tracking-tight">{d.title}</h1>
            <div className="flex flex-col gap-3 leading-relaxed">
              <RichText source={d.source} />
            </div>
          </article>
        ) : (
          <p className="text-muted">Documento no encontrado.</p>
        )}
        <nav aria-label="Documentos legales" className="flex flex-wrap gap-4 text-sm">
          {Object.entries(legalDocs).map(([k, v]) => (
            <Link key={k} to={`/legal/${k}`} className="text-primary hover:underline">
              {v.title}
            </Link>
          ))}
        </nav>
      </div>
    </div>
  );
}
