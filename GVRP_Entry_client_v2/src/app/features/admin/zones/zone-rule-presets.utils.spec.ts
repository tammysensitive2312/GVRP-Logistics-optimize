import { localDateIso } from '@shared/utils/date.utils';

import {
  emptyRuleForm,
  RULE_PRESETS,
  ruleWarnings,
  validateRuleForm,
  ZoneRuleFormModel
} from './zone-rule-form.utils';

/** UL-T5 / UL-T6 (UX-LEZ-001, D5/D6): rule presets and non-blocking warnings. */
describe('zone rule presets and warnings', () => {
  const form = (patch: Partial<ZoneRuleFormModel> = {}): ZoneRuleFormModel =>
    ({ ...emptyRuleForm(), effectiveFrom: '2027-01-01', ...patch });

  // ---------- UL-T5 ----------

  it('UL-T5: there are the four presets of the design', () => {
    expect(RULE_PRESETS.map(preset => preset.label)).toEqual([
      'Trucks over 3.5 t, peak hours',
      'Below Euro 4, all day',
      'Only electric vehicles',
      'Motorbikes, all day'
    ]);
  });

  it('UL-T5: every preset yields a valid rule and keeps dates, basis and note', () => {
    const base = form({
      effectiveTo: '2027-12-31', legalBasis: 'Decision 1', note: 'keep me',
      legalClasses: ['BUS'], scheduleType: 'ALLOWED_ONLY', intervals: [{ start: '01:00', end: '02:00' }]
    });

    for (const preset of RULE_PRESETS) {
      const result = preset.apply(base);
      expect(validateRuleForm(result, 1)).withContext(preset.label).toEqual([]);
      expect(result.effectiveFrom).toBe('2027-01-01');
      expect(result.effectiveTo).toBe('2027-12-31');
      expect(result.legalBasis).toBe('Decision 1');
      expect(result.note).toBe('keep me');
      expect(result.legalClasses).not.toContain('BUS');
    }
  });

  it('UL-T5: a blank effective-from becomes today', () => {
    const result = RULE_PRESETS[1].apply(form({ effectiveFrom: '' }));

    expect(result.effectiveFrom).toBe(localDateIso());
    expect(validateRuleForm(result, 1)).toEqual([]);
  });

  it('UL-T5: the truck preset is GROSS > 3500 during both peaks', () => {
    const result = RULE_PRESETS[0].apply(form());

    expect(result.legalClasses).toEqual(['TRUCK']);
    expect(result.weightMeasure).toBe('GROSS');
    expect(result.weightGt).toBe(3500);
    expect(result.scheduleType).toBe('FORBIDDEN_DURING');
    expect(result.intervals).toEqual([{ start: '06:00', end: '09:00' }, { start: '16:00', end: '19:30' }]);
  });

  // ---------- UL-T6 ----------

  const today = '2026-10-29';

  it('UL-T6: a normal rule has no warning', () => {
    expect(ruleWarnings(form({ emissionStandardBelow: 4, legalClasses: ['TRUCK'] }), today)).toEqual([]);
  });

  it('UL-T6: emission standard below 1 never matches', () => {
    expect(ruleWarnings(form({ emissionStandardBelow: 1 }), today))
      .toEqual(['No vehicle has an emission standard below 1; this rule never matches on emissions.']);
  });

  it('UL-T6: emission standard below 7 matches every known standard', () => {
    expect(ruleWarnings(form({ emissionStandardBelow: 7 }), today))
      .toEqual(['Euro levels go up to 6; every vehicle with a known standard matches.']);
  });

  it('UL-T6: every class or every energy type ticked equals leaving them empty', () => {
    const warnings = ruleWarnings(form({
      legalClasses: ['MOTORBIKE', 'CAR', 'TRUCK', 'BUS', 'NON_MOTORIZED'],
      energyTypes: ['ELECTRIC', 'HYBRID', 'PETROL', 'DIESEL', 'OTHER']
    }), today);

    expect(warnings).toEqual([
      'All classes are ticked; this is the same as leaving them empty.',
      'All energy types are ticked; this is the same as leaving them empty.'
    ]);
  });

  it('UL-T6: an end date before today has already ended; today itself has not', () => {
    expect(ruleWarnings(form({ effectiveFrom: '2026-01-01', effectiveTo: '2026-10-28' }), today))
      .toEqual(['This rule has already ended.']);
    expect(ruleWarnings(form({ effectiveFrom: '2026-01-01', effectiveTo: today }), today)).toEqual([]);
  });

  it('UL-T6: warnings never block validation', () => {
    const rule = form({ emissionStandardBelow: 1 });

    expect(ruleWarnings(rule, today).length).toBe(1);
    expect(validateRuleForm(rule, 1)).toEqual([]);
  });
});
