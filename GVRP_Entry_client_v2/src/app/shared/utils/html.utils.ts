/**
 * HTML escaping for strings that Leaflet renders with `innerHTML`
 * (SEC-XSS-001, SEC-015).
 *
 * Leaflet sets string content of tooltips, popups and `divIcon.html` as HTML,
 * so API-supplied text (depot, zone, stop and order fields) must be escaped
 * before it is interpolated into those strings.
 */

/** Escapes text for safe interpolation into HTML strings (Leaflet popups, tooltips, divIcon). */
export function escapeHtml(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
