<template>
  <div class="mx-auto max-w-[920px] px-5 py-10 sm:px-8 lg:px-12">
    <!-- ── Header ─────────────────────────────────────── -->
    <header class="mb-10">
      <p class="kicker kicker-signal mb-3">{{ t('quiz.history.kicker') }}</p>
      <i18n-t scope="global" keypath="quiz.history.headline" tag="h1" class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95] mb-6">
        <template #tail><em class="italic-display">{{ t('quiz.history.tail') }}</em></template>
      </i18n-t>
      <div class="rule-double w-full"></div>
    </header>

    <!-- ── Error ─────────────────────────────────────── -->
    <section v-if="errorOccurred" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker kicker-signal mb-4">{{ t('quiz.history.error.kicker') }}</p>
      <h3 class="display-headline text-[28px] mb-3">{{ t('quiz.history.error.headline') }}</h3>
      <button class="btn-editorial-ghost" @click="loadHistory">{{ t('quiz.history.error.retry') }}</button>
    </section>

    <!-- ── Loading ────────────────────────────────────── -->
    <section v-else-if="loading" class="space-y-4">
      <div v-for="i in 5" :key="i" class="border-t border-[color:var(--rule)] pt-5 animate-pulse flex justify-between items-end">
        <div>
          <div class="h-3 w-32 bg-paper-deep mb-3"></div>
          <div class="h-7 w-44 bg-paper-deep"></div>
        </div>
        <div class="h-12 w-20 bg-paper-deep"></div>
      </div>
    </section>

    <!-- ── Empty ─────────────────────────────────────── -->
    <section v-else-if="history.length === 0" class="border-2 border-[color:var(--rule)] bg-paper-deep p-14 text-center">
      <p class="kicker mb-5">{{ t('quiz.history.empty.kicker') }}</p>
      <i18n-t scope="global" keypath="quiz.history.empty.headline" tag="h3" class="display-headline text-[36px] leading-tight mb-3">
        <template #tail><em class="italic-display">{{ t('quiz.history.empty.tail') }}</em></template>
      </i18n-t>
      <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-7 max-w-md mx-auto">
        {{ t('quiz.history.empty.body') }}
      </p>
      <router-link to="/dashboard/archive" class="btn-editorial">
        {{ t('quiz.history.empty.cta') }}
        <span aria-hidden="true">→</span>
      </router-link>
    </section>

    <!-- ── Stat row ──────────────────────────────────── -->
    <template v-else>
      <section class="grid grid-cols-3 border-t-2 border-b-2 border-[color:var(--rule)] divide-x divide-[color:var(--rule)] mb-10">
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none">{{ String(history.length).padStart(2, '0') }}</div>
          <p class="kicker mt-3">{{ t('quiz.history.stats.filed') }}</p>
        </div>
        <div class="px-5 py-6">
          <div :class="['deco-num text-[44px] leading-none', avgPct >= 80 ? 'text-[color:var(--leaf)]' : avgPct >= 60 ? 'text-[color:var(--ink)]' : 'text-[color:var(--mute)]']">
            {{ avgPct }}<span class="text-[color:var(--mute)] text-[28px]">%</span>
          </div>
          <p class="kicker mt-3">{{ t('quiz.history.stats.average') }}</p>
        </div>
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none text-[color:var(--signal-deep)]">
            {{ bestPct }}<span class="text-[color:var(--mute)] text-[28px]">%</span>
          </div>
          <p class="kicker mt-3">{{ t('quiz.history.stats.best') }}</p>
        </div>
      </section>

      <!-- ── Scoreboard ────────────────────────────── -->
      <section>
        <div class="kicker pb-3 grid grid-cols-12 gap-3 border-b border-[color:var(--rule)]">
          <span class="col-span-2">{{ t('quiz.history.table.no') }}</span>
          <span class="col-span-6">{{ t('quiz.history.table.filed') }}</span>
          <span class="col-span-2 text-center">{{ t('quiz.history.table.marks') }}</span>
          <span class="col-span-2 text-right">{{ t('quiz.history.table.score') }}</span>
        </div>
        <ol class="divide-y divide-[color:var(--rule)]">
          <li
            v-for="(attempt, idx) in history"
            :key="attempt.id"
            class="grid grid-cols-12 gap-3 py-5 items-center"
          >
            <span class="col-span-2 num-tab text-[14px] text-[color:var(--mute)]">
              {{ String(history.length - idx).padStart(3, '0') }}
            </span>
            <div class="col-span-6">
              <p class="font-display text-[18px] leading-tight">{{ formatDate(attempt.completedAt) }}</p>
              <p class="kicker mt-1">{{ formatTime(attempt.completedAt) }}</p>
            </div>
            <div class="col-span-2 hidden sm:flex justify-center gap-1">
              <span
                v-for="i in attempt.totalQuestions"
                :key="i"
                :class="[
                  'w-3 h-3 border border-[color:var(--rule)]',
                  i <= attempt.score ? barColor(attempt.score, attempt.totalQuestions) : ''
                ]"
              ></span>
            </div>
            <div class="col-span-4 sm:col-span-2 text-right">
              <span :class="['deco-num text-[24px] leading-none', scoreText(attempt.score, attempt.totalQuestions)]">
                {{ attempt.score }}<span class="text-[color:var(--mute)]">/</span>{{ attempt.totalQuestions }}
              </span>
              <p class="kicker mt-1">{{ pct(attempt.score, attempt.totalQuestions) }}%</p>
            </div>
          </li>
        </ol>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useQuizStore } from '@/stores/quiz'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'

