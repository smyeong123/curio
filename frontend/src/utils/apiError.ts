/**
 * Extract the user-facing message from an API error, falling back to the
 * caller's generic copy. Backend error messages come from GlobalExceptionHandler
 * and are written for users — always safe to show.
 *
 * This is THE place for the `err.response.data.message` shape; don't re-inline
 * the cast in views.
 */
export function getApiErrorMessage(err: unknown, fallback: string): string {
  const anyErr = err as { response?: { data?: { message?: string } } }
  return anyErr?.response?.data?.message || fallback
}

/** HTTP status of an API error, or undefined for network/non-HTTP failures. */
export function getApiErrorStatus(err: unknown): number | undefined {
  const anyErr = err as { response?: { status?: number } }
  return anyErr?.response?.status
}
