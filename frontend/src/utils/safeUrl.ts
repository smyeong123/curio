/**
 * Allow-list an untrusted URL for use in an anchor `href`.
 *
 * Digest `source_url` values are AI-generated (Claude/Gemini/OpenAI summarizing
 * ingested article text — an indirect prompt-injection surface). Binding them
 * straight into `:href` would let a poisoned source emit `javascript:` /
 * `data:` schemes that execute in our origin on click. Only http(s) is allowed;
 * anything else returns null so the caller can drop the link. This mirrors the
 * guard already in the backend digest-email Thymeleaf template.
 */
export function safeExternalUrl(raw: string | null | undefined): string | null {
  if (!raw) return null
  const trimmed = raw.trim()
  const lower = trimmed.toLowerCase()
  if (lower.startsWith('http://') || lower.startsWith('https://')) {
    return trimmed
  }
  return null
}

/**
 * Allow-list a post-login `?redirect=` target to a same-origin in-app path.
 *
 * The value is attacker-supplied (it's whatever was in the URL that bounced to
 * login), so pushing it verbatim risks an open redirect. Only a single-slash
 * absolute path is safe: reject protocol-relative (`//evil.com`), absolute URLs
 * (`https://…`), and scheme-bearing values (`javascript:…`). Returns the fallback
 * route name for anything else.
 */
export function safeRedirectPath(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  const path = raw.trim()
  // Must start with exactly one '/', and contain no scheme or backslash tricks.
  if (!path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) {
    return null
  }
  if (path.includes(':')) return null
  return path
}
