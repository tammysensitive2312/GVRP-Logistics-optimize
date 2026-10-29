import {
  DayOfWeekCode,
  ENERGY_TYPES,
  EnergyType,
  LEGAL_CLASSES,
  LegalClass,
  RuleSchedule,
  RuleSelector,
  ScheduleType,
  WeightBounds,
  WeightMeasure,
  ZoneRuleDTO,
  ZoneRuleInput
} from '@core/models';
import { hhMmToMinutes } from '@shared/utils/lez-format.utils';
import { localDateIso } from '@shared/utils/date.utils';

/** Mirrors `RuleValidator` / `ZoneService` limits on the server. */
export const MAX_RULES = 50;
export const MAX_INTERVALS = 48;

export interface RuleIntervalForm {
  start: string;
  end: string;
}

/**
 * Editable form model of one COPIED-zone rule (D7).
 *
 * Deliberately flat and free of Angular types: the mapping to and from the wire
 * is pure, so the rules-of-the-form can be tested without a component.
 */
export interface ZoneRuleFormModel {
  legalClasses: LegalClass[];
  energyTypes: EnergyType[];
  weightMeasure: WeightMeasure | null;
  weightGt: number | null;
  weightGte: number | null;
  weightLt: number | null;
  weightLte: number | null;
  emissionStandardBelow: number | null;
  appBased: boolean;
  scheduleType: ScheduleType;
  daysOfWeek: DayOfWeekCode[];
  intervals: RuleIntervalForm[];
  effectiveFrom: string;
  effectiveTo: string;
  legalBasis: string;
  note: string;
}

export function emptyRuleForm(): ZoneRuleFormModel {
  return {
    legalClasses: [],
    energyTypes: [],
    weightMeasure: null,
    weightGt: null,
    weightGte: null,
    weightLt: null,
    weightLte: null,
    emissionStandardBelow: null,
    appBased: false,
    scheduleType: 'ALWAYS_FORBIDDEN',
    daysOfWeek: [],
    intervals: [],
    effectiveFrom: '',
    effectiveTo: '',
    legalBasis: '',
    note: ''
  };
}

export function hasWeightBound(form: ZoneRuleFormModel): boolean {
  return form.weightGt != null
    || form.weightGte != null
    || form.weightLt != null
    || form.weightLte != null;
}

/** True when the rule constrains no vehicle field: it then applies to every vehicle. */
export function isSelectorEmpty(form: ZoneRuleFormModel): boolean {
  return form.legalClasses.length === 0
    && form.energyTypes.length === 0
    && form.weightMeasure == null
    && !hasWeightBound(form)
    && form.emissionStandardBelow == null
    && !form.appBased;
}

/** Form → request body. Absent selector fields are omitted (absent = no constraint). */
export function toRuleInput(form: ZoneRuleFormModel): ZoneRuleInput {
  const selector: RuleSelector = {};

  if (form.legalClasses.length > 0) {
    selector.legalClasses = [...form.legalClasses];
  }
  if (form.energyTypes.length > 0) {
    selector.energyTypes = [...form.energyTypes];
  }
  if (form.weightMeasure != null && hasWeightBound(form)) {
    const weightKg: WeightBounds = {};
    if (form.weightGt != null) weightKg.gt = form.weightGt;
    if (form.weightGte != null) weightKg.gte = form.weightGte;
    if (form.weightLt != null) weightKg.lt = form.weightLt;
    if (form.weightLte != null) weightKg.lte = form.weightLte;
    selector.weightMeasure = form.weightMeasure;
    selector.weightKg = weightKg;
  }
  if (form.emissionStandardBelow != null) {
    selector.emissionStandardBelow = form.emissionStandardBelow;
  }
  if (form.appBased) {
    selector.appBased = true;
  }

  const schedule: RuleSchedule = { type: form.scheduleType };
  if (form.daysOfWeek.length > 0) {
    schedule.daysOfWeek = [...form.daysOfWeek];
  }
  if (form.scheduleType !== 'ALWAYS_FORBIDDEN') {
    schedule.intervals = form.intervals.map(interval => [interval.start, interval.end]);
  }

  return {
    selector,
    schedule,
    effective_from: blankToNull(form.effectiveFrom),
    effective_to: blankToNull(form.effectiveTo),
    legal_basis: blankToNull(form.legalBasis),
    note: blankToNull(form.note)
  };
}

/** A stored rule → the form model (round trip of {@link toRuleInput}). */
export function fromRuleDTO(rule: ZoneRuleDTO): ZoneRuleFormModel {
  const selector = rule.selector ?? {};
  const schedule = rule.schedule ?? null;

  return {
    legalClasses: [...(selector.legalClasses ?? [])],
    energyTypes: [...(selector.energyTypes ?? [])],
    weightMeasure: selector.weightMeasure ?? null,
    weightGt: selector.weightKg?.gt ?? null,
    weightGte: selector.weightKg?.gte ?? null,
    weightLt: selector.weightKg?.lt ?? null,
    weightLte: selector.weightKg?.lte ?? null,
    emissionStandardBelow: selector.emissionStandardBelow ?? null,
    appBased: selector.appBased === true,
    scheduleType: schedule?.type ?? 'ALWAYS_FORBIDDEN',
    daysOfWeek: [...(schedule?.daysOfWeek ?? [])],
    intervals: (schedule?.intervals ?? []).map(([start, end]) => ({ start, end })),
    effectiveFrom: rule.effective_from ?? '',
    effectiveTo: rule.effective_to ?? '',
    legalBasis: rule.legal_basis ?? '',
    note: rule.note ?? ''
  };
}

