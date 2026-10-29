import { localDateIso } from './date.utils';

/** MO-T2 of MAP-ORDERS-001: "today" is the local calendar date, never UTC. */
describe('localDateIso', () => {
  it('MO-T2: formats the local calendar date of a local-constructed instant', () => {
    const justAfterMidnight = new Date(2026, 9, 29, 0, 12);

    expect(localDateIso(justAfterMidnight)).toBe('2026-10-29');
  });

  it('MO-T2: equals the local getters, not the UTC date, at either end of the day', () => {
    const pad = (value: number) => String(value).padStart(2, '0');
    for (const instant of [new Date(2026, 9, 29, 0, 12), new Date(2026, 9, 29, 23, 50)]) {
      const local = `${instant.getFullYear()}-${pad(instant.getMonth() + 1)}-${pad(instant.getDate())}`;
      expect(localDateIso(instant)).toBe(local);
    }
  });

  it('pads single-digit months and days', () => {
    expect(localDateIso(new Date(2026, 0, 5, 12, 0))).toBe('2026-01-05');
  });

  it('defaults to the current instant', () => {
    const before = localDateIso(new Date());
    const value = localDateIso();
    const after = localDateIso(new Date());

    expect([before, after]).toContain(value);
  });
});
