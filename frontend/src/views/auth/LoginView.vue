<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] lg:grid lg:grid-cols-[1.05fr_0.95fr]">
    <!-- ── Editorial aside ─────────────────────────────────────── -->
    <aside class="relative hidden flex-col bg-ink p-12 text-[color:var(--paper)] lg:flex">
      <router-link to="/" class="flex items-baseline gap-3">
        <span class="display-headline text-[36px] leading-none">Curio</span>
        <span class="kicker" style="color: var(--paper); opacity: 0.6;">Vol. 047</span>
      </router-link>

      <div class="my-auto max-w-[420px]">
        <p class="kicker mb-5" style="color: var(--signal);">{{ t('auth.login.aside.kicker') }}</p>
        <i18n-t scope="global" keypath="auth.login.aside.headline" tag="h2" class="display-headline text-[clamp(40px,5vw,68px)] leading-[0.96] mb-8">
          <template #running>
            <em class="italic-display" style="color: var(--signal);">{{ t('auth.login.aside.running') }}</em>
          </template>
        </i18n-t>
        <p class="font-body-curio text-[15px] opacity-80 leading-relaxed mb-10">
          {{ t('auth.login.aside.body') }}
        </p>

        <div class="space-y-4 border-t border-[color:var(--paper)]/20 pt-6">
          <div v-for="(item, i) in features" :key="item" class="flex items-baseline gap-4">
            <span class="num-tab text-[11px] opacity-60">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="font-display text-[15px] leading-tight">{{ item }}</span>
          </div>
        </div>
      </div>

      <div class="flex items-end justify-between">
        <p class="kicker" style="color: var(--paper); opacity: 0.5;">{{ t('auth.common.colophon') }}</p>
        <div class="flex items-baseline gap-5">
          <LanguageToggle class="text-[11px]" style="color: var(--paper); opacity: 0.6;" />
          <p class="kicker" style="color: var(--paper); opacity: 0.5;">{{ todayShort }}</p>
        </div>
      </div>
    </aside>

    <!-- ── Form panel ─────────────────────────────────────────── -->
    <section class="flex items-center justify-center px-6 py-14">
      <div class="w-full max-w-[460px]">
        <div class="mb-10 flex items-baseline justify-between gap-4 lg:hidden">
          <router-link to="/" class="flex items-baseline gap-3">
            <span class="display-headline text-[28px] leading-none">Curio</span>
            <span class="kicker">Vol. 047</span>
          </router-link>
          <LanguageToggle class="text-[12px]" />
        </div>

        <header class="mb-10">
          <p class="kicker kicker-signal mb-3">{{ t('auth.login.kicker') }}</p>
          <i18n-t scope="global" keypath="auth.login.headline" tag="h1" class="display-headline text-[clamp(40px,5vw,64px)] leading-[0.96] mb-4">
            <template #back><em class="italic-display">{{ t('auth.login.back') }}</em></template>
          </i18n-t>
          <i18n-t scope="global" keypath="auth.login.lede" tag="p" class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
            <template #br><br /></template>
            <template #link>
              <router-link to="/reset-password" class="ink-link">{{ t('auth.login.resetLink') }}</router-link>
            </template>
          </i18n-t>
        </header>

        <LoginForm
          :loading="loading"
          :server-error="serverError"
          @submit="handleLogin"
          @google="handleGoogleLogin"
        />

        <div class="mt-8 pt-6 border-t border-[color:var(--rule)] flex items-baseline justify-between">
          <span class="kicker">{{ t('auth.login.noAccount') }}</span>
          <router-link to="/register" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
            {{ t('auth.login.subscribeLink') }}
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'
import LoginForm from '@/components/auth/LoginForm.vue'
import LanguageToggle from '@/components/ui/LanguageToggle.vue'
import { safeRedirectPath } from '@/utils/safeUrl'
import { getApiErrorMessage } from '@/utils/apiError'

const { t, tm, rt } = useI18n()
const { intlLocale } = useLocale()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { success, error } = useToast()

const loading = ref(false)
const serverError = ref('')

const features = computed(() => (tm('auth.login.aside.features') as string[]).map((line) => rt(line)))

const todayShort = computed(() => {
  const d = new Date()
  return d.toLocaleDateString(intlLocale.value, { month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()
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
      success(t('auth.login.toast.welcome'))
      router.push(redirect || { name: 'archive' })
    } else {
      // A one-time code was emailed — finish on the verify-code step.
      router.push({ name: 'verify-code', query: redirect ? { redirect } : {} })
    }
  } catch (err: unknown) {
    serverError.value = getApiErrorMessage(err, t('auth.login.errors.invalidCredentials'))
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
    error(t('auth.login.errors.googleNotConfigured'))
    return
  }

  if (!window.google?.accounts?.id) {
    error(t('auth.login.errors.googleSdk'))
    return
  }

  loading.value = true
  window.google.accounts.id.initialize({
    client_id: googleClientId,
    callback: async (response: { credential?: string }) => {
      const credential = response.credential
      if (!credential) {
        loading.value = false
        serverError.value = t('auth.login.errors.googleFailed')
        return
      }
      try {
        await authStore.googleLogin(credential)
        success(t('auth.login.toast.welcome'))
        router.push(safeRedirectPath(route.query.redirect) || { name: 'archive' })
      } catch (err: unknown) {
        serverError.value = getApiErrorMessage(err, t('auth.login.errors.googleFailed'))
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
