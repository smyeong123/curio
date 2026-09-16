<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] flex items-center justify-center px-6 py-14">
    <div class="w-full max-w-[480px]">
      <router-link to="/" class="mb-10 flex items-baseline gap-3">
        <span class="display-headline text-[28px] leading-none">Curio</span>
        <span class="kicker">Vol. 047</span>
      </router-link>

      <header class="mb-10">
        <p class="kicker kicker-signal mb-3">{{ hasToken ? 'New password' : 'Forgot it?' }}</p>
        <h1 class="display-headline text-[clamp(36px,5vw,52px)] leading-[0.96] mb-3">
          <template v-if="hasToken">Choose a new <em class="italic-display">password</em>.</template>
          <template v-else>Reset your <em class="italic-display">password</em>.</template>
        </h1>
        <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
          {{ hasToken
            ? "Pick a fresh password for your account. Eight characters or more."
            : "Tell us your email and we'll send a link." }}
        </p>
      </header>

      <!-- Sent confirmation -->
      <section
        v-if="sent && !hasToken"
        class="border border-[color:var(--leaf)] bg-paper-deep p-6"
      >
        <p class="kicker mb-3" style="color: var(--leaf);">— Mail's away —</p>
        <p class="display-headline text-[22px] leading-tight mb-2">
          Check your <em class="italic-display">inbox</em>.
        </p>
        <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 leading-relaxed">
          If an account exists for <strong class="font-semibold text-[color:var(--ink)]">{{ email }}</strong>,
          you'll receive a reset link shortly.
        </p>
        <router-link to="/login" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
          ← Back to sign in
        </router-link>
      </section>

      <!-- Reset complete -->
      <section
        v-else-if="passwordResetComplete"
        class="border border-[color:var(--leaf)] bg-paper-deep p-6"
      >
        <p class="kicker mb-3" style="color: var(--leaf);">— Updated —</p>
        <p class="display-headline text-[22px] leading-tight mb-2">
          Your password is <em class="italic-display">set</em>.
        </p>
        <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6">
          You can sign in with your new password now.
        </p>
        <router-link to="/login" class="btn-editorial">
          Sign in →
        </router-link>
      </section>

      <!-- Choose new password -->
      <form v-else-if="hasToken" @submit.prevent="handlePasswordReset" class="space-y-7">
        <BaseInput
          v-model="newPassword"
          label="New password"
          type="password"
          placeholder="At least 8 characters"
          :error="newPasswordError"
          id="new-password"
        />
        <BaseInput
          v-model="confirmPassword"
          label="Confirm password"
          type="password"
          placeholder="Type it again"
          :error="confirmPasswordError"
          id="confirm-password"
        />
        <button type="submit" class="btn-editorial w-full justify-center py-4" :disabled="loading">
          <span v-if="loading">Updating…</span>
          <span v-else>Update password</span>
          <span aria-hidden="true">→</span>
        </button>
      </form>

      <!-- Request reset -->
      <form v-else @submit.prevent="handleRequestReset" class="space-y-7">
        <BaseInput
          v-model="email"
          label="Email"
          type="email"
          placeholder="you@example.com"
          :error="emailError"
          id="reset-email"
        />
        <button type="submit" class="btn-editorial w-full justify-center py-4" :disabled="loading">
          <span v-if="loading">Sending…</span>
          <span v-else>Send reset link</span>
          <span aria-hidden="true">→</span>
        </button>
      </form>

      <p v-if="!sent && !passwordResetComplete" class="mt-8 pt-6 border-t border-[color:var(--rule)] text-center">
        <router-link to="/login" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
          ← Back to sign in
        </router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import BaseInput from '@/components/ui/BaseInput.vue'

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
    emailError.value = 'Email is required'
    return
  }
  loading.value = true
  try {
    await api.auth.forgotPassword(email.value)
    sent.value = true
  } catch {
    error('Failed to send reset link. Please try again.')
  } finally {
    loading.value = false
  }
}

const handlePasswordReset = async () => {
  newPasswordError.value = ''
  confirmPasswordError.value = ''

  if (!newPassword.value) {
    newPasswordError.value = 'New password is required'
    return
  }
  if (newPassword.value.length < 8) {
    newPasswordError.value = 'Password must be at least 8 characters'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    confirmPasswordError.value = 'Passwords do not match'
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
    success('Password reset successful')
  } catch {
    error('Reset link is invalid or expired')
  } finally {
    loading.value = false
  }
}
</script>
