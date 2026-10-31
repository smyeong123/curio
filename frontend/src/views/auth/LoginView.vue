<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] lg:grid lg:grid-cols-[1.05fr_0.95fr]">
    <!-- ── Editorial aside ─────────────────────────────────────── -->
    <aside class="dot-block relative hidden flex-col p-12 lg:flex">
      <AuthMasthead on-ink />

      <div class="my-auto max-w-[420px]">
        <p class="kicker kicker-signal mb-5">{{ t('auth.login.aside.kicker') }}</p>
        <i18n-t scope="global" keypath="auth.login.aside.headline" tag="h2" class="rise-mask rise-d-1 display-headline text-[clamp(40px,5vw,68px)] leading-[0.96] mb-8">
          <template #running>
            <em class="italic-display">{{ t('auth.login.aside.running') }}</em>
          </template>
        </i18n-t>
        <p class="font-body-curio text-[15px] opacity-80 leading-relaxed mb-10">
          {{ t('auth.login.aside.body') }}
        </p>

        <div class="space-y-4 border-t border-white/30 pt-6">
          <div v-for="(item, i) in features" :key="item" class="flex items-baseline gap-4">
            <span class="num-tab text-[11px] opacity-60">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="font-display text-[15px] leading-tight">{{ item }}</span>
          </div>
        </div>
      </div>

      <AuthColophon />
    </aside>

    <!-- ── Form panel ─────────────────────────────────────────── -->
    <section class="flex items-center justify-center px-6 py-14">
      <div class="w-full max-w-[460px]">
        <AuthMasthead class="lg:hidden" />

        <header class="mb-10">
          <p class="kicker kicker-signal mb-3">{{ t('auth.login.kicker') }}</p>
          <i18n-t scope="global" keypath="auth.login.headline" tag="h1" class="rise-mask rise-d-2 display-headline text-[clamp(40px,5vw,64px)] leading-[0.96] mb-4">
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
import AuthMasthead from '@/components/auth/AuthMasthead.vue'
import AuthColophon from '@/components/auth/AuthColophon.vue'
import LoginForm from '@/components/auth/LoginForm.vue'
import { safeRedirectPath } from '@/utils/safeUrl'
import { getApiErrorMessage } from '@/utils/apiError'
import { requestGoogleCredential, GoogleCredentialError } from '@/utils/googleCredential'

const { t, tm, rt } = useI18n()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { success, error } = useToast()

const loading = ref(false)
const serverError = ref('')

const features = computed(() => (tm('auth.login.aside.features') as string[]).map((line) => rt(line)))

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

const handleGoogleLogin = async () => {
  serverError.value = ''
  let credential: string
  try {
    loading.value = true
    credential = await requestGoogleCredential()
  } catch (err: unknown) {
    loading.value = false
    const reason = err instanceof GoogleCredentialError ? err.reason : 'no_credential'
    if (reason === 'not_configured') error(t('auth.login.errors.googleNotConfigured'))
    else if (reason === 'sdk_missing') error(t('auth.login.errors.googleSdk'))
    else if (reason === 'no_credential') serverError.value = t('auth.login.errors.googleFailed')
    // 'dismissed': the user closed One Tap — nothing to report.
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
}
</script>
