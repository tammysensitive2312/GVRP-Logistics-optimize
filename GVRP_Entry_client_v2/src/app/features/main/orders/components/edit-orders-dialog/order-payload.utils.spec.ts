import { OrderFormModel, toOrderPayload } from './order-payload.utils';

/** UO-C1 (UX-ORDERS-001, C1): the order dialog's request body. */
describe('toOrderPayload', () => {
  const form = (patch: Partial<OrderFormModel> = {}): OrderFormModel => ({
    order_code: ' ORD-1 ',
    customer_name: ' Khach ',
    customer_phone: '',
    address: ' 1 Trang Tien, Ha Noi ',
    latitude: null,
    longitude: null,
    demand: 10,
    service_time: 5,
    time_window_start: '',
    time_window_end: '   ',
    priority: 5,
    delivery_notes: '  ',
    required_skills: [],
    delivery_date: '2026-10-29',
    ...patch
  });

  it('UO-C1: blank optional fields are sent as null', () => {
    const payload = toOrderPayload(form());

    expect(payload.customer_phone).toBeNull();
    expect(payload.delivery_notes).toBeNull();
    expect(payload.time_window_start).toBeNull();
    expect(payload.time_window_end).toBeNull();
  });

  it('UO-C1: empty coordinates are sent as null', () => {
    expect(toOrderPayload(form()).latitude).toBeNull();
    const fromText = toOrderPayload(form({ latitude: '', longitude: '  ' }));
    expect(fromText.latitude).toBeNull();
    expect(fromText.longitude).toBeNull();
    const fromUndefined = toOrderPayload(form({ latitude: undefined as unknown as null }));
    expect(fromUndefined.latitude).toBeNull();
  });

  it('UO-C1: numeric strings become numbers and strings are trimmed', () => {
    const payload = toOrderPayload(form({
      latitude: '21.0245', longitude: '105.8572',
      demand: '12.5' as unknown as number, priority: '2' as unknown as number,
      customer_phone: ' 0901234567 ', time_window_start: '08:00', time_window_end: '11:00'
    }));

    expect(payload.latitude).toBe(21.0245);
    expect(payload.longitude).toBe(105.8572);
    expect(payload.demand).toBe(12.5);
    expect(payload.priority).toBe(2);
    expect(payload.customer_phone).toBe('0901234567');
    expect(payload.order_code).toBe('ORD-1');
    expect(payload.address).toBe('1 Trang Tien, Ha Noi');
    expect(payload.time_window_start).toBe('08:00');
    expect(payload.delivery_date).toBe('2026-10-29');
  });

  it('UO-C1: numeric coordinates are kept, including zero', () => {
    const payload = toOrderPayload(form({ latitude: 0, longitude: 0 }));

    expect(payload.latitude).toBe(0);
    expect(payload.longitude).toBe(0);
  });
});
