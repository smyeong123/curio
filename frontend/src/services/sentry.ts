/**
 * Frontend Sentry scaffold.
 *
 * To enable:
 *   npm install --save @sentry/vue
 *   set VITE_SENTRY_DSN in .env.local
 *   restart dev server
 *
 * Without the package installed or the DSN set, this is a no-op.
 */
import type { App } from 'vue'
import type { Router } from 'vue-router'

type SentryModule = {
  init: (options: Record<string, unknown>) => void
  browserTracingIntegration: (options: Record<string, unknown>) => unknown
}

// One-time/short-lived credentials that must never leave the browser in a
// Sentry event: the emailed 2FA code and the password-reset token both ride in
// URL query strings, and the access token rides the Authorization header.
const REDACTED = '[redacted]'
const SENSITIVE_QUERY_KEYS = ['code', 'token']

/** Replace ?code=…/?token=… in any URL-ish string with a redacted marker. */
function redactUrl(value: string): string {
  return value.replace(
    /([?&])(code|token)=[^&#]*/gi,
    (_m, sep, key) => `${sep}${key}=${REDACTED}`
  )
}

/** Drop auth headers and redact sensitive query params from a Sentry event. */
function scrubEvent(event: Record<string, unknown>): Record<string, unknown> {
  const request = event.request as
    | { url?: string; headers?: Record<string, unknown>; query_string?: unknown }
    | undefined
  if (request) {
    if (typeof request.url === 'string') request.url = redactUrl(request.url)
    if (request.headers) {
      delete request.headers.Authorization
      delete request.headers.authorization
      delete request.headers.Cookie
      delete request.headers.cookie
    }
    if (typeof request.query_string === 'string') {
      request.query_string = redactUrl(`?${request.query_string}`).slice(1)
    }
  }
  return event
}

export async function initSentry(app: App, router: Router): Promise<void> {
  const dsn = import.meta.env.VITE_SENTRY_DSN
  if (!dsn) return

  try {
    // Hide the spec from Vite's static dep scanner; dep is opt-in via npm install.
    const spec = ['@sentry', 'vue'].join('/')
    const mod = (await import(/* @vite-ignore */ spec)) as SentryModule
    mod.init({
      app,
      dsn,
      environment: import.meta.env.MODE,
      tracesSampleRate: 0.1,
      // Never attach cookies/IP or other PII to events.
      sendDefaultPii: false,
      integrations: [mod.browserTracingIntegration({ router })],
      // Strip auth headers + ?code=/?token= from the event payload…
      beforeSend: (event: Record<string, unknown>) => scrubEvent(event),
      // …and from navigation/fetch/xhr breadcrumbs, whose URLs would otherwise
      // capture the verify deep link and the reset link.
      beforeBreadcrumb: (breadcrumb: Record<string, unknown>) => {
        const data = breadcrumb.data as Record<string, unknown> | undefined
        if (data && typeof data.url === 'string') data.url = redactUrl(data.url)
        if (typeof breadcrumb.message === 'string') {
          breadcrumb.message = redactUrl(breadcrumb.message)
        }
        return breadcrumb
      }
    })
  } catch (err) {
    console.warn('[sentry] init skipped — @sentry/vue not installed or failed to load', err)
  }
}

// Exported for unit tests; safe to ignore at runtime.
export const __sentryTestHooks = { redactUrl, scrubEvent, SENSITIVE_QUERY_KEYS }
