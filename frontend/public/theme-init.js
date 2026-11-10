// Aplica el tema guardado antes del primer pintado para evitar parpadeo.
// Archivo externo (no en línea) para poder usar una CSP estricta: script-src 'self'.
try {
  var t = localStorage.getItem('englishos-theme');
  var dark = t ? t === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
  document.documentElement.classList.toggle('dark', dark);
} catch (e) {
  /* almacenamiento no disponible */
}
