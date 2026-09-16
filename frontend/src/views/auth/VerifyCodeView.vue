<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] flex items-center justify-center px-6 py-14">
    <div class="w-full max-w-[480px]">
      <router-link to="/" class="mb-10 flex items-baseline gap-3">
        <span class="display-headline text-[28px] leading-none">Curio</span>
        <span class="kicker">Vol. 047</span>
      </router-link>

      <!-- ── Locked: out of attempts, offer a reset ─────────────── -->
      <template v-if="locked">
        <header class="mb-8">
          <p class="kicker mb-3" style="color: var(--signal);">Too many tries</p>
          <h1 class="display-headline text-[clamp(36px,5vw,52px)] leading-[0.96] mb-3">
            Let's <em class="italic-display">reset</em> instead.
          </h1>
          <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
            You've used all {{ maxAttempts }} verification attempts. For your security we've
            paused this sign-in. Reset your password and you'll be back in shortly.
          </p>
        </header>

        <section v-if="resetSent" class="border border-[color:var(--leaf)] bg-paper-deep p-6">
          <p class="kicker mb-3" style="color: var(--leaf);">— Mail's away —</p>
          <p class="display-headline text-[22px] leading-tight mb-2">
            Check your <em class="italic-display">inbox</em>.
          </p>
          <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 leading-relaxed">
            If an account exists for <strong class="font-semibold text-[color:var(--ink)]">{{ email }}</strong>,
            a password reset link is on its way.
          </p>
          <router-link to="/login" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
            ← Back to sign in
          </router-link>
        </section>

        <div v-else class="space-y-5">
          <BaseButton
            :loading="resetLoading"
            class="w-full justify-center py-4"
            size="lg"
            @click="handleSendReset"
          >
            <template #trailing><span aria-hidden="true">→</span></template>
            Send password reset email
          </BaseButton>
          <p class="text-center">
            <router-link to="/login" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
              ← Back to sign in
            </router-link>
          </p>
        </div>
      </template>

      <!-- ── Expired: challenge gone, sign in again ─────────────── -->
      <template v-else-if="expired">
        <header class="mb-8">
          <p class="kicker mb-3" style="color: var(--signal);">Session expired</p>
          <h1 class="display-headline text-[clamp(36px,5vw,52px)] leading-[0.96] mb-3">
            That code <em class="italic-display">timed out</em>.
          </h1>
          <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
            For your security the code is only valid for a short while, and it must be opened in the
            same browser where you started signing in. Sign in again to get a fresh code.
          </p>
        </header>
        <router-link to="/login" class="btn-editorial w-full justify-center py-4">
          Back to sign in →
        </router-link>
      </template>

      <!-- ── Enter code ─────────────────────────────────────────── -->
      <template v-else>
        <header class="mb-10">
          <p class="kicker kicker-signal mb-3">Verify it's you</p>
          <h1 class="display-headline text-[clamp(36px,5vw,52px)] leading-[0.96] mb-4">
            Check your <em class="italic-display">email</em>.
          </h1>
          <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
            We sent a {{ codeLength }}-digit code to
            <strong class="font-semibold text-[color:var(--ink)]">{{ email }}</strong>.
            Enter it below to finish signing in.
          </p>
        </header>

        <form @submit.prevent="handleVerify" class="space-y-7">
          <div>
            <div class="flex items-baseline justify-between">
              <label for="verify-code" class="kicker mb-2 block">Verification code</label>
              <!-- Pull the code straight from the clipboard (the click is the
                   user-gesture the browser needs to read it). -->
              <button
                type="button"
                class="ink-link font-mono-curio text-[11px] uppercase tracking-[0.14em] disabled:opacity-50"
                :disabled="pasting"
                @click="handlePaste"
              >
                {{ pasting ? 'Pasting…' : 'Paste code' }}
              </button>
            </div>
            <!-- Native input (not BaseInput) so the OTP autofill hints land on the
                 <input> itself: inputmode="numeric" pops the numeric keypad,
                 autocomplete="one-time-code" enables iOS/Android SMS+email autofill,
                 and maxlength caps it to the configured code length. -->
            <input
              id="verify-code"
              v-model="code"
              type="text"
              inputmode="numeric"
              autocomplete="one-time-code"
              :maxlength="codeLength"
              placeholder="000000"
              :class="[
                'block w-full border-b-2 bg-transparent px-2 py-3 font-display text-[18px] text-[color:var(--ink)] placeholder:text-[color:var(--mute)] placeholder:font-body-curio placeholder:text-[15px] transition-all duration-150',
                'focus:outline-none focus:border-[color:var(--signal)]',
                codeError
                  ? 'border-red-300 text-red-900 placeholder-red-300'
                  : 'border-[color:var(--rule)] hover:border-[color:var(--ink)]'
              ]"
            />
            <p
              v-if="codeError"
              class="mt-2 flex items-center gap-1 text-xs text-red-600 font-mono-curio uppercase tracking-[0.12em]"
            >
              <AppIcon name="warning" class="h-3.5 w-3.5 flex-shrink-0" />
              {{ codeError }}
            </p>
          </div>

          <!-- Attempts remaining -->
          <p class="kicker" :style="attemptsRemaining <= 2 ? 'color: var(--signal);' : ''">
            {{ attemptsRemaining }} {{ attemptsRemaining === 1 ? 'attempt' : 'attempts' }} remaining
          </p>

          <BaseButton type="submit" :loading="loading" class="w-full justify-center py-4" size="lg">
            <template #trailing><span aria-hidden="true">→</span></template>
            Verify &amp; sign in
          </BaseButton>
        </form>

        <div class="mt-8 pt-6 border-t border-[color:var(--rule)] flex items-baseline justify-between">
          <button
            type="button"
            class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em] disabled:opacity-50"
            :disabled="resendLoading"
            @click="handleResend"
          >
            {{ resendLoading ? 'Sending…' : 'Resend code' }}
          </button>
          <router-link to="/login" class="kicker">← Back to sign in</router-link>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { api } from '@/services/api'