/**
 * Client-side mirror of `RuleValidator`. The server stays the authority; these
 * messages only spare a round trip for the obvious mistakes (design-p1c §3.11).
 */
export function validateRuleForm(form: ZoneRuleFormModel, ruleCount: number): string[] {
  const errors: string[] = [];

  if (ruleCount > MAX_RULES) {
    errors.push(`A zone may have at most ${MAX_RULES} rules.`);
  }
  if (form.intervals.length > MAX_INTERVALS) {
    errors.push(`A rule may have at most ${MAX_INTERVALS} intervals.`);
  }

  if (hasWeightBound(form) && form.weightMeasure == null) {
    errors.push('Choose what the weight bound reads (gross weight or payload).');
  }
  if (form.weightMeasure != null && !hasWeightBound(form)) {
    errors.push('Add at least one weight bound.');
  }
  for (const bound of [form.weightGt, form.weightGte, form.weightLt, form.weightLte]) {
    if (bound != null && (!Number.isInteger(bound) || bound < 0)) {
      errors.push('Weight bounds must be non-negative integers.');
      break;
    }
  }
  if (form.emissionStandardBelow != null
    && (!Number.isInteger(form.emissionStandardBelow) || form.emissionStandardBelow < 0)) {
    errors.push('Emission standard must be a non-negative integer.');
  }

  if (form.scheduleType !== 'ALWAYS_FORBIDDEN' && form.intervals.length === 0) {
    errors.push('Add at least one interval.');
  }
  for (const interval of form.intervals) {
    const start = hhMmToMinutes(interval.start);
    const end = hhMmToMinutes(interval.end);
    if (start == null || end == null) {
      errors.push('Times must be HH:mm (24:00 allowed only as an end).');
      continue;
    }
    if (start === 24 * 60) {
      errors.push('24:00 can only be an end.');
    }
    if (start === end) {
      errors.push(`An interval may not start and end at the same time (${interval.start}).`);
    }
  }

  if (form.effectiveFrom && form.effectiveTo && form.effectiveTo < form.effectiveFrom) {
    errors.push('"Effective to" must be on or after "Effective from".');
  }
  // `RuleValidator` requires an effective date; mirror it (review-p1c R6).
  if (!form.effectiveFrom.trim()) {
    errors.push('"Effective from" is required.');
  }

  return [...new Set(errors)];
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

// ---------- presets and warnings (UX-LEZ-001, D5/D6) ----------

interface RulePreset {
  label: string;
  /** Replaces the selector and schedule; keeps dates, legal basis and note. */
  apply(form: ZoneRuleFormModel): ZoneRuleFormModel;
}

/** Selector and schedule of a preset applied over the kept fields of `form`. */
function presetOver(
  form: ZoneRuleFormModel,
  patch: Partial<ZoneRuleFormModel>
): ZoneRuleFormModel {
  return {
    ...emptyRuleForm(),
    ...patch,
    effectiveFrom: form.effectiveFrom.trim() ? form.effectiveFrom : localDateIso(),
    effectiveTo: form.effectiveTo,
    legalBasis: form.legalBasis,
    note: form.note
  };
}

export const RULE_PRESETS: readonly RulePreset[] = [
  {
    label: 'Trucks over 3.5 t, peak hours',
    apply: form => presetOver(form, {
      legalClasses: ['TRUCK'],
      weightMeasure: 'GROSS',
      weightGt: 3500,
      scheduleType: 'FORBIDDEN_DURING',
      intervals: [{ start: '06:00', end: '09:00' }, { start: '16:00', end: '19:30' }]
    })
  },
  {
    label: 'Below Euro 4, all day',
    apply: form => presetOver(form, {
      emissionStandardBelow: 4,
      scheduleType: 'ALWAYS_FORBIDDEN'
    })
  },
  {
    label: 'Only electric vehicles',
    apply: form => presetOver(form, {
      energyTypes: ['HYBRID', 'PETROL', 'DIESEL', 'OTHER'],
      scheduleType: 'ALWAYS_FORBIDDEN'
    })
  },
  {
    label: 'Motorbikes, all day',
    apply: form => presetOver(form, {
      legalClasses: ['MOTORBIKE'],
      scheduleType: 'ALWAYS_FORBIDDEN'
    })
  }
];

/**
 * Non-blocking hints about a rule that can never or always match by mistake.
 * Never used to refuse a save; `validateRuleForm` stays the only gate.
 */
export function ruleWarnings(form: ZoneRuleFormModel, today: string = localDateIso()): string[] {
  const warnings: string[] = [];
  const below = form.emissionStandardBelow;
  if (below != null && below <= 1) {
    warnings.push('No vehicle has an emission standard below 1; this rule never matches on emissions.');
  }
  if (below != null && below > 6) {
    warnings.push('Euro levels go up to 6; every vehicle with a known standard matches.');
  }
  if (LEGAL_CLASSES.every(value => form.legalClasses.includes(value))) {
    warnings.push('All classes are ticked; this is the same as leaving them empty.');
  }
  if (ENERGY_TYPES.every(value => form.energyTypes.includes(value))) {
    warnings.push('All energy types are ticked; this is the same as leaving them empty.');
  }
  if (form.effectiveTo && form.effectiveTo < today) {
    warnings.push('This rule has already ended.');
  }
  return warnings;
}
