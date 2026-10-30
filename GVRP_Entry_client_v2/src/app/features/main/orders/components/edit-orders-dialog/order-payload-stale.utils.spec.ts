import { clearStaleCoordinates, OrderFormModel } from './order-payload.utils';

/** OF-C1 (ORDERS-FIX-001, C1): when an edit clears the loaded coordinates. */
describe('clearStaleCoordinates', () => {
  const original = { address: '1 Example Street, Ha Noi', latitude: 21.0285, longitude: 105.8542 };

  const form = (patch: Partial<OrderFormModel> = {}): OrderFormModel => ({
    order_code: 'ORD-1',
    customer_name: 'Test Customer',
    address: original.address,
    latitude: original.latitude,
    longitude: original.longitude,
    demand: 10,
    service_time: 5,
    priority: 1,
    ...patch
  });

  it('OF-C1: same address → unchanged (same object)', () => {
    const input = form();

    expect(clearStaleCoordinates(input, original)).toBe(input);
  });

  it('OF-C1: changed address with the loaded coordinates → coordinates cleared', () => {
    const input = form({ address: '2 Other Street, Ha Noi' });

    const result = clearStaleCoordinates(input, original);

    expect(result).not.toBe(input);
    expect(result.latitude).toBeNull();
    expect(result.longitude).toBeNull();
    expect(result.address).toBe('2 Other Street, Ha Noi');
    expect(input.latitude).toBe(original.latitude);
  });

  it('OF-C1: changed address with new coordinates → unchanged', () => {
    const input = form({ address: '2 Other Street, Ha Noi', latitude: 21.04, longitude: 105.86 });

    expect(clearStaleCoordinates(input, original)).toBe(input);
  });

  it('OF-C1: changed address with one coordinate typed as text → unchanged', () => {
    const input = form({ address: '2 Other Street, Ha Noi', latitude: '21.05' });

    expect(clearStaleCoordinates(input, original)).toBe(input);
  });

  it('OF-C1: whitespace-only change of the address → unchanged', () => {
    const input = form({ address: `  ${original.address}  ` });

    expect(clearStaleCoordinates(input, original)).toBe(input);
  });
});