import { safeRedirectPath } from '@/utils/safeUrl'
import AppIcon from '@/components/ui/AppIcon.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const { success, error } = useToast()

const codeLength = 6
const maxAttempts = 5
// Single source of truth for the code shape — keeps header, regex and message in sync.
const codeRegex = new RegExp(`^\\d{${codeLength}}$`)

const code = ref('')
const codeError = ref('')
const loading = ref(false)
const pasting = ref(false)
const resendLoading = ref(false)
const resetLoading = ref(false)
const locked = ref(false)
const expired = ref(false)
const resetSent = ref(false)

// Snapshot the email up front — the store clears the pending challenge on success.
const email = ref(authStore.pendingVerification?.email ?? '')
const attemptsRemaining = ref(authStore.pendingVerification?.attemptsRemaining ?? maxAttempts)

// The email's "Copy code →" link lands here with ?code=NNNNNN. Email clients
// can't touch the clipboard, so the code is delivered by prefilling the input.
const queryCode = (() => {
  const raw = route.query.code
  return typeof raw === 'string' ? (raw.match(/\d/g) ?? []).join('').slice(0, codeLength) : ''
})()

onMounted(() => {
  // Without the local challenge the emailed code is unusable — we have no
  // challengeId to verify it against (e.g. the deep link was opened in a
  // different browser than the one used to sign in, or storage was cleared).
  // Surface the "expired" panel with restart guidance instead of dead-ending on
  // a blank form. The challenge is persisted, so a same-browser link resumes.
  if (!authStore.pendingVerification) {
    expired.value = true
    return
  }
  if (queryCode) {
    code.value = queryCode
    // Don't leave the one-time code sitting in the address bar / browser history.
    router.replace({ query: {} })
    // Deliberately NO clipboard write here: silently copying an OTP without a
    // user gesture parks a security code in the OS clipboard (and clipboard
    // managers) for no benefit — the field is already prefilled.
    success('Code filled in from your link.')
  }
})

// Read the verification code from the clipboard and fill the input. Keeps only
// digits and trims to the code length, so pasting "Your code: 123456" still works.
const handlePaste = async () => {
  if (!navigator.clipboard?.readText) {
    error('Clipboard access isn\'t available — paste manually with ⌘V / Ctrl+V.')
    return
  }
  pasting.value = true
  try {
    const text = await navigator.clipboard.readText()
    const digits = (text.match(/\d/g) ?? []).join('').slice(0, codeLength)
    if (!digits) {
      error('No code found on your clipboard.')
      return
    }
    code.value = digits
    codeError.value = ''
    if (digits.length === codeLength) {
      success('Code pasted.')
    }
  } catch {
    error('Couldn\'t read the clipboard — paste manually with ⌘V / Ctrl+V.')
  } finally {
    pasting.value = false
  }
}

const syncAttempts = () => {
  attemptsRemaining.value = authStore.pendingVerification?.attemptsRemaining ?? attemptsRemaining.value
}

const handleVerify = async () => {
  codeError.value = ''
  if (!code.value) {
    codeError.value = 'Code is required'
    return
  }
  if (!codeRegex.test(code.value.trim())) {
    codeError.value = `Enter the ${codeLength}-digit code from your email`
    return
  }

  loading.value = true
  try {
    const result = await authStore.verifyCode(code.value.trim())
    switch (result.status) {
      case 'VERIFIED':
        success('Welcome back!')
        router.push(safeRedirectPath(route.query.redirect) || { name: 'archive' })
        break
      case 'INVALID_CODE':
        syncAttempts()
        codeError.value = result.message || 'That code isn\'t right.'
        code.value = ''
        break
      case 'LOCKED':
        attemptsRemaining.value = 0
        locked.value = true
        break
      case 'EXPIRED':
      default:
        expired.value = true
        break
    }
  } catch {
    error('Something went wrong. Please try again.')
  } finally {
    loading.value = false
  }
}

const handleResend = async () => {
  resendLoading.value = true
  try {
    const result = await authStore.resendCode()
    if (result.status === 'SENT') {
      syncAttempts()
      code.value = ''
      codeError.value = ''
      success('A new code is on its way.')
    } else {
      expired.value = true
    }
  } catch {
    error('Couldn\'t resend the code. Please try again.')
  } finally {
    resendLoading.value = false
  }
}

const handleSendReset = async () => {
  resetLoading.value = true
  try {
    await api.auth.forgotPassword(email.value)
    authStore.clearPendingVerification()
    resetSent.value = true
  } catch {
    error('Failed to send reset link. Please try again.')
  } finally {
    resetLoading.value = false
  }
}
</script>
