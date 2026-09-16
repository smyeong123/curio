<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] flex items-center justify-center px-6 py-14">
    <div class="w-full max-w-[480px]">
      <div class="mb-10 flex items-baseline justify-between gap-4">
        <router-link to="/" class="flex items-baseline gap-3">
          <span class="display-headline text-[28px] leading-none">Curio</span>
          <span class="kicker">Vol. 047</span>
        </router-link>
        <LanguageToggle class="text-[12px]" />
      </div>

      <header class="mb-10">
        <p class="kicker kicker-signal mb-3">{{ hasToken ? t('auth.reset.choose.kicker') : t('auth.reset.request.kicker') }}</p>
        <h1 class="display-headline text-[clamp(36px,5vw,52px)] leading-[0.96] mb-3">
          <i18n-t v-if="hasToken" scope="global" keypath="auth.reset.choose.headline">
            <template #password><em class="italic-display">{{ t('auth.reset.choose.password') }}</em></template>
          </i18n-t>
          <i18n-t v-else scope="global" keypath="auth.reset.request.headline">
            <template #password><em class="italic-display">{{ t('auth.reset.request.password') }}</em></template>
          </i18n-t>
        </h1>
        <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
          {{ hasToken ? t('auth.reset.choose.lede') : t('auth.reset.request.lede') }}
        </p>
      </header>

      <!-- Sent confirmation -->
      <section
        v-if="sent && !hasToken"
        class="border border-[color:var(--leaf)] bg-paper-deep p-6"
      >
        <p class="kicker mb-3" style="color: var(--leaf);">{{ t('auth.common.mailSentKicker') }}</p>
        <i18n-t scope="global" keypath="auth.common.mailSentHeadline" tag="p" class="display-headline text-[22px] leading-tight mb-2">
          <template #inbox><em class="italic-display">{{ t('auth.common.inbox') }}</em></template>
        </i18n-t>
        <i18n-t scope="global" keypath="auth.reset.sent.body" tag="p" class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 leading-relaxed">
          <template #email><strong class="font-semibold text-[color:var(--ink)]">{{ email }}</strong></template>
        </i18n-t>
        <router-link to="/login" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
          {{ t('auth.common.backToSignIn') }}
        </router-link>
      </section>

      <!-- Reset complete -->
      <section
        v-else-if="passwordResetComplete"
        class="border border-[color:var(--leaf)] bg-paper-deep p-6"
      >
        <p class="kicker mb-3" style="color: var(--leaf);">{{ t('auth.reset.done.kicker') }}</p>
        <i18n-t scope="global" keypath="auth.reset.done.headline" tag="p" class="display-headline text-[22px] leading-tight mb-2">
          <template #set><em class="italic-display">{{ t('auth.reset.done.set') }}</em></template>
        </i18n-t>
        <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6">
          {{ t('auth.reset.done.body') }}
        </p>
        <router-link to="/login" class="btn-editorial">
          {{ t('auth.reset.done.signIn') }}
        </router-link>
      </section>

      <!-- Choose new password -->
      <form v-else-if="hasToken" @submit.prevent="handlePasswordReset" class="space-y-7">
        <BaseInput
          v-model="newPassword"
          :label="t('auth.reset.choose.newPasswordLabel')"
          type="password"
          :placeholder="t('auth.form.passwordMinPlaceholder')"
          :error="newPasswordError"
          id="new-password"
        />
        <BaseInput
          v-model="confirmPassword"
          :label="t('auth.form.confirmPasswordLabel')"
          type="password"
          :placeholder="t('auth.form.confirmPasswordPlaceholder')"
          :error="confirmPasswordError"
          id="confirm-password"
        />
        <button type="submit" class="btn-editorial w-full justify-center py-4" :disabled="loading">
          <span v-if="loading">{{ t('auth.reset.choose.submitting') }}</span>
          <span v-else>{{ t('auth.reset.choose.submit') }}</span>
          <span aria-hidden="true">→</span>
        </button>
      </form>

      <!-- Request reset -->
      <form v-else @submit.prevent="handleRequestReset" class="space-y-7">
        <BaseInput
          v-model="email"
          :label="t('auth.form.emailLabel')"
          type="email"
          :placeholder="t('auth.form.emailPlaceholder')"
          :error="emailError"
          id="reset-email"
        />
        <button type="submit" class="btn-editorial w-full justify-center py-4" :disabled="loading">
          <span v-if="loading">{{ t('auth.reset.request.submitting') }}</span>
          <span v-else>{{ t('auth.reset.request.submit') }}</span>
          <span aria-hidden="true">→</span>
        </button>
      </form>

      <p v-if="!sent && !passwordResetComplete" class="mt-8 pt-6 border-t border-[color:var(--rule)] text-center">
        <router-link to="/login" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
          {{ t('auth.common.backToSignIn') }}
        </router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import BaseInput from '@/components/ui/BaseInput.vue'
import LanguageToggle from '@/components/ui/LanguageToggle.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { success, error } = useToast()

const token = computed(() => {
  const value = route.query.token
  return typeof value === 'string' ? value : ''
})
const hasToken = computed(() => token.value.length > 0)

const email = ref('')
const emailError = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const newPasswordError = ref('')
const confirmPasswordError = ref('')
const loading = ref(false)
const sent = ref(false)
const passwordResetComplete = ref(false)

const handleRequestReset = async () => {
  emailError.value = ''
  if (!email.value) {
    emailError.value = t('auth.form.errors.emailRequired')
    return
  }
  loading.value = true
  try {
    await api.auth.forgotPassword(email.value)
    sent.value = true
  } catch {
    error(t('auth.reset.errors.sendFailed'))
  } finally {
    loading.value = false
  }
}

const handlePasswordReset = async () => {
  newPasswordError.value = ''
  confirmPasswordError.value = ''

  if (!newPassword.value) {
    newPasswordError.value = t('auth.reset.errors.newPasswordRequired')
    return
  }
  if (newPassword.value.length < 8) {
    newPasswordError.value = t('auth.form.errors.passwordTooShort')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    confirmPasswordError.value = t('auth.form.errors.passwordMismatch')
    return
  }

  loading.value = true
  try {
    await api.auth.resetPassword(token.value, newPassword.value)
    passwordResetComplete.value = true
    // Strip the single-use token from the URL/history once consumed, so it
    // can't linger as document.referrer or be replayed from browser history
    // (mirrors VerifyCodeView's handling of the emailed code).
    router.replace({ query: {} })
    success(t('auth.reset.toast.success'))
  } catch {
    error(t('auth.reset.errors.invalidToken'))
  } finally {
    loading.value = false
  }
}
</script>
