import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './assets/styles/main.css'
import App from './App.vue'
import router from './router'
import { useToast } from './composables/useToast'
import { initSentry } from './services/sentry'
import { persistencePlugin } from './stores/persistence'
import { getApiErrorMessage } from '@/utils/apiError'

// Honor a stored theme preference before the first paint so logged-out pages
// (e.g. Home) match the dashboard theme the user picked previously. Without
// this, the homepage flickers in the OS-default theme until the dashboard
// mounts and applies the stored value.
;(function applyStoredTheme() {
  try {
    const stored = localStorage.getItem('curio:theme')
    if (stored === 'dark' || stored === 'light') {
      document.documentElement.dataset.theme = stored
      document.documentElement.classList.toggle('dark', stored === 'dark')
    }
  } catch {
    /* ignore storage errors (private mode etc.) */
  }
})()

const app = createApp(App)
const pinia = createPinia()
pinia.use(persistencePlugin)

void initSentry(app, router)

const { error: toastError } = useToast()

const GENERIC_ERROR = 'Something went wrong. Please try again.'

app.config.errorHandler = (err, _instance, info) => {
  console.error('[Vue Error]', err, info)
  // Render/lifecycle errors are internal — their raw message is a debugging
  // aid, not user copy. Surface it in dev; show a generic message in prod
  // (Sentry still receives the full error).
  toastError(readableMessage(err) ?? GENERIC_ERROR)
}

window.addEventListener('unhandledrejection', (event) => {
  console.error('[Unhandled Promise]', event.reason)
  const message = readableMessage(event.reason)
  if (message) {
    toastError(message)
  }
})

window.addEventListener('error', (event) => {
  console.error('[Uncaught Error]', event.error ?? event.message)
})

function readableMessage(err: unknown): string | null {
  if (!err) return null
  const anyErr = err as { message?: string }
  // API error messages come from our backend's GlobalExceptionHandler and are
  // written for users — always safe to show.
  const apiMessage = getApiErrorMessage(err, '')
  if (apiMessage) return apiMessage
  // Anything else (TypeError, ReferenceError, plain throws) is internal detail:
  // show it only in dev builds, never in production.
  if (!import.meta.env.DEV) return null
  if (typeof err === 'string') return err
  if (err instanceof Error) return err.message
  return anyErr?.message ?? null
}

app.use(pinia)
app.use(router)
app.mount('#app')
