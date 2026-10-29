/**
 * The most specific message the Entry API produced, for a toast or a form error.
 *
 * The API answers validation failures with `{ errors: [{ message }] }` and
 * refusals with `{ errors: [...] }` or `{ message }`. Call sites receive the
 * object thrown by `errorInterceptor` (`{ message, status, originalError }`)
 * whose `message` already prefers the API detail, so that text is used first;
 * a raw `HttpErrorResponse` still resolves through its body. An error without a
 * readable body falls back to the caller's text.
 */
export function serverErrorMessage(error: unknown, fallback: string): string {
  const candidate = error as {
    message?: string;
    error?: unknown;
    originalError?: { error?: unknown };
  } | null;

  const fromBody = bodyDetail(candidate?.originalError?.error)
    ?? bodyDetail(candidate?.error);
  if (fromBody) {
    return fromBody;
  }

  const message = candidate?.message;
  if (typeof message === 'string' && message.trim()) {
    return message;
  }
  return fallback;
}

/** First `errors[].message` (or `message`) of an API error body. */
function bodyDetail(body: unknown): string | null {
  if (typeof body === 'string') {
    return body.trim() ? body : null;
  }
  if (typeof body !== 'object' || body === null) {
    return null;
  }

  const envelope = body as {
    errors?: Array<{ message?: unknown }>;
    message?: unknown;
  };

  const first = envelope.errors?.[0]?.message;
  if (typeof first === 'string' && first.trim()) {
    return first;
  }
  if (typeof envelope.message === 'string' && envelope.message.trim()) {
    return envelope.message;
  }
  return null;
}