const quizStore = useQuizStore()
const { quizHistory: history } = storeToRefs(quizStore)
const { error: showError } = useToast()
const { t } = useI18n()
const { intlLocale } = useLocale()

const loading = ref(true)
const errorOccurred = ref(false)

// Guard against a malformed attempt with totalQuestions === 0 poisoning the stats
// (division by zero → NaN% everywhere). One bad row coerces to 0, never NaN.
const ratio = (score: number, total: number) => (total > 0 ? score / total : 0)
const pct = (score: number, total: number) => Math.round(ratio(score, total) * 100)

const avgPct = computed(() => {
  if (!history.value.length) return 0
  const sum = history.value.reduce((acc, a) => acc + ratio(a.score, a.totalQuestions), 0)
  return Math.round((sum / history.value.length) * 100)
})

const bestPct = computed(() => {
  if (!history.value.length) return 0
  return Math.round(Math.max(...history.value.map((a) => ratio(a.score, a.totalQuestions))) * 100)
})

const barColor = (score: number, total: number) => {
  const p = ratio(score, total)
  if (p >= 0.8) return 'bg-[color:var(--leaf)] border-[color:var(--leaf)]'
  if (p >= 0.6) return 'bg-[color:var(--signal)] border-[color:var(--signal)]'
  return 'bg-[color:var(--ink)] border-[color:var(--ink)]'
}

const scoreText = (score: number, total: number) => {
  const p = ratio(score, total)
  if (p >= 0.8) return 'text-[color:var(--leaf)]'
  if (p >= 0.6) return 'text-[color:var(--ink)]'
  return 'text-[color:var(--mute)]'
}

const formatDate = (dateStr: string) =>
  new Date(dateStr).toLocaleDateString(intlLocale.value, {
    weekday: 'long', month: 'long', day: 'numeric', year: 'numeric',
  })

const formatTime = (dateStr: string) =>
  new Date(dateStr).toLocaleTimeString(intlLocale.value, { hour: '2-digit', minute: '2-digit' })

const loadHistory = async () => {
  loading.value = true
  errorOccurred.value = false
  try {
    await quizStore.fetchHistory()
  } catch {
    errorOccurred.value = true
    showError(t('quiz.errors.history'))
  } finally {
    loading.value = false
  }
}

onMounted(() => loadHistory())
</script>
