import {
  complianceStatusLabel,
  formatIntervals,
  hhMmToMinutes,
  minutesToHhMm,
  violationSummary
} from './lez-format.utils';

describe('lez-format.utils', () => {
  it('formats minutes of the day, including 24:00 as an end', () => {
    expect(minutesToHhMm(0)).toBe('00:00');
    expect(minutesToHhMm(90)).toBe('01:30');
    expect(minutesToHhMm(19 * 60 + 30)).toBe('19:30');
    expect(minutesToHhMm(1440)).toBe('24:00');
  });

  it('parses HH:mm and rejects impossible text', () => {
    expect(hhMmToMinutes('06:00')).toBe(360);
    expect(hhMmToMinutes('24:00')).toBe(1440);
    expect(hhMmToMinutes('24:01')).toBeNull();
    expect(hhMmToMinutes('6:00')).toBeNull();
    expect(hhMmToMinutes('')).toBeNull();
    expect(hhMmToMinutes(null)).toBeNull();
  });

  it('formats an interval list', () => {
    expect(formatIntervals([[360, 540], [960, 1170]])).toBe('06:00–09:00, 16:00–19:30');
    expect(formatIntervals([])).toBe('—');
    expect(formatIntervals(null)).toBe('—');
  });

  it('labels compliance statuses', () => {
    expect(complianceStatusLabel('COMPLIANT')).toBe('Compliant');
    expect(complianceStatusLabel('VIOLATION')).toBe('Violation');
    expect(complianceStatusLabel('STALE')).toBe('Needs recheck');
    expect(complianceStatusLabel('UNCHECKED')).toBe('Not checked');
    expect(complianceStatusLabel(null)).toBe('Unknown');
  });

  it('summarises a violation with its stop and zone name', () => {
    expect(violationSummary(
      { code: 'LEZ_FORBIDDEN_INTERVAL', seq: 2, zone_key: 'Z17', detail: '08:10' },
      'Order A',
      'Hanoi LEZ'
    )).toBe('LEZ_FORBIDDEN_INTERVAL at Order A in Hanoi LEZ — 08:10');
  });
});
