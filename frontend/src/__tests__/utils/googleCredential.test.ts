import { afterEach, describe, expect, it, vi } from 'vitest'
import { GoogleCredentialError, requestGoogleCredential } from '@/utils/googleCredential'

type Gsi = NonNullable<NonNullable<NonNullable<Window['google']>['accounts']>['id']>

function installGsi(behaviour: (cb: (r: { credential?: string }) => void, notify: (n: unknown) => void) => void) {
  let callback: (r: { credential?: string }) => void = () => {}
  const gsi: Gsi = {
    initialize: (cfg) => { callback = cfg.callback },
    prompt: (listener) => behaviour(callback, listener ?? (() => {})),
  }
  window.google = { accounts: { id: gsi } }
}

async function reasonOf(p: Promise<string>) {
  try { await p } catch (e) { return (e as GoogleCredentialError).reason }
  return 'resolved'
}

describe('requestGoogleCredential', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    delete window.google
  })

  it('rejects when no client id is configured', async () => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', '')
    expect(await reasonOf(requestGoogleCredential())).toBe('not_configured')
  })

  it('rejects when the Google SDK is missing', async () => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'client-id')
    expect(await reasonOf(requestGoogleCredential())).toBe('sdk_missing')
  })

  it('resolves with the ID token Google returns', async () => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'client-id')
    installGsi((cb) => cb({ credential: 'id-token' }))
    await expect(requestGoogleCredential()).resolves.toBe('id-token')
  })

  it('rejects as dismissed when One Tap is not shown', async () => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'client-id')
    installGsi((_cb, notify) => notify({ isNotDisplayed: () => true }))
    expect(await reasonOf(requestGoogleCredential())).toBe('dismissed')
  })

  it('settles when the user closes One Tap with the X', async () => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'client-id')
    installGsi((_cb, notify) => notify({ isDismissedMoment: () => true }))
    expect(await reasonOf(requestGoogleCredential())).toBe('dismissed')
  })

  it('still resolves when the post-credential dismissed moment fires', async () => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'client-id')
    installGsi((cb, notify) => { cb({ credential: 'id-token' }); notify({ isDismissedMoment: () => true }) })
    await expect(requestGoogleCredential()).resolves.toBe('id-token')
  })

  it('rejects when Google answers without a credential', async () => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'client-id')
    installGsi((cb) => cb({}))
    expect(await reasonOf(requestGoogleCredential())).toBe('no_credential')
  })
})
