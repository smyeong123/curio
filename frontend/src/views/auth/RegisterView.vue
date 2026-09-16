<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] lg:grid lg:grid-cols-[1.05fr_0.95fr]">
    <!-- ── Editorial aside (preview of beats) ─────────────────── -->
    <aside class="relative hidden flex-col bg-ink p-12 text-[color:var(--paper)] lg:flex">
      <router-link to="/" class="flex items-baseline gap-3">
        <span class="display-headline text-[36px] leading-none">Curio</span>
        <span class="kicker" style="color: var(--paper); opacity: 0.6;">Vol. 047</span>
      </router-link>

      <div class="my-auto max-w-[460px]">
        <p class="kicker mb-5" style="color: var(--signal);">{{ t('auth.register.aside.kicker') }}</p>
        <i18n-t scope="global" keypath="auth.register.aside.headline" tag="h2" class="display-headline text-[clamp(40px,5vw,68px)] leading-[0.96] mb-8">
          <template #daily>
            <em class="italic-display" style="color: var(--signal);">{{ t('auth.register.aside.daily') }}</em>
          </template>
        </i18n-t>
        <p class="font-body-curio text-[15px] opacity-80 leading-relaxed mb-10">
          {{ t('auth.register.aside.body') }}
        </p>

        <ul class="grid grid-cols-2 gap-x-6 gap-y-3 border-t border-[color:var(--paper)]/20 pt-6">
          <li
            v-for="(topic, i) in sampleTopics"
            :key="topic"
            class="flex items-baseline gap-3"
          >
            <span class="num-tab text-[10px] opacity-60">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="font-display text-[14px] leading-tight">{{ topicLabel(topic) }}</span>
          </li>
        </ul>
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
          <p class="kicker kicker-signal mb-3">{{ t('auth.register.kicker') }}</p>
          <i18n-t scope="global" keypath="auth.register.headline" tag="h1" class="display-headline text-[clamp(40px,5vw,64px)] leading-[0.96] mb-4">
            <template #subscription><em class="italic-display">{{ t('auth.register.subscription') }}</em></template>
          </i18n-t>
          <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed">
            {{ t('auth.register.lede') }}
          </p>
        </header>

        <RegisterForm
          :loading="loading"
          :server-error="serverError"
          @submit="handleRegister"
        />

        <div class="mt-8 pt-6 border-t border-[color:var(--rule)] flex items-baseline justify-between">
          <span class="kicker">{{ t('auth.register.haveAccount') }}</span>
          <router-link to="/login" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
            {{ t('auth.register.signInLink') }}
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'
import { useTopicLabels } from '@/composables/useTopicLabels'
import RegisterForm from '@/components/auth/RegisterForm.vue'
import LanguageToggle from '@/components/ui/LanguageToggle.vue'
import { getApiErrorMessage } from '@/utils/apiError'

const { t } = useI18n()
const { intlLocale } = useLocale()
const { topicLabel } = useTopicLabels()
const authStore = useAuthStore()
const router = useRouter()
const { success } = useToast()

const loading = ref(false)
const serverError = ref('')

// Canonical topic names (data/topics.ts); rendered through topicLabel().
const sampleTopics = [
  'Claude (Anthropic)',
  'GPT & ChatGPT (OpenAI)',
  'Gemini (Google DeepMind)',
  'Llama (Meta AI)',
  'Reasoning & Context',
  'Pricing & Availability',
  'Benchmarks & Evaluations',
  'New & Emerging Models',
] as const

const todayShort = computed(() => {
  const d = new Date()
  return d.toLocaleDateString(intlLocale.value, { month: 'short', day: '2-digit', year: 'numeric' }).toUpperCase()
})

const handleRegister = async (data: { email: string; password: string; fullName: string }) => {
  loading.value = true
  serverError.value = ''
  try {
    await authStore.register(data)
    success(t('auth.register.toast.created'))
    router.push({ name: 'onboarding' })
  } catch (err: unknown) {
    serverError.value = getApiErrorMessage(err, t('auth.register.errors.failed'))
  } finally {
    loading.value = false
  }
}
</script>
