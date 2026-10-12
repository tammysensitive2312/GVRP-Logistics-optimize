/**
 * Shared HTTP/backend error message extraction.
 *
 * Returns the error's `message` when it is a non-empty string, else null.
 * Call sites keep their own `?? fallback` user-facing strings.
 */
export function extractErrorMessage(error: unknown): string | null {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim().length > 0) {
      return message;
    }
  }
  return null;
}
