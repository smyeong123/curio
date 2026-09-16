<template>
  <div class="mx-auto max-w-[860px] px-5 py-10 sm:px-8 lg:px-12">
    <!-- ── Loading ─────────────────────────────────── -->
    <div v-if="loading" class="border-t-2 border-[color:var(--rule)] pt-24 pb-24 text-center">
      <p class="kicker kicker-signal mb-4">{{ t('quiz.loading.kicker') }}</p>
      <i18n-t scope="global" keypath="quiz.loading.headline" tag="p" class="display-headline text-[36px] leading-tight">
        <template #questions><em class="italic-display">{{ t('quiz.loading.questions') }}</em></template>
      </i18n-t>
    </div>

    <!-- ── Error ───────────────────────────────────── -->
    <div v-else-if="errorMsg" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker kicker-signal mb-4">{{ t('quiz.error.kicker') }}</p>
      <h3 class="display-headline text-[28px] mb-2">{{ errorMsg }}</h3>
      <router-link to="/dashboard/archive" class="btn-editorial-ghost mt-6 inline-flex">
        <span aria-hidden="true">←</span>
        {{ t('quiz.nav.backToEdition') }}
      </router-link>
    </div>

    <!-- ── Loaded ──────────────────────────────────── -->
    <div v-else>
      <template v-if="submitted && !reviewing">
        <QuizResults
          :score="results.score"
          :total="results.totalQuestions"
          @review="reviewing = true"
          @back="router.push({ name: 'archive' })"
        />
        <!-- Retake with a lower score keeps the prior best (backend improved=false). -->
        <p
          v-if="results.improved === false"
          class="kicker text-center mt-4 text-[color:var(--ink-soft)]"
        >
          {{ t('quiz.results.bestStays', { best: results.bestScore, total: results.totalQuestions }) }}
        </p>
      </template>

      <div v-else>
        <div class="mb-6 flex items-baseline justify-between gap-4">
          <router-link
            to="/dashboard/archive"
            class="kicker inline-flex items-baseline gap-2 hover:text-[color:var(--ink)]"
          >
            <span aria-hidden="true">←</span> {{ t('quiz.nav.backToEdition') }}
          </router-link>
          <button
            v-if="submitted"
            type="button"
            class="kicker inline-flex items-baseline gap-2 hover:text-[color:var(--ink)]"
            @click="reviewing = false"
          >
            <span aria-hidden="true">←</span> {{ t('quiz.nav.backToResults') }}
          </button>
        </div>

        <header class="mb-10">
          <p class="kicker kicker-signal mb-3">{{ t('quiz.header.kicker') }}</p>
          <i18n-t scope="global" keypath="quiz.header.headline" tag="h1" class="display-headline text-[clamp(40px,6vw,80px)] leading-[0.96] mb-4">
            <template #tail><em class="italic-display">{{ t('quiz.header.tail') }}</em></template>
          </i18n-t>
          <!-- Prior attempt (retakes are "better score wins") -->
          <p
            v-if="previousAttempt && !submitted"
            class="kicker inline-flex items-baseline gap-2 border border-[color:var(--rule)] bg-paper-deep px-3 py-2"
          >
            <span class="text-[color:var(--signal-deep)]">{{ t('quiz.header.filedBefore') }}</span>
            {{ t('quiz.header.previousAttempt', { score: previousAttempt.score, total: questions.length || 5 }) }}
          </p>
          <div class="flex items-end justify-between flex-wrap gap-3 mt-6 border-t-2 border-b-2 border-[color:var(--rule)] py-4">
            <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] leading-snug max-w-[42ch]">
              {{ t('quiz.header.intro') }}
            </p>
            <div class="text-right">
              <span class="deco-num text-[28px] leading-none">
                {{ Object.keys(answers).length }}<span class="text-[color:var(--mute)]">/</span>{{ questions.length }}
              </span>
              <p class="kicker mt-1">{{ t('quiz.header.answered') }}</p>
            </div>
          </div>
        </header>

        <!-- Questions -->
        <div class="space-y-10 mb-10">
          <QuizQuestion
            v-for="q in questions"
            :key="q.id"
            :question="q"
            :total="questions.length"
            :selected-answer="answers[q.id]"
            :submitted="submitted"
            :result="resultMap[q.id]"
            @select="handleSelect"
          />
        </div>

        <!-- Submit -->
        <div v-if="!submitted" class="border-t-2 border-[color:var(--rule)] pt-8">
          <button
            class="btn-editorial w-full justify-center py-4"
            :disabled="Object.keys(answers).length < questions.length || submitting"
            @click="handleSubmit"
          >
            <span v-if="submitting">{{ t('quiz.submit.submitting') }}</span>
            <span v-else>{{ t('quiz.submit.cta') }}</span>
            <span aria-hidden="true">→</span>
          </button>
          <p
            v-if="Object.keys(answers).length < questions.length"
            class="kicker text-center mt-3"
          >
            {{ t('quiz.submit.hint', { count: questions.length }) }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useQuizStore } from '@/stores/quiz'
import { useToast } from '@/composables/useToast'
import type { QuizQuestion as QuizQuestionType, QuizSubmitResponse } from '@/types/quiz'
import QuizQuestion from '@/components/quiz/QuizQuestion.vue'
import QuizResults from '@/components/quiz/QuizResults.vue'
import { getApiErrorMessage } from '@/utils/apiError'

const route = useRoute()
const router = useRouter()
const quizStore = useQuizStore()
const { error: showError } = useToast()
const { t } = useI18n()

const loading = ref(true)
const errorMsg = ref('')
const questions = ref<QuizQuestionType[]>([])
const quizId = ref('')
const answers = ref<Record<number, string>>({})
const submitted = ref(false)
const submitting = ref(false)
const reviewing = ref(false)
const results = ref<QuizSubmitResponse>({ score: 0, totalQuestions: 0, results: [] })
const previousAttempt = ref<{ score: number; completedAt: string | null } | null>(null)

const resultMap = computed(() => {
  const map: Record<number, QuizSubmitResponse['results'][number]> = {}
  if (results.value?.results) {
    for (const r of results.value.results) {
      map[r.questionId] = r
    }
  }
  return map
})

const handleSelect = (questionId: number, answer: string) => {
  if (submitted.value) return
  answers.value[questionId] = answer
}

const handleSubmit = async () => {
  submitting.value = true
  try {
    const res = await quizStore.submitQuiz(quizId.value, answers.value)
    results.value = res
    submitted.value = true
  } catch (err: unknown) {
    showError(getApiErrorMessage(err, t('quiz.errors.submit')))
  } finally {
    submitting.value = false
  }
}

onBeforeRouteLeave(() => {
  if (Object.keys(answers.value).length > 0 && !submitted.value) {
    return window.confirm(t('quiz.leaveConfirm'))
  }
})

onMounted(async () => {
  const digestId = route.params.digestId as string
  try {
    const quiz = await quizStore.fetchQuiz(digestId)
    quizId.value = quiz.id
    questions.value = quiz.questions?.questions || []
    previousAttempt.value = quiz.previousAttempt ?? null
  } catch {
    errorMsg.value = t('quiz.error.notFound')
  } finally {
    loading.value = false
  }
})
</script>
