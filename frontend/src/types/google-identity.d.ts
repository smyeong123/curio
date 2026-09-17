// Google Identity Services (the `gsi/client` script loaded in index.html)
// attaches itself to `window`. Only the One Tap surface the login page uses
// is typed here.
export {}

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: {
          initialize: (config: {
            client_id: string
            callback: (response: { credential?: string }) => void
          }) => void
          prompt: (momentListener?: (notification: unknown) => void) => void
        }
      }
    }
  }
}
