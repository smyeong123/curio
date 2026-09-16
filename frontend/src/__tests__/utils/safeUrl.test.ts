import { describe, it, expect } from 'vitest'
import { safeExternalUrl, safeRedirectPath } from '@/utils/safeUrl'

describe('safeExternalUrl', () => {
  it('allows http and https', () => {
    expect(safeExternalUrl('https://example.com/x')).toBe('https://example.com/x')
    expect(safeExternalUrl('http://example.com')).toBe('http://example.com')
  })

  it('allows scheme case-insensitively and trims', () => {
    expect(safeExternalUrl('  HTTPS://Example.com  ')).toBe('HTTPS://Example.com')
  })

  it('rejects javascript: and data: and other dangerous schemes', () => {
    expect(safeExternalUrl('javascript:alert(1)')).toBeNull()
    expect(safeExternalUrl('JavaScript:alert(1)')).toBeNull()
    expect(safeExternalUrl(' javascript:alert(1)')).toBeNull()
    expect(safeExternalUrl('data:text/html,<script>alert(1)</script>')).toBeNull()
    expect(safeExternalUrl('vbscript:msgbox(1)')).toBeNull()
    expect(safeExternalUrl('file:///etc/passwd')).toBeNull()
  })

  it('rejects protocol-relative and scheme-less values', () => {
    expect(safeExternalUrl('//evil.tld')).toBeNull()
    expect(safeExternalUrl('/dashboard')).toBeNull()
    expect(safeExternalUrl('example.com')).toBeNull()
  })

  it('rejects null, undefined, and blank', () => {
    expect(safeExternalUrl(null)).toBeNull()
    expect(safeExternalUrl(undefined)).toBeNull()
    expect(safeExternalUrl('   ')).toBeNull()
  })
})

describe('safeRedirectPath', () => {
  it('allows single-slash in-app paths', () => {
    expect(safeRedirectPath('/dashboard/archive')).toBe('/dashboard/archive')
    expect(safeRedirectPath('/admin/users?page=2')).toBe('/admin/users?page=2')
  })
  it('rejects protocol-relative and absolute URLs', () => {
    expect(safeRedirectPath('//evil.tld')).toBeNull()
    expect(safeRedirectPath('https://evil.tld')).toBeNull()
    expect(safeRedirectPath('http://evil.tld/x')).toBeNull()
  })
  it('rejects scheme and non-slash values', () => {
    expect(safeRedirectPath('javascript:alert(1)')).toBeNull()
    expect(safeRedirectPath('/\\evil.tld')).toBeNull()
    expect(safeRedirectPath('dashboard')).toBeNull()
    expect(safeRedirectPath('')).toBeNull()
    expect(safeRedirectPath(undefined)).toBeNull()
    expect(safeRedirectPath(['/x'])).toBeNull()
  })
})
