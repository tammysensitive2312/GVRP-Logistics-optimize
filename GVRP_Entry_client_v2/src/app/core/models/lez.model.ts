/**
 * Wire models of the low-emission-zone APIs (LEZ-001 P1c).
 *
 * Keys are exactly as the Entry API sends them: snake_case outside, camelCase
 * inside `selector`/`schedule` (those two are stored as raw JSON with explicit
 * `@JsonProperty` on the server, P1a deviation 3).
 */

export type BranchZoneMode = 'LINKED' | 'COPIED';

export type LegalClass = 'MOTORBIKE' | 'CAR' | 'TRUCK' | 'BUS' | 'NON_MOTORIZED';

export type EnergyType = 'ELECTRIC' | 'HYBRID' | 'PETROL' | 'DIESEL' | 'OTHER';

export type WeightMeasure = 'GROSS' | 'PAYLOAD';

export type ScheduleType = 'ALWAYS_FORBIDDEN' | 'FORBIDDEN_DURING' | 'ALLOWED_ONLY';

export type DayOfWeekCode = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN';

export type BoundaryConfidence = string;

/** Every present field must match the vehicle (AND); absent means no constraint. */
export interface RuleSelector {
  legalClasses?: LegalClass[];
  energyTypes?: EnergyType[];
  weightMeasure?: WeightMeasure;
  weightKg?: WeightBounds;
  emissionStandardBelow?: number;
  appBased?: boolean;
}

/** Weight bounds in kilograms; every present bound must hold. */
export interface WeightBounds {
  gt?: number;
  gte?: number;
  lt?: number;
  lte?: number;
}

/** `["HH:mm","HH:mm"]` pairs; `24:00` only as an end; absent days = every day. */
export interface RuleSchedule {
  type: ScheduleType;
  daysOfWeek?: DayOfWeekCode[];
  intervals?: [string, string][];
}

export interface ZoneRuleDTO {
  id?: number;
  template_version?: number | null;
  selector: RuleSelector;
  schedule?: RuleSchedule | null;
  effective_from?: string | null;
  effective_to?: string | null;
  legal_basis?: string | null;
  note?: string | null;
}

/** Body of `PUT /zones/{id}/rules`: the whole list replaces the previous one. */
export interface ZoneRuleInput {
  selector: RuleSelector;
  schedule?: RuleSchedule | null;
  effective_from?: string | null;
  effective_to?: string | null;
  legal_basis?: string | null;
  note?: string | null;
}

export interface ZoneTemplateDTO {
  id: number;
  code: string;
  name: string;
  jurisdiction?: string | null;
  version: number;
  boundary_confidence?: BoundaryConfidence | null;
  boundary_source?: string | null;
  legal_basis?: string | null;
  /** GeoJSON MultiPolygon; detail only. */
  geometry?: unknown;
  /** Current version's rules; detail only. */
  rules?: ZoneRuleDTO[];
}

export interface ZoneWarningDTO {
  code: string;
  depot_id?: number | null;
}

export interface BranchZoneDTO {
  id: number;
  mode: BranchZoneMode;
  template_id: number;
  template_code?: string | null;
  name: string;
  enabled: boolean;
  effective_version?: number | null;
  source_template_version?: number | null;
  acknowledged_template_version?: number | null;
  template_version?: number | null;
  update_available?: boolean | null;
  /** GeoJSON MultiPolygon, x = longitude, y = latitude. */
  geometry?: unknown;
  rules?: ZoneRuleDTO[];
  /** Present only on responses to writes. */
  warnings?: ZoneWarningDTO[] | null;
}

export interface CreateBranchZonePayload {
  template_id: number;
  mode: BranchZoneMode;
}

export interface ZoneVerdictDTO {
  kind: 'ALLOWED' | 'ALL_DAY' | 'PARTIAL';
  /** Forbidden `[start, end]` minute pairs of the day. */
  forbidden: [number, number][];
  missing_attributes: string[];
  applied_permit_ids: number[];
}

export interface VehiclePermitDTO {
  id: number;
  vehicle_id: number;
  branch_zone_id: number;
  permit_no: string;
  /** null = the whole day. */
  schedule?: RuleSchedule | null;
  valid_from?: string | null;
  valid_to?: string | null;
  note?: string | null;
}

export interface VehiclePermitInput {
  branch_zone_id: number;
  permit_no: string;
  /** null = the whole day. `ALWAYS_FORBIDDEN` is refused by the server. */
  schedule: RuleSchedule | null;
  valid_from?: string | null;
  valid_to?: string | null;
  note?: string | null;
}

/** One turn-by-turn instruction; the text is whatever language the engine produced. */
export interface RouteLegInstructionDTO {
  text: string;
  distance_m?: number | null;
  time_ms?: number | null;
  sign?: number | null;
  street_name?: string | null;
}

export interface RouteGeometryLegDTO {
  from_seq: number;
  to_seq: number;
  /** Encoded polyline (precision 1e5, `[lat, lon]`); null when the leg is not routed. */
  polyline?: string | null;
  instructions?: RouteLegInstructionDTO[];
}

export interface RouteGeometryDTO {
  route_id: number;
  legs: RouteGeometryLegDTO[];
}

/**
 * One recorded violation. The server stores it with the Spring `ObjectMapper`
 * (SNAKE_CASE), so the wire keys are these five.
 */
export interface ComplianceViolationDTO {
  code: string;
  /** 0-based plan index of the stop. */
  seq: number;
  order_id?: number | null;
  /** Stable zone key `Z<branchZoneId>`. */
  zone_key?: string | null;
  detail?: string | null;
}

export type ComplianceStatus = 'UNCHECKED' | 'COMPLIANT' | 'VIOLATION' | 'STALE';

export interface RouteComplianceDTO {
  route_id: number;
  vehicle_id?: number | null;
  status: ComplianceStatus;
  checked_at?: string | null;
  violations?: ComplianceViolationDTO[] | null;
}

export interface ZoneRefDTO {
  key: string;
  branch_zone_id: number;
  name: string;
}

export interface LezUnassignedDTO {
  order_id?: number | null;
  order_code?: string | null;
  reason_code: string;
  reason?: string | null;
}

export interface SolutionComplianceDTO {
  solution_id: number;
  routes: RouteComplianceDTO[];
  zones: ZoneRefDTO[];
  lez_unassigned: LezUnassignedDTO[];
}

export interface OrderZoneDTO {
  branch_zone_id: number;
  name: string;
}

export interface OrderZoneMembershipDTO {
  order_id: number;
  zones: OrderZoneDTO[];
}
