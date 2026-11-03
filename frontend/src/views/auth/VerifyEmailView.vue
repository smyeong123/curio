<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] flex items-center justify-center px-6 py-14">
    <div class="w-full max-w-[480px]">
      <AuthMasthead />

      <!-- ── Confirming: the link is being checked ──────────────── -->
      <header v-if="state === 'confirming'" class="mb-10" aria-live="polite">
        <p class="kicker kicker-signal mb-3">{{ t('auth.verifyEmail.confirming.kicker') }}</p>
        <i18n-t scope="global" keypath="auth.verifyEmail.confirming.headline" tag="h1" class="rise-mask display-headline text-[clamp(36px,5vw,52px)] leading-[0.96] mb-3">
          <template #moment><em class="italic-display">{{ t('auth.verifyEmail.confirming.moment') }}</em></template>
        </i18n-t>
        <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
          {{ t('auth.verifyEmail.confirming.lede') }}
        </p>
      </header>

      <!-- ── Invalid / expired / used / missing link: offer a fresh one ── -->
      <template v-else>
        <header class="mb-10">
          <p class="kicker mb-3" style="color: var(--signal);">{{ t('auth.verifyEmail.invalid.kicker') }}</p>
          <i18n-t scope="global" keypath="auth.verifyEmail.invalid.headline" tag="h1" class="rise-mask display-headline text-[clamp(36px,5vw,52px)] leading-[0.96] mb-3">
            <template #expired><em class="italic-display">{{ t('auth.verifyEmail.invalid.expired') }}</em></template>
          </i18n-t>
          <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
            {{ t('auth.verifyEmail.invalid.lede') }}
          </p>
        </header>

        <MailSentPanel v-if="sentTo" :email="sentTo" body-keypath="auth.verifyEmail.sentBody" />

        <form v-else class="space-y-7" @submit.prevent>
          <BaseInput
            v-model="email"
            :label="t('auth.form.emailLabel')"
            type="email"
            :placeholder="t('auth.form.emailPlaceholder')"
            id="verify-email-address"
          />
          <div class="flex items-baseline justify-between gap-4">
            <ResendConfirmationButton :email="email" @sent="sentTo = email.trim()" />
            <router-link to="/login" class="kicker">{{ t('auth.common.backToSignIn') }}</router-link>
          </div>
        </form>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
// Target of the sign-up confirmation email (/verify-email?token=...). Opening
// the link confirms the address and signs the reader in, then onboarding picks
// up. The single-use token is stripped from the address bar before anything
// else so it can't linger in history or leak as a referrer.

import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import BaseInput from '@/components/ui/BaseInput.vue'
import AuthMasthead from '@/components/auth/AuthMasthead.vue'
import MailSentPanel from '@/components/auth/MailSentPanel.vue'
import ResendConfirmationButton from '@/components/auth/ResendConfirmationButton.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { success } = useToast()

const state = ref<'confirming' | 'invalid'>('confirming')
const email = ref('')
const sentTo = ref('')

onMounted(async () => {
  const raw = route.query.token
  const token = typeof raw === 'string' ? raw : ''
  if (raw !== undefined) {
    await router.replace({ query: {} })
  }
  if (!token) {
    state.value = 'invalid'
    return
  }
  try {
    await authStore.confirmEmail(token)
    success(t('auth.verifyEmail.toast.confirmed'))
    router.push({ name: 'onboarding' })
  } catch {
    // Unknown, expired and used links share one answer (CONFIRMATION_TOKEN_INVALID);
    // any other failure gets the same recovery path: request a fresh link.
    state.value = 'invalid'
  }
})
</script>
