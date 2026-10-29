import { serverErrorMessage } from './api-error.utils';

describe('serverErrorMessage', () => {
  it('uses the API message that the error interceptor already resolved', () => {
    const error = { message: 'validFrom and validTo are required', status: 400 };

    expect(serverErrorMessage(error, 'Failed to save the permit'))
      .toBe('validFrom and validTo are required');
  });

  it('reads the body of a raw HttpErrorResponse', () => {
    const error = {
      status: 400,
      message: 'Http failure response for /api/v1/vehicle-types/9004: 400 OK',
      error: { errors: [{ code: '0040001', message: 'Unrecognized field "vehicle_features"' }] }
    };

    expect(serverErrorMessage(error, 'Failed to save vehicle type'))
      .toBe('Unrecognized field "vehicle_features"');
  });

  it('reads the original response of the interceptor wrapper', () => {
    const error = {
      message: 'Server error',
      status: 404,
      originalError: { status: 404, error: { errors: [{ message: 'Resource not found.' }] } }
    };

    expect(serverErrorMessage(error, 'Failed to load the zones')).toBe('Resource not found.');
  });

  it('falls back when the error carries no readable text', () => {
    expect(serverErrorMessage({ status: 500 }, 'Failed to save')).toBe('Failed to save');
    expect(serverErrorMessage(undefined, 'Failed to save')).toBe('Failed to save');
    expect(serverErrorMessage({ message: '   ' }, 'Failed to save')).toBe('Failed to save');
  });
});
