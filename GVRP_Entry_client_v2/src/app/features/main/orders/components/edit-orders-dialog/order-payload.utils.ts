import { OrderInputDTO, OrderWritePayload } from '@core/models';

/**
 * The order dialog's form: coordinates may be empty (the server geocodes the
 * address), and number inputs may hand back strings or `null`.
 */
export type OrderFormModel = Omit<OrderInputDTO, 'latitude' | 'longitude'> & {
  latitude: number | string | null;
  longitude: number | string | null;
  status?: string;
  delivery_date?: string;
};

/**
 * Request body of the order dialog (UX-ORDERS-001, C1). Pure: trims strings,
 * sends blank optional fields as `null` (an empty phone used to fail the
 * server's phone pattern, BROWSER-FIX-001 review F1), empty coordinates as
 * `null`, and numeric strings as numbers.
 */
export function toOrderPayload(form: OrderFormModel): OrderWritePayload {
  return {
    ...form,
    order_code: form.order_code?.trim() ?? '',
    customer_name: form.customer_name?.trim() ?? '',
    address: form.address?.trim() ?? '',
    customer_phone: blankToNull(form.customer_phone),
    delivery_notes: blankToNull(form.delivery_notes),
    time_window_start: blankToNull(form.time_window_start),
    time_window_end: blankToNull(form.time_window_end),
    latitude: toNumberOrNull(form.latitude),
    longitude: toNumberOrNull(form.longitude),
    demand: toNumberOrNull(form.demand) ?? form.demand,
    service_time: toNumberOrNull(form.service_time) ?? form.service_time,
    priority: toNumberOrNull(form.priority) ?? form.priority
  };
}

function blankToNull(value: string | null | undefined): string | null {
  if (value == null) {
    return null;
  }
  const trimmed = String(value).trim();
  return trimmed ? trimmed : null;
}

function toNumberOrNull(value: number | string | null | undefined): number | null {
  if (value == null) {
    return null;
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }
  const trimmed = value.trim();
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

/** True when a form coordinate is empty (null, undefined or blank text). */
export function isBlankCoordinate(value: number | string | null | undefined): boolean {
  return value == null || String(value).trim() === '';
}

/** A form coordinate as a number, or null when empty or not a number. */
export function coordinateValue(value: number | string | null | undefined): number | null {
  return toNumberOrNull(value);
}
