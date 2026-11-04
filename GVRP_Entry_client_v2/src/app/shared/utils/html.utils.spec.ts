import { escapeHtml } from './html.utils';

/** XS-T1 (SEC-XSS-001, D1): escaping of API text before Leaflet innerHTML. */
describe('escapeHtml', () => {
  it('XS-T1: escapes markup so no tag survives', () => {
    expect(escapeHtml('<img src=x onerror=1>')).toBe('&lt;img src=x onerror=1&gt;');
  });

  it('XS-T1: escapes & first, without double escaping the others', () => {
    expect(escapeHtml('a & b')).toBe('a &amp; b');
    expect(escapeHtml('<&>')).toBe('&lt;&amp;&gt;');
  });

  it('XS-T1: escapes double and single quotes for attribute values', () => {
    expect(escapeHtml(`"q" 'q'`)).toBe('&quot;q&quot; &#39;q&#39;');
  });

  it('XS-T1: null and undefined become empty, numbers become strings', () => {
    expect(escapeHtml(null)).toBe('');
    expect(escapeHtml(undefined)).toBe('');
    expect(escapeHtml(42)).toBe('42');
    expect(escapeHtml(0)).toBe('0');
  });

  it('XS-T1: leaves Vietnamese text unchanged', () => {
    expect(escapeHtml('Kho Hà Nội - Đường Láng')).toBe('Kho Hà Nội - Đường Láng');
  });
});
