<template>
  <button
    type="button"
    class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em] disabled:opacity-50 disabled:no-underline"
    :disabled="loading || cooldown > 0 || !email"
    data-test="resend-confirmation"
    @click="resend"
  >
    <template v-if="loading">{{ t('auth.confirmation.resending') }}</template>
    <template v-else-if="cooldown > 0">{{ t('auth.confirmation.resendIn', { seconds: cooldown }) }}</template>
    <template v-else>{{ t('auth.confirmation.resend') }}</template>
  </button>
</template>

<script setup lang="ts">
// "Resend link" for the sign-up confirmation email, shared by the register,
// verify-email and login screens. The backend answers 202 whatever the address,
// so the toast is deliberately non-committal. A short cooldown stops repeat
// clicks from burning the strict per-IP rate-limit bucket.

import { onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

const props = withDefaults(defineProps<{
  /** Address to send the link to. The button is disabled while it is blank. */
  email: string
  /** Seconds before the button can be pressed again. */
  cooldownSeconds?: number
  /** Start in cooldown (e.g. a link was sent a moment ago by sign-up itself). */
  startCooledDown?: boolean
}>(), {
  cooldownSeconds: 30,
  startCooledDown: false
})

const emit = defineEmits<{ sent: [] }>()

const { t } = useI18n()
const authStore = useAuthStore()
const { success, error } = useToast()

const loading = ref(false)
const cooldown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const stopTimer = () => {
  if (timer) clearInterval(timer)
  timer = null
}

const startCooldown = () => {
  stopTimer()
  cooldown.value = props.cooldownSeconds
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      cooldown.value = 0
      stopTimer()
    }
  }, 1000)
}

if (props.startCooledDown) startCooldown()

const resend = async () => {
  if (!props.email || loading.value || cooldown.value > 0) return
  loading.value = true
  try {
    await authStore.resendConfirmation(props.email.trim())
    success(t('auth.confirmation.toast.resent'))
    startCooldown()
    emit('sent')
  } catch {
    error(t('auth.confirmation.errors.resendFailed'))
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(stopTimer)
</script>
