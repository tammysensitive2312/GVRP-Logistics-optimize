import { ZoneRuleDTO } from '@core/models';

import {
  emptyRuleForm,
  fromRuleDTO,
  isSelectorEmpty,
  toRuleInput,
  validateRuleForm,
  ZoneRuleFormModel
} from './zone-rule-form.utils';

/** RF-T1 / RF-T2: pure mapping and validation of the structured rule form (D7). */
describe('zone-rule-form.utils', () => {
  it('omits intervals and days for ALWAYS_FORBIDDEN', () => {
    const input = toRuleInput({ ...emptyRuleForm(), scheduleType: 'ALWAYS_FORBIDDEN' });

    expect(input.schedule).toEqual({ type: 'ALWAYS_FORBIDDEN' });
    expect(input.schedule?.intervals).toBeUndefined();
    expect(input.schedule?.daysOfWeek).toBeUndefined();
  });

  it('maps a gross weight bound and keeps the selector minimal', () => {
    const form: ZoneRuleFormModel = {
      ...emptyRuleForm(),
      weightMeasure: 'GROSS',
      weightGt: 3500
    };

    expect(toRuleInput(form).selector).toEqual({
      weightMeasure: 'GROSS',
      weightKg: { gt: 3500 }
    });
  });

  it('turns a fully empty selector into {} and flags that it applies to every vehicle', () => {
    const form = emptyRuleForm();

    expect(toRuleInput(form).selector).toEqual({});
    expect(isSelectorEmpty(form)).toBeTrue();
  });

  it('round trips a stored rule through the form model', () => {
    const dto: ZoneRuleDTO = {
      selector: {
        legalClasses: ['TRUCK'],
        energyTypes: ['DIESEL'],
        weightMeasure: 'GROSS',
        weightKg: { gte: 3500, lt: 5000 },
        emissionStandardBelow: 4,
        appBased: true
      },
      schedule: {
        type: 'FORBIDDEN_DURING',
        daysOfWeek: ['MON', 'TUE'],
        intervals: [['06:00', '09:00'], ['16:00', '19:30']]
      },
      effective_from: '2027-01-01',
      effective_to: null,
      legal_basis: 'Decision 23',
      note: null
    };

    const input = toRuleInput(fromRuleDTO(dto));

    expect(input.selector).toEqual(dto.selector);
    expect(input.schedule).toEqual(dto.schedule);
    expect(input.effective_from).toBe('2027-01-01');
    expect(input.effective_to).toBeNull();
    expect(input.legal_basis).toBe('Decision 23');
    expect(input.note).toBeNull();
  });

  // ---------- RF-T2 ----------

  it('rejects an interval whose start equals its end', () => {
    const form: ZoneRuleFormModel = {
      ...emptyRuleForm(),
      scheduleType: 'FORBIDDEN_DURING',
      intervals: [{ start: '06:00', end: '06:00' }]
    };

    expect(validateRuleForm(form, 1)).toContain(
      'An interval may not start and end at the same time (06:00).'
    );
  });

  it('rejects 24:00 as an interval start', () => {
    const form: ZoneRuleFormModel = {
      ...emptyRuleForm(),
      scheduleType: 'FORBIDDEN_DURING',
      intervals: [{ start: '24:00', end: '23:59' }]
    };

    expect(validateRuleForm(form, 1)).toContain('24:00 can only be an end.');
  });

  it('rejects ALLOWED_ONLY without an interval', () => {
    const form: ZoneRuleFormModel = {
      ...emptyRuleForm(),
      scheduleType: 'ALLOWED_ONLY',
      intervals: []
    };

    expect(validateRuleForm(form, 1)).toContain('Add at least one interval.');
  });

  it('rejects effective_to before effective_from', () => {
    const form: ZoneRuleFormModel = {
      ...emptyRuleForm(),
      effectiveFrom: '2027-06-01',
      effectiveTo: '2027-05-31'
    };

    expect(validateRuleForm(form, 1)).toContain(
      '"Effective to" must be on or after "Effective from".'
    );
  });

  it('rejects more than 50 rules', () => {
    expect(validateRuleForm(emptyRuleForm(), 51)).toContain(
      'A zone may have at most 50 rules.'
    );
  });

  it('rejects a weight measure without a bound', () => {
    const form: ZoneRuleFormModel = {
      ...emptyRuleForm(),
      weightMeasure: 'PAYLOAD'
    };

    expect(validateRuleForm(form, 1)).toContain('Add at least one weight bound.');
  });

  it('accepts a valid interval rule', () => {
    const form: ZoneRuleFormModel = {
      ...emptyRuleForm(),
      scheduleType: 'ALLOWED_ONLY',
      intervals: [{ start: '21:00', end: '06:00' }],
      // review-p1c R6: the effective date is required, so a valid rule has one.
      effectiveFrom: '2027-01-01'
    };

    expect(validateRuleForm(form, 1)).toEqual([]);
  });

  // FR6-T1 (review-p1c R6): `RuleValidator` requires an effective date.
  it('requires an effective date', () => {
    const form: ZoneRuleFormModel = {
      ...emptyRuleForm(),
      scheduleType: 'ALWAYS_FORBIDDEN'
    };

    expect(validateRuleForm(form, 1)).toContain('"Effective from" is required.');
  });
});
