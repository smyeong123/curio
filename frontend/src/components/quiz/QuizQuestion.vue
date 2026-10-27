<template>
  <!-- One question as a ruled block on the quiz's ink section: outlined
       numeral, the question in display type, answers as full-width rows. -->
  <article class="border-t border-[color:var(--on-ink-rule)] pt-8">
    <!-- Question -->
    <div class="mb-6 flex items-start gap-5 sm:gap-8">
      <span class="num-outline num-outline-on-ink shrink-0 text-[clamp(56px,6vw,88px)]" aria-hidden="true">
        {{ String(question.id).padStart(2, '0') }}
      </span>
      <div class="min-w-0">
        <p class="kicker mb-3" style="color: var(--on-ink-2)">{{ t('quiz.question.counter', { n: question.id, total }) }}</p>
        <p class="display-headline text-[clamp(24px,2.8vw,40px)] leading-[1.02] tracking-[-0.045em]">{{ question.question }}</p>
      </div>
    </div>

    <!-- Options -->
    <div
      class="border-t border-[color:var(--on-ink-rule)]"
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
          'group flex min-h-[60px] w-full items-baseline gap-5 border-b border-[color:var(--on-ink-rule)] px-3 py-4 text-left transition-colors sm:gap-8 sm:px-4',
          getOptionClass(key as string)
        ]"
        :disabled="submitted"
        @click="$emit('select', question.id, key)"
        @keydown="onOptionKeydown($event, key as string)"
      >
        <span :class="['w-5 flex-shrink-0 font-mono-curio text-[13px]', getKeyClass(key as string)]">{{ key }}</span>
        <span :class="['flex-1 font-body-curio text-[clamp(17px,1.6vw,22px)] font-medium leading-snug tracking-[-0.015em]', getTextClass(key as string)]">{{ text }}</span>
        <span
          v-if="submitted && result && key === result.correctAnswer"
          aria-hidden="true"
          class="font-mono-curio text-[12px] uppercase tracking-[0.06em]"
        >✓ {{ t('quiz.question.correctTag') }}</span>
        <span
          v-else-if="submitted && key === selectedAnswer && result && !result.correct"
          aria-hidden="true"
          class="font-mono-curio text-[12px] uppercase tracking-[0.06em] text-[color:var(--miss)]"
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
      class="mt-5 grid gap-x-6 gap-y-1 sm:grid-cols-[160px_minmax(0,1fr)]"
    >
      <span class="kicker pt-0.5" :style="result.correct ? 'color: var(--paper)' : 'color: var(--miss)'">
        {{ result.correct ? `✓ ${t('quiz.question.correctTag')}` : `× ${t('quiz.question.wrongTag')}` }}
      </span>
      <p class="font-body-curio text-[16px] leading-[1.5] text-[color:var(--on-ink-2)]">
        {{ feedbackText }}
      </p>
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

// Rows live on the ink section: the pick is a paper row before submitting;
// after grading the right answer fills with the accent, a wrong pick turns
// the miss colour and is struck through, and the rest recede.
const getOptionClass = (key: string) => {
  if (!props.submitted) {
    return props.selectedAnswer === key
      ? 'bg-[color:var(--paper)] text-[color:var(--ink)]'
      : 'text-[color:var(--paper)] hover:bg-[color:var(--paper)]/10 cursor-pointer'
  }
  if (props.result) {
    if (key === props.result.correctAnswer) return 'bg-[color:var(--signal)] text-[color:var(--on-signal)]'
    if (key === props.selectedAnswer && !props.result.correct) return 'text-[color:var(--miss)]'
  }
  return 'text-[color:var(--paper)] opacity-45'
}

const getKeyClass = (key: string) => {
  if (!props.submitted && props.selectedAnswer === key) return 'text-[color:var(--ink)]'
  if (props.submitted && props.result?.correctAnswer === key) return 'text-[color:var(--on-signal)]'
  if (props.submitted && key === props.selectedAnswer && props.result && !props.result.correct) return 'text-[color:var(--miss)]'
  return 'text-[color:var(--on-ink-2)] group-hover:text-[color:var(--paper)]'
}

const getTextClass = (key: string) =>
  props.submitted && props.result && key === props.selectedAnswer && !props.result.correct ? 'line-through decoration-2' : ''
</script>
