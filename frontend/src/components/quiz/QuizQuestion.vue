<template>
  <article class="border-t-2 border-[color:var(--rule)] pt-6">
    <!-- Question -->
    <div class="grid grid-cols-12 gap-4 mb-6">
      <span class="col-span-2 sm:col-span-1 deco-num text-[40px] leading-none text-[color:var(--mute)]">
        {{ String(question.id).padStart(2, '0') }}
      </span>
      <div class="col-span-10 sm:col-span-11">
        <p class="kicker mb-2">{{ t('quiz.question.counter', { n: question.id, total }) }}</p>
        <p class="display-headline text-[clamp(20px,2.4vw,28px)] leading-[1.15]">{{ question.question }}</p>
      </div>
    </div>

    <!-- Options -->
    <div
      class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3"
      role="radiogroup"
      :aria-label="question.question"
    >
      <button
        v-for="(text, key) in question.options"
        :key="key"
        type="button"
        role="radio"
        :aria-checked="selectedAnswer === key"
        :data-selected="String(selectedAnswer === key)"
        :tabindex="rovingTabindex(key as string)"
        :class="[
          'group flex items-baseline gap-4 px-5 py-4 border text-left transition-all',
          getOptionClass(key as string)
        ]"
        :disabled="submitted"
        @click="$emit('select', question.id, key)"
        @keydown="onOptionKeydown($event, key as string)"
      >
        <span :class="['font-mono-curio text-[14px] font-bold flex-shrink-0', getKeyClass(key as string)]">{{ key }}</span>
        <span class="font-display text-[16px] leading-snug flex-1">{{ text }}</span>
        <span
          v-if="submitted && result && key === result.correctAnswer"
          aria-hidden="true"
          class="font-mono-curio text-[16px] text-[color:var(--leaf)]"
        >✓</span>
        <span
          v-else-if="submitted && key === selectedAnswer && result && !result.correct"
          aria-hidden="true"
          class="font-mono-curio text-[16px] text-[color:var(--signal-deep)]"
        >×</span>
        <!-- Non-color-only correctness cue for assistive tech -->
        <span v-if="submitted && result && key === result.correctAnswer" class="sr-only">{{ t('quiz.question.a11yCorrect') }}</span>
        <span
          v-else-if="submitted && key === selectedAnswer && result && !result.correct"
          class="sr-only"
        >{{ t('quiz.question.a11yIncorrect') }}</span>
      </button>
    </div>

    <!-- Feedback -->
    <div
      v-if="submitted && result"
      :class="[
        'mt-5 grid grid-cols-12 gap-4',
      ]"
    >
      <span class="col-span-2 sm:col-span-1 kicker pt-1" :style="result.correct ? 'color: var(--leaf)' : 'color: var(--signal-deep)'">
        {{ result.correct ? `✓ ${t('quiz.question.correctTag')}` : `× ${t('quiz.question.wrongTag')}` }}
      </span>
      <div class="col-span-10 sm:col-span-11">
        <p class="font-body-curio text-[14.5px] leading-[1.55] text-[color:var(--ink-soft)] border-l-2 border-[color:var(--signal)] pl-4">
          {{ feedbackText }}
        </p>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { QuizQuestion as QuizQuestionType, QuizSubmitResponse } from '@/types/quiz'

const props = defineProps<{
  question: QuizQuestionType
  total: number
  selectedAnswer?: string
  submitted: boolean
  result?: QuizSubmitResponse['results'][number]
}>()

const emit = defineEmits<{
  select: [questionId: number, answer: string]
}>()

const { t } = useI18n()

// One string so the verdict and explanation keep their space; the catalog
// labels carry their own end punctuation ("Correct!", "Incorrect.").
const feedbackText = computed(() => {
  if (!props.result) return ''
  const verdict = t(props.result.correct ? 'quiz.question.feedbackCorrect' : 'quiz.question.feedbackIncorrect')
  return props.result.explanation ? `${verdict} ${props.result.explanation}` : verdict
})

// ── WAI-ARIA radio group keyboard pattern ──────────────────────────
// Roving tabindex: only one option is in the Tab order — the selected one, or
// the first when nothing is selected yet — and arrow keys move (and select)
// within the group. Without this, `role="radio"` on <button>s advertises radio
// semantics to screen readers but forces Tab-through-every-option navigation.
const optionKeys = () => Object.keys(props.question.options)

const rovingTabindex = (key: string) => {
  const keys = optionKeys()
  const active = props.selectedAnswer && keys.includes(props.selectedAnswer)
    ? props.selectedAnswer
    : keys[0]
  return key === active ? 0 : -1
}

const onOptionKeydown = (event: KeyboardEvent, key: string) => {
  if (props.submitted) return
  const keys = optionKeys()
  let delta = 0
  if (event.key === 'ArrowDown' || event.key === 'ArrowRight') delta = 1
  else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') delta = -1
  else return
  event.preventDefault()
  const nextIndex = (keys.indexOf(key) + delta + keys.length) % keys.length
  const next = keys[nextIndex]
  if (next === undefined) return
  emit('select', props.question.id, next)
  // Move focus to the newly selected radio (arrow selection = focus follows).
  const group = (event.currentTarget as HTMLElement).closest('[role="radiogroup"]')
  const radios = group?.querySelectorAll<HTMLElement>('[role="radio"]')
  radios?.[nextIndex]?.focus()
}

const getOptionClass = (key: string) => {
  if (!props.submitted) {
    return props.selectedAnswer === key
      ? 'bg-ink text-[color:var(--paper)] border-[color:var(--ink)]'
      : 'bg-paper text-[color:var(--ink)] border-[color:var(--rule)] hover:border-[color:var(--signal)] hover:translate-x-[-2px] cursor-pointer'
  }
  if (props.result) {
    if (key === props.result.correctAnswer) return 'border-[color:var(--leaf)] bg-[color:var(--leaf)]/10 text-[color:var(--ink)]'
    if (key === props.selectedAnswer && !props.result.correct) return 'border-[color:var(--signal-deep)] bg-[color:var(--signal)]/10 text-[color:var(--ink)]'
  }
  return 'border-[color:var(--rule)]/40 text-[color:var(--mute)] bg-paper-deep'
}

const getKeyClass = (key: string) => {
  if (!props.submitted && props.selectedAnswer === key) return 'text-[color:var(--paper)]'
  if (props.submitted && props.result?.correctAnswer === key) return 'text-[color:var(--leaf)]'
  if (props.submitted && key === props.selectedAnswer && props.result && !props.result.correct) return 'text-[color:var(--signal-deep)]'
  return 'text-[color:var(--mute)] group-hover:text-[color:var(--signal-deep)]'
}
</script>
