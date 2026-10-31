// Google Identity Services One Tap, wrapped as a promise. Used to sign in
// (LoginView) and to re-confirm a Google-only account before a destructive
// action (account deletion), where the backend checks the fresh ID token
// belongs to the same Google account.

export type GoogleCredentialFailure =
  | 'not_configured' // VITE_GOOGLE_CLIENT_ID missing
  | 'sdk_missing' // the gsi/client script didn't load
  | 'dismissed' // One Tap was closed or couldn't be shown
  | 'no_credential' // Google answered without a token

export class GoogleCredentialError extends Error {
  readonly reason: GoogleCredentialFailure

  constructor(reason: GoogleCredentialFailure) {
    super(`google_credential_${reason}`)
    this.reason = reason
  }
}

export function requestGoogleCredential(): Promise<string> {
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID
  if (!clientId || clientId === 'your-google-client-id') {
    return Promise.reject(new GoogleCredentialError('not_configured'))
  }
  const gsi = window.google?.accounts?.id
  if (!gsi) {
    return Promise.reject(new GoogleCredentialError('sdk_missing'))
  }
  return new Promise((resolve, reject) => {
    gsi.initialize({
      client_id: clientId,
      callback: (response: { credential?: string }) => {
        if (response.credential) resolve(response.credential)
        else reject(new GoogleCredentialError('no_credential'))
      },
    })
    gsi.prompt((notification: unknown) => {
      const n = notification as {
        isNotDisplayed?: () => boolean
        isSkippedMoment?: () => boolean
        isDismissedMoment?: () => boolean
      }
      // A dismissed moment also fires after a successful credential; by then the
      // promise has resolved and this reject is a no-op. Otherwise (closed with X)
      // it must settle, or callers' loading state hangs forever.
      if (n?.isNotDisplayed?.() || n?.isSkippedMoment?.() || n?.isDismissedMoment?.()) {
        reject(new GoogleCredentialError('dismissed'))
      }
    })
  })
}
