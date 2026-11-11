const mxn = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });
const precise = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 6 });

/** Importe en pesos mexicanos (los importes llegan del backend ya redondeados a 2 decimales). */
export function formatMXN(value?: number | null): string {
  return value == null ? '—' : mxn.format(value);
}

/** Costos de proveedor con alta precisión (pueden ser fracciones de centavo). */
export function formatPrecise(value?: number | null, currency = ''): string {
  return value == null ? '—' : `${precise.format(value)}${currency ? ` ${currency}` : ''}`;
}
