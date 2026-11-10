import type { ReactNode } from 'react';

/**
 * Renderizador mínimo y SEGURO del formato de las explicaciones: "## título", listas "- " y **negritas**.
 * Construye elementos de React (nunca innerHTML), así que el contenido no puede inyectar HTML ni scripts.
 */
function inline(text: string, keyPrefix: string): ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, i) =>
    part.startsWith('**') && part.endsWith('**') && part.length > 4 ? (
      <strong key={`${keyPrefix}-${i}`} className="font-semibold text-text">
        {part.slice(2, -2)}
      </strong>
    ) : (
      part
    ),
  );
}

export function RichText({ source }: { source: string }) {
  const blocks: ReactNode[] = [];
  let list: string[] = [];
  let paragraph: string[] = [];

  const flushList = () => {
    if (list.length) {
      const items = list;
      blocks.push(
        <ul key={`ul-${blocks.length}`} className="my-2 flex list-disc flex-col gap-1 pl-5">
          {items.map((item, i) => (
            <li key={i}>{inline(item, `li-${blocks.length}-${i}`)}</li>
          ))}
        </ul>,
      );
      list = [];
    }
  };
  const flushParagraph = () => {
    if (paragraph.length) {
      const text = paragraph.join(' ');
      blocks.push(
        <p key={`p-${blocks.length}`} className="my-2">
          {inline(text, `p-${blocks.length}`)}
        </p>,
      );
      paragraph = [];
    }
  };

  for (const raw of source.split('\n')) {
    const line = raw.trim();
    if (line.startsWith('## ')) {
      flushList();
      flushParagraph();
      blocks.push(
        <h3 key={`h-${blocks.length}`} className="mt-5 mb-1 text-base font-semibold text-text first:mt-0">
          {line.slice(3)}
        </h3>,
      );
    } else if (line.startsWith('- ')) {
      flushParagraph();
      list.push(line.slice(2));
    } else if (line === '') {
      flushList();
      flushParagraph();
    } else {
      flushList();
      paragraph.push(line);
    }
  }
  flushList();
  flushParagraph();
  return <div className="leading-relaxed text-text/90">{blocks}</div>;
}
