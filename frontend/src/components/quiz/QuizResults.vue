<template>
  <section
    :data-tier="tier"
    aria-labelledby="quiz-result-heading"
  >
    <!-- Score masthead -->
    <header class="border-t-2 border-b-2 border-[color:var(--rule)] py-10 mb-8" role="status" aria-live="polite">
      <p class="kicker kicker-signal mb-3">{{ t('quiz.results.kicker') }}</p>
      <div class="flex items-end justify-between flex-wrap gap-4 mb-4">
        <h2 id="quiz-result-heading" class="display-headline text-[clamp(48px,8vw,108px)] leading-[0.92]">
          {{ message.lead }}
          <em class="italic-display">{{ message.tail }}</em>
        </h2>
        <div class="text-right">
          <span class="deco-num text-[80px] leading-none" :class="scoreColor" :aria-label="t('quiz.results.a11yScore', { score, total })">
            {{ score }}<span class="text-[color:var(--mute)]">/</span>{{ total }}
          </span>
          <p class="kicker mt-2">{{ t('quiz.results.percentCorrect', { percent: percentage }) }}</p>
        </div>
      </div>

      <!-- Score bar -->
      <div
        class="flex items-center gap-1.5 mt-6"
        role="img"
        :aria-label="t('quiz.results.a11yBar', { score, total })"
      >
        <span
          v-for="i in total"
          :key="i"
          :class="[
            'flex-1 h-2 border border-[color:var(--rule)]',
            i <= score ? scoreBarColor : 'bg-paper'
          ]"
        ></span>
      </div>
    </header>

    <!-- Actions -->
    <div class="flex flex-wrap gap-3">
      <button class="btn-editorial-ghost flex-1 justify-center" @click="$emit('review')">{{ t('quiz.results.review') }}</button>
      <button class="btn-editorial flex-1 justify-center" @click="$emit('back')">{{ t('quiz.nav.backToEdition') }} →</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  score: number
  total: number
}>()

defineEmits<{
  review: []
  back: []
}>()

const { t } = useI18n()

const percentage = computed(() =>
  props.total > 0 ? Math.round((props.score / props.total) * 100) : 0
)

type Tier = 'excellent' | 'good' | 'keep-reading'

const tier = computed<Tier>(() => {
  if (percentage.value >= 80) return 'excellent'
  if (percentage.value >= 60) return 'good'
  return 'keep-reading'
})

const scoreColor = computed(() => {
  switch (tier.value) {
    case 'excellent':
      return 'text-[color:var(--leaf)]'
    case 'good':
      return 'text-[color:var(--signal-deep)]'
    default:
      return 'text-[color:var(--ink)]'
  }
})

const scoreBarColor = computed(() => {
  switch (tier.value) {
    case 'excellent':
      return 'bg-[color:var(--leaf)] border-[color:var(--leaf)]'
    case 'good':
      return 'bg-[color:var(--signal)] border-[color:var(--signal)]'
    default:
      return 'bg-[color:var(--ink)] border-[color:var(--ink)]'
  }
})

// Headline is split lead/tail so the tail can carry the italic display face.
// Tails carry their own end punctuation ("work!"), so the template adds none.
const message = computed(() => {
  const key = tier.value === 'keep-reading' ? 'keepReading' : tier.value
  return {
    lead: t(`quiz.results.tiers.${key}.lead`),
    tail: t(`quiz.results.tiers.${key}.tail`),
  }
})
</script>
