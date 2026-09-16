<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] lg:grid lg:grid-cols-[1.05fr_0.95fr]">
    <!-- ── Editorial aside ─────────────────────────────────────── -->
    <aside class="relative hidden flex-col bg-ink p-12 text-[color:var(--paper)] lg:flex">
      <router-link to="/" class="flex items-baseline gap-3">
        <span class="display-headline text-[36px] leading-none">Curio</span>
        <span class="kicker" style="color: var(--paper); opacity: 0.6;">Vol. 047</span>
      </router-link>

      <div class="my-auto max-w-[420px]">
        <p class="kicker mb-5" style="color: var(--signal);">Welcome back</p>
        <h2 class="display-headline text-[clamp(40px,5vw,68px)] leading-[0.96] mb-8">
          The press is
          <em class="italic-display" style="color: var(--signal);">running</em>
          for you.
        </h2>
        <p class="font-body-curio text-[15px] opacity-80 leading-relaxed mb-10">
          Tomorrow's edition is being typeset right now — your beats, your reading order,
          your five-question quiz. Sign in to pick up where you left off.
        </p>

        <div class="space-y-4 border-t border-[color:var(--paper)]/20 pt-6">
          <div v-for="(item, i) in features" :key="item" class="flex items-baseline gap-4">
            <span class="num-tab text-[11px] opacity-60">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="font-display text-[15px] leading-tight">{{ item }}</span>
          </div>
        </div>
      </div>

      <div class="flex items-end justify-between">
        <p class="kicker" style="color: var(--paper); opacity: 0.5;">© 2026 — set in Fraunces</p>
        <p class="kicker" style="color: var(--paper); opacity: 0.5;">{{ todayShort }}</p>
      </div>
    </aside>

    <!-- ── Form panel ─────────────────────────────────────────── -->
    <section class="flex items-center justify-center px-6 py-14">
      <div class="w-full max-w-[460px]">
        <router-link to="/" class="mb-10 flex items-baseline gap-3 lg:hidden">
          <span class="display-headline text-[28px] leading-none">Curio</span>
          <span class="kicker">Vol. 047</span>
        </router-link>

        <header class="mb-10">
          <p class="kicker kicker-signal mb-3">Sign in</p>
          <h1 class="display-headline text-[clamp(40px,5vw,64px)] leading-[0.96] mb-4">
            Welcome
            <em class="italic-display">back</em>.
          </h1>
          <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
            Continue your daily briefing.<br />
            Forgot your password?
            <router-link to="/reset-password" class="ink-link">Reset it →</router-link>
          </p>
        </header>

        <LoginForm
          :loading="loading"
          :server-error="serverError"
          @submit="handleLogin"
          @google="handleGoogleLogin"
        />

        <div class="mt-8 pt-6 border-t border-[color:var(--rule)] flex items-baseline justify-between">
          <span class="kicker">No account yet?</span>
          <router-link to="/register" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
            Subscribe →
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import LoginForm from '@/components/auth/LoginForm.vue'
import { safeRedirectPath } from '@/utils/safeUrl'
import { getApiErrorMessage } from '@/utils/apiError'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { success, error } = useToast()

const loading = ref(false)
const serverError = ref('')

const features = [
  'A daily five-minute brief on AI model news',
  'Summaries written by Claude, not aggregated',
  'A five-question quiz — built for retention',
] as const

const todayShort = computed(() => {
  const d = new Date()
  return d.toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()
})

const handleLogin = async (email: string, password: string) => {
  loading.value = true
  serverError.value = ''
  try {
    const result = await authStore.login(email, password)
    // Only honor a same-origin in-app path — never an attacker-supplied absolute
    // or protocol-relative URL (open-redirect defense).
    const redirect = safeRedirectPath(route.query.redirect)
    if (result.status === 'AUTHENTICATED') {
      success('Welcome back!')
      router.push(redirect || { name: 'archive' })
    } else {
      // A one-time code was emailed — finish on the verify-code step.
      router.push({ name: 'verify-code', query: redirect ? { redirect } : {} })
    }
  } catch (err: unknown) {
    serverError.value = getApiErrorMessage(err, 'Invalid email or password')
  } finally {
    loading.value = false
  }
}

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

const handleGoogleLogin = () => {
  serverError.value = ''
  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID

  if (!googleClientId || googleClientId === 'your-google-client-id') {
    error('Google login is not configured.')
    return
  }

  if (!window.google?.accounts?.id) {
    error('Google SDK did not load. Refresh and try again.')
    return
  }

  loading.value = true
  window.google.accounts.id.initialize({
    client_id: googleClientId,
    callback: async (response: { credential?: string }) => {
      const credential = response.credential
      if (!credential) {
        loading.value = false
        serverError.value = 'Google sign-in failed'
        return
      }
      try {
        await authStore.googleLogin(credential)
        success('Welcome back!')
        router.push(safeRedirectPath(route.query.redirect) || { name: 'archive' })
      } catch (err: unknown) {
        serverError.value = getApiErrorMessage(err, 'Google sign-in failed')
      } finally {
        loading.value = false
      }
    },
  })
  window.google.accounts.id.prompt((notification: unknown) => {
    const n = notification as { isNotDisplayed?: () => boolean; isSkippedMoment?: () => boolean }
    if (n?.isNotDisplayed?.() || n?.isSkippedMoment?.()) {
      loading.value = false
    }
  })
}
</script>
