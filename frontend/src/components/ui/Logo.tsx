export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2 font-bold tracking-tight text-text">
      <svg aria-hidden viewBox="0 0 32 32" className="size-8">
        <rect width="32" height="32" rx="8" fill="var(--color-primary)" />
        <path d="M9 10h11v3h-7.5v2.5H19v3h-6.5V21H20v3H9z" fill="var(--color-on-primary)" />
        <circle cx="23.5" cy="22.5" r="2.5" fill="var(--color-accent)" />
      </svg>
      {!compact && (
        <span className="text-lg">
          English<span className="text-primary">OS</span>
        </span>
      )}
    </span>
  );
}
