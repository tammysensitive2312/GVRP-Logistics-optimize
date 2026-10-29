import { groupGeometryMissing, isGeometrySummary } from './compliance-rows.utils';

/** UL-T7 (UX-LEZ-001, D9): repeated geometry findings of one route are one line. */
describe('groupGeometryMissing', () => {
  const access = { text: 'Stop in zone', code: 'ZONE_ACCESS', detail: 'd', seq: 2 };
  const missing = (seq: number) =>
    ({ text: `Leg ${seq} has no geometry`, code: 'LEZ_GEOMETRY_MISSING', detail: null, seq });

  it('UL-T7: no geometry finding → unchanged', () => {
    expect(groupGeometryMissing([access])).toEqual([access]);
    expect(groupGeometryMissing([])).toEqual([]);
  });

  it('UL-T7: one geometry finding → unchanged and still clickable', () => {
    const rows = [missing(0), access];

    const grouped = groupGeometryMissing(rows);

    expect(grouped).toEqual(rows);
    expect(isGeometrySummary(grouped[0])).toBeFalse();
  });

  it('UL-T7: three geometry findings → one summary line where the first was, then ZONE_ACCESS', () => {
    const grouped = groupGeometryMissing([missing(0), missing(1), access, missing(3)]);

    expect(grouped.length).toBe(2);
    expect(grouped[0].text)
      .toBe('3 legs have no stored road geometry — it is fetched and checked when the route is sent.');
    expect(grouped[0].seq).toBe(0);
    expect(isGeometrySummary(grouped[0])).toBeTrue();
    expect(grouped[1]).toEqual(access);
  });
});
