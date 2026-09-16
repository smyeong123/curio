<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <!-- ── Header ────────────────────────────────── -->
    <header class="mb-10">
      <p class="kicker kicker-signal mb-3">{{ t('admin.dashboard.kicker') }}</p>
      <i18n-t scope="global" keypath="admin.dashboard.headline" tag="h1" class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95] mb-6">
        <template #desk><em class="italic-display">{{ t('admin.dashboard.desk') }}</em></template>
      </i18n-t>
      <div class="rule-double w-full"></div>
    </header>

    <!-- ── Error ────────────────────────────────── -->
    <section v-if="errorOccurred" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker kicker-signal mb-4">{{ t('admin.common.loadFailed') }}</p>
      <h3 class="display-headline text-[28px] mb-3">{{ t('admin.dashboard.loadFailedTitle') }}</h3>
      <button class="btn-editorial-ghost" @click="loadDashboard">{{ t('admin.common.retry') }}</button>
    </section>

    <div v-else-if="statsLoading" class="border-t-2 border-[color:var(--rule)] pt-12 flex items-center justify-center gap-3 text-[color:var(--mute)]">
      <BaseSpinner :size="16" />
      <p class="kicker">{{ t('admin.dashboard.loading') }}</p>
    </div>

    <div v-else class="space-y-14">
      <!-- ── Quick stats ─────────────────────── -->
      <section class="grid grid-cols-3 border-t-2 border-b-2 border-[color:var(--rule)] divide-x divide-[color:var(--rule)]">
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none">{{ fmtNum(quickStats.totalUsers) }}</div>
          <p class="kicker mt-3">{{ t('admin.dashboard.stats.totalUsers') }}</p>
        </div>
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none text-[color:var(--leaf)]">{{ fmtNum(quickStats.emailsSentToday) }}</div>
          <p class="kicker mt-3">{{ t('admin.common.kpi.emailsToday') }}</p>
        </div>
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none text-[color:var(--signal-deep)]">{{ fmtNum(quickStats.quizCompletionsToday) }}</div>
          <p class="kicker mt-3">{{ t('admin.common.kpi.quizzesToday') }}</p>
        </div>
      </section>

      <!-- ── Scheduled jobs ──────────────────── -->
      <section>
        <p class="kicker mb-4">{{ t('admin.dashboard.jobs.kicker') }}</p>
        <div class="space-y-0 border-t-2 border-b-2 border-[color:var(--rule)] divide-y divide-[color:var(--rule)]">
          <!-- Generate digests -->
          <article class="py-6">
            <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
              <div class="flex-1">
                <div class="flex items-baseline flex-wrap gap-3 mb-2">
                  <p class="display-headline text-[22px] leading-tight">{{ t('admin.dashboard.jobs.generate.title') }}</p>
                  <span class="kicker">{{ t('admin.dashboard.jobs.generate.cron') }}</span>
                  <span v-if="jobStatus.digestGeneration.running" class="kicker inline-flex items-center gap-1.5" style="color: var(--signal-deep);"><span class="inline-block w-[6px] h-[6px] rounded-full bg-current animate-pulse"></span>{{ t('admin.dashboard.jobs.running') }}</span>
                  <span
                    v-else-if="jobStatus.digestGeneration.lastResult"
                    :class="['kicker']"
                    :style="jobStatus.digestGeneration.lastResult.failed ? 'color: #b91c1c' : 'color: var(--leaf)'"
                  >● {{ jobStatus.digestGeneration.lastResult.failed ? t('admin.dashboard.jobs.failed') : t('admin.dashboard.jobs.generate.done') }}</span>
                </div>
                <p class="font-body-curio text-[13.5px] text-[color:var(--ink-soft)] mb-3 max-w-[60ch]">
                  {{ t('admin.dashboard.jobs.generate.body') }}
                </p>
                <select
                  v-model="digestTopicFilter"
                  class="bg-transparent border-b border-[color:var(--rule)] py-1 font-mono-curio text-[12px] text-[color:var(--ink)] focus:outline-none focus:border-[color:var(--signal)]"
                >
                  <option value="">{{ t('admin.common.allBeats') }}</option>
                  <option v-for="topic in allTopics" :key="topic" :value="topic">{{ topicLabel(topic) }}</option>
                </select>
                <p v-if="jobStatus.digestGeneration.lastRanAt" class="kicker mt-3">
                  {{ t('admin.dashboard.jobs.lastRan', { time: formatDate(jobStatus.digestGeneration.lastRanAt) }) }}
                </p>
                <p v-if="jobStatus.digestGeneration.lastResult" class="font-body-curio text-[13px] text-[color:var(--ink)] mt-1">
                  {{ formatJobResult('digests', jobStatus.digestGeneration.lastResult) }}
                </p>

                <div
                  v-if="hasDigestErrors(jobStatus.digestGeneration.lastResult)"
                  class="mt-4 border border-red-300 bg-red-50/40 p-4 text-[12px]"
                  role="alert"
                >
                  <p class="kicker mb-2" style="color: #b91c1c;">{{ t('admin.dashboard.jobs.whyFailed') }}</p>
                  <ul
                    v-if="jobStatus.digestGeneration.lastResult?.errorsByType"
                    class="space-y-1 mb-2 font-mono-curio text-[11px] text-red-900"
                  >
                    <li v-for="(count, kind) in jobStatus.digestGeneration.lastResult.errorsByType" :key="String(kind)">
                      {{ kind }} × {{ count }}
                    </li>
                  </ul>
                  <ul
                    v-if="jobStatus.digestGeneration.lastResult?.sampleErrors?.length"
                    class="space-y-1.5 font-body-curio text-[12px] text-red-900"
                  >
                    <li v-for="(sample, idx) in jobStatus.digestGeneration.lastResult.sampleErrors" :key="idx">
                      <span class="font-display">{{ sample.userEmail }}</span> —
                      <span class="font-mono-curio text-[11px]">{{ sample.message }}</span>
                    </li>
                  </ul>
                </div>
              </div>
              <button class="btn-editorial flex-shrink-0" :disabled="generating || jobStatus.digestGeneration.running" @click="handleGenerateDigests">
                <span v-if="generating || jobStatus.digestGeneration.running">{{ t('admin.dashboard.jobs.running') }}</span>
                <span v-else>{{ t('admin.dashboard.jobs.runNow') }}</span>
                <BaseSpinner v-if="generating || jobStatus.digestGeneration.running" :size="13" />
                <span v-else aria-hidden="true">→</span>
              </button>
            </div>
          </article>

          <!-- Send emails -->
          <article class="py-6">
            <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
              <div class="flex-1">
                <div class="flex items-baseline flex-wrap gap-3 mb-2">
                  <p class="display-headline text-[22px] leading-tight">{{ t('admin.dashboard.jobs.send.title') }}</p>
                  <span class="kicker">{{ t('admin.dashboard.jobs.send.cron') }}</span>
                  <span v-if="jobStatus.emailSend.running" class="kicker inline-flex items-center gap-1.5" style="color: var(--signal-deep);"><span class="inline-block w-[6px] h-[6px] rounded-full bg-current animate-pulse"></span>{{ t('admin.dashboard.jobs.running') }}</span>
                  <span
                    v-else-if="jobStatus.emailSend.lastResult"
                    :class="['kicker']"
                    :style="jobStatus.emailSend.lastResult.failed ? 'color: #b91c1c' : 'color: var(--leaf)'"
                  >● {{ jobStatus.emailSend.lastResult.failed ? t('admin.dashboard.jobs.failed') : t('admin.dashboard.jobs.send.done') }}</span>
                </div>
                <p class="font-body-curio text-[13.5px] text-[color:var(--ink-soft)] max-w-[60ch]">
                  {{ t('admin.dashboard.jobs.send.body') }}
                </p>
                <p v-if="jobStatus.emailSend.lastRanAt" class="kicker mt-3">
                  {{ t('admin.dashboard.jobs.lastRan', { time: formatDate(jobStatus.emailSend.lastRanAt) }) }}
                </p>
                <p v-if="jobStatus.emailSend.lastResult" class="font-body-curio text-[13px] text-[color:var(--ink)] mt-1">
                  {{ formatJobResult('emails', jobStatus.emailSend.lastResult) }}
                </p>
              </div>
              <button class="btn-editorial flex-shrink-0" :disabled="sendingEmails || jobStatus.emailSend.running" @click="handleSendEmails">
                <span v-if="sendingEmails || jobStatus.emailSend.running">{{ t('admin.dashboard.jobs.send.sending') }}</span>
                <span v-else>{{ t('admin.dashboard.jobs.runNow') }}</span>
                <BaseSpinner v-if="sendingEmails || jobStatus.emailSend.running" :size="13" />
                <span v-else aria-hidden="true">→</span>
              </button>
            </div>
          </article>

          <!-- Cleanup -->
          <article class="py-6">
            <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
              <div class="flex-1">
                <div class="flex items-baseline flex-wrap gap-3 mb-2">
                  <p class="display-headline text-[22px] leading-tight">{{ t('admin.dashboard.jobs.cleanup.title') }}</p>
                  <span class="kicker">{{ t('admin.dashboard.jobs.cleanup.cron') }}</span>
                  <span v-if="jobStatus.cleanup.running" class="kicker inline-flex items-center gap-1.5" style="color: var(--signal-deep);"><span class="inline-block w-[6px] h-[6px] rounded-full bg-current animate-pulse"></span>{{ t('admin.dashboard.jobs.running') }}</span>
                  <span
                    v-else-if="jobStatus.cleanup.lastResult"
                    :class="['kicker']"
                    :style="jobStatus.cleanup.lastResult.failed ? 'color: #b91c1c' : 'color: var(--leaf)'"
                  >● {{ jobStatus.cleanup.lastResult.failed ? t('admin.dashboard.jobs.failed') : t('admin.dashboard.jobs.cleanup.done') }}</span>
                </div>
                <p class="font-body-curio text-[13.5px] text-[color:var(--ink-soft)] max-w-[60ch]">
                  {{ t('admin.dashboard.jobs.cleanup.body') }}
                </p>
                <p v-if="jobStatus.cleanup.lastRanAt" class="kicker mt-3">
                  {{ t('admin.dashboard.jobs.lastRan', { time: formatDate(jobStatus.cleanup.lastRanAt) }) }}
                </p>
                <p v-if="jobStatus.cleanup.lastResult" class="font-body-curio text-[13px] text-[color:var(--ink)] mt-1">
                  {{ formatJobResult('cleanup', jobStatus.cleanup.lastResult) }}
                </p>
              </div>
              <button
                class="inline-flex items-center gap-3 border border-red-400 bg-transparent px-6 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] text-red-600 hover:bg-red-600 hover:text-white transition-colors disabled:opacity-50"
                :disabled="cleaningUp"
                @click="showCleanupConfirm = true"
              >
                <span v-if="cleaningUp">{{ t('admin.dashboard.jobs.cleanup.recycling') }}</span>
                <span v-else>{{ t('admin.dashboard.jobs.runNow') }}</span>
                <BaseSpinner v-if="cleaningUp" :size="13" />
                <span v-else aria-hidden="true">↗</span>
              </button>
            </div>
          </article>
        </div>
      </section>

      <!-- ── Topics overview ─────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-4">
          <p class="kicker">{{ t('admin.dashboard.beats.kicker') }}</p>
          <span class="kicker">{{ t('admin.dashboard.beats.count', { count: topics.length }) }}</span>
        </div>
        <table class="w-full text-[13.5px] border-t-2 border-[color:var(--rule)]">
          <thead>
            <tr class="border-b border-[color:var(--rule)]">
              <th class="text-left py-3 kicker">{{ t('admin.dashboard.beats.columns.beat') }}</th>
              <th class="text-right py-3 kicker">{{ t('admin.dashboard.beats.columns.subscribers') }}</th>
              <th class="text-right py-3 kicker">{{ t('admin.dashboard.beats.columns.filedToday') }}</th>
              <th class="text-right py-3 kicker">{{ t('admin.dashboard.beats.columns.lastFiled') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="topic in topics"
              :key="topic.topic"
              class="border-b border-[color:var(--rule)]/40 last:border-0"
            >
              <td class="py-3 font-display text-[15px] text-[color:var(--ink)]">{{ topicLabel(topic.topic) }}</td>
              <td class="py-3 text-right num-tab text-[14px]">{{ topic.subscriberCount }}</td>
              <td
                :class="[
                  'py-3 text-right num-tab text-[14px]',
                  topic.digestsGeneratedToday > 0 ? 'text-[color:var(--leaf)] font-semibold' : 'text-[color:var(--mute)]'
                ]"
              >{{ topic.digestsGeneratedToday }}</td>
              <td class="py-3 text-right font-mono-curio text-[12px] text-[color:var(--ink-soft)]">
                {{ topic.latestDigestGeneratedAt ? formatDateShort(topic.latestDigestGeneratedAt) : '—' }}
              </td>
            </tr>
            <tr v-if="topics.length === 0">
              <td colspan="4" class="py-6 text-center kicker">{{ t('admin.dashboard.beats.empty') }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </div>

    <!-- Cleanup confirm -->
    <BaseModal :show="showCleanupConfirm" :title="t('admin.dashboard.cleanupModal.title')" @close="showCleanupConfirm = false">
      <p class="font-body-curio text-[14.5px] text-[color:var(--ink-soft)] mb-6 leading-relaxed">
        {{ t('admin.dashboard.cleanupModal.body') }}
      </p>
      <div class="flex justify-end gap-3">
        <button class="btn-editorial-ghost" @click="showCleanupConfirm = false">{{ t('common.actions.cancel') }}</button>
        <button
          class="inline-flex items-center gap-3 bg-red-600 text-white px-5 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] hover:bg-red-700 transition-colors disabled:opacity-50"
          :disabled="cleaningUp"
          @click="handleCleanup"
        >
          <BaseSpinner v-if="cleaningUp" :size="13" />
          {{ cleaningUp ? t('admin.dashboard.jobs.cleanup.recycling') : t('admin.dashboard.cleanupModal.confirm') }}
        </button>
      </div>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { ALL_TOPICS } from '@/data/topics'
import BaseModal from '@/components/ui/BaseModal.vue'
import BaseSpinner from '@/components/ui/BaseSpinner.vue'

const { t } = useI18n()
const { intlLocale } = useLocale()
const { topicLabel } = useTopicLabels()
const { error, success } = useToast()

interface TopicStatus {
  topic: string
  subscriberCount: number
  latestDigestGeneratedAt: string | null
  digestsGeneratedToday: number
}

interface SampleError {
  userEmail: string
  message: string
}

interface JobResult {
  failed?: boolean
  totalUsers?: number
  successCount?: number
  failCount?: number
  sampleErrors?: SampleError[]
  errorsByType?: Record<string, number>
  sentCount?: number
  totalUsersProcessed?: number
  deletedDigests?: number
  cutoffDate?: string
  skippedCount?: number
  // Scheduled DigestGenerationJob emits these keys instead of the manual shape
  usersProcessed?: number
  digestSuccess?: number
  digestFail?: number
}

interface JobState {
  running: boolean
  lastRanAt: string | null
  lastResult: JobResult | null
}

const statsLoading = ref(false)
const errorOccurred = ref(false)
const quickStats = ref({ totalUsers: 0, emailsSentToday: 0, quizCompletionsToday: 0 })
const topics = ref<TopicStatus[]>([])

// Canonical topic names (data/topics.ts) are what the backend filters on;
// they are rendered through topicLabel() but sent as-is.
const digestTopicFilter = ref('')
const allTopics = ALL_TOPICS

const generating = ref(false)
const sendingEmails = ref(false)
const cleaningUp = ref(false)
const showCleanupConfirm = ref(false)

const jobStatus = reactive<{
  digestGeneration: JobState
  emailSend: JobState
  cleanup: JobState
}>({
  digestGeneration: { running: false, lastRanAt: null, lastResult: null },
  emailSend: { running: false, lastRanAt: null, lastResult: null },
  cleanup: { running: false, lastRanAt: null, lastResult: null },
})

const fmtNum = (n?: number) => (n ?? 0).toLocaleString(intlLocale.value)

const formatDate = (value: string) =>
  new Date(value).toLocaleString(intlLocale.value, {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })

const formatDateShort = (value: string) =>
  new Date(value).toLocaleString(intlLocale.value, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })

const hasDigestErrors = (result: JobResult | null): boolean => {
  if (!result) return false
  if (result.failed) return true
  // Manual run emits failCount; the scheduled DigestGenerationJob emits digestFail
  if (((result.failCount ?? result.digestFail) ?? 0) > 0) return true
  if (result.sampleErrors && result.sampleErrors.length > 0) return true
  return false
}

const formatJobResult = (type: 'digests' | 'emails' | 'cleanup', result: JobResult): string => {
  if (result.failed) return t('admin.dashboard.result.failed')
  if (type === 'digests') {
    // Manual run uses successCount/totalUsers/failCount; the scheduled job uses
    // digestSuccess/usersProcessed/digestFail — normalize so both render cleanly.
    const success = result.successCount ?? result.digestSuccess ?? 0
    const total = result.totalUsers ?? result.usersProcessed ?? 0
    const fail = result.failCount ?? result.digestFail ?? 0
    return result.skippedCount
      ? t('admin.dashboard.result.digestsSkipped', { success, total, fail, skipped: result.skippedCount })
      : t('admin.dashboard.result.digests', { success, total, fail })
  }
  if (type === 'emails') {
    return t('admin.dashboard.result.emails', {
      sent: result.sentCount ?? 0,
      fail: result.failCount ?? 0,
      total: result.totalUsersProcessed ?? 0,
    })
  }
  if (type === 'cleanup') {
    return t('admin.dashboard.result.cleanup', {
      count: result.deletedDigests ?? 0,
      cutoff: result.cutoffDate ? formatDateShort(result.cutoffDate) : '—',
    })
  }
  return ''
}

const handleCleanup = async () => {
  cleaningUp.value = true
  jobStatus.cleanup.running = true
  jobStatus.cleanup.lastResult = null
  try {
    const response = await api.admin.runCleanup()
    const data = response.data
    jobStatus.cleanup.lastRanAt = new Date().toISOString()
    jobStatus.cleanup.lastResult = data
    showCleanupConfirm.value = false
    success(t('admin.dashboard.toast.cleanupDone', { count: data.deletedDigests }))
  } catch {
    jobStatus.cleanup.lastResult = { failed: true }
    error(t('admin.dashboard.toast.cleanupFailed'))
  } finally {
    cleaningUp.value = false
    jobStatus.cleanup.running = false
  }
}

type JobStatusEntry = {
  jobName: string
  lastRanAt: string | null
  lastStatus: string
  lastResult: JobResult | null
  running: boolean
}

const applyJobsStatus = (jobs: JobStatusEntry[]) => {
  const digestJob = jobs.find((j) => j.jobName === 'digest-generation')
  const emailJob = jobs.find((j) => j.jobName === 'email-send')
  const cleanupJobData = jobs.find((j) => j.jobName === 'cleanup')
  if (digestJob) {
    if (digestJob.lastStatus !== 'NEVER_RAN') {
      jobStatus.digestGeneration.lastRanAt = digestJob.lastRanAt
      jobStatus.digestGeneration.lastResult = digestJob.lastStatus === 'FAILED' ? { failed: true } : digestJob.lastResult
    }
    // Server-reported live state wins over the client-local optimistic flag, so
    // the "Running…" badge is correct on load and in other tabs.
    jobStatus.digestGeneration.running = digestJob.running
  }
  if (emailJob) {
    if (emailJob.lastStatus !== 'NEVER_RAN') {
      jobStatus.emailSend.lastRanAt = emailJob.lastRanAt
      jobStatus.emailSend.lastResult = emailJob.lastStatus === 'FAILED' ? { failed: true } : emailJob.lastResult
    }
    jobStatus.emailSend.running = emailJob.running
  }
  if (cleanupJobData && cleanupJobData.lastStatus !== 'NEVER_RAN') {
    jobStatus.cleanup.lastRanAt = cleanupJobData.lastRanAt
    jobStatus.cleanup.lastResult = cleanupJobData.lastStatus === 'FAILED' ? { failed: true } : cleanupJobData.lastResult
  }
}

// Re-poll just the job registry after a fire-and-forget generate/send trigger.
const refreshJobsStatus = async () => {
  try {
    const jobsResponse = await api.admin.getJobsStatus()
    applyJobsStatus(jobsResponse.data)
  } catch {
    // non-fatal: the trigger toast already fired; leave prior counts in place
  }
}

// The manual generate/send jobs run in the background (202 fire-and-forget) and the
// registry only records a result on COMPLETION, so we poll /admin/jobs/status until
// the job's lastRanAt advances past what it was at trigger time — then the fresh
// counts are on screen. The "Running…" indicator is driven by jobStatus.*.running.
type PollableJob = 'digestGeneration' | 'emailSend'
const jobPollers = new Map<PollableJob, ReturnType<typeof setInterval>>()

const stopPoll = (key: PollableJob) => {
  const timer = jobPollers.get(key)
  if (timer !== undefined) {
    clearInterval(timer)
    jobPollers.delete(key)
  }
  jobStatus[key].running = false
}

// The batches legitimately take minutes (the POST itself allows 600s), so the
// poll budget must comfortably exceed that — giving up at 2 minutes used to
// silently drop the "Running…" badge while the job was still crunching.
const MAX_JOB_POLL_ATTEMPTS = 220 // × 3s ≈ 11 min, past the backend's 600s ceiling

const pollJobUntilComplete = (key: PollableJob, prevRanAt: string | null) => {
  stopPoll(key)
  jobStatus[key].running = true
  let attempts = 0
  const timer = setInterval(async () => {
    attempts += 1
    await refreshJobsStatus()
    const now = jobStatus[key].lastRanAt
    if (now && now !== prevRanAt) {
      stopPoll(key) // completed — a fresh run was recorded
    } else if (attempts >= MAX_JOB_POLL_ATTEMPTS) {
      stopPoll(key)
      // Don't silently revert to idle: tell the admin the job may still be going.
      error(t('admin.dashboard.toast.jobSlow'))
    }
  }, 3000)
  jobPollers.set(key, timer)
}

onUnmounted(() => {
  jobPollers.forEach((timer) => clearInterval(timer))
  jobPollers.clear()
})

// Generate/send are now fire-and-forget: the backend returns 202 { status }
// immediately and runs the batch in the background, so we surface a toast and
// then poll /admin/jobs/status for live counts instead of reading the POST body.
const handleGenerateDigests = async () => {
  generating.value = true
  const prevRanAt = jobStatus.digestGeneration.lastRanAt
  try {
    const response = await api.admin.generateDigests(digestTopicFilter.value ? [digestTopicFilter.value] : undefined)
    if (response.data?.status === 'already_running') {
      error(t('admin.dashboard.toast.generateRunning'))
    } else {
      success(t('admin.dashboard.toast.generateStarted'))
    }
    pollJobUntilComplete('digestGeneration', prevRanAt)
  } catch {
    error(t('admin.dashboard.toast.generateFailed'))
  } finally {
    generating.value = false
  }
}

const handleSendEmails = async () => {
  sendingEmails.value = true
  const prevRanAt = jobStatus.emailSend.lastRanAt
  try {
    const response = await api.admin.sendEmails()
    if (response.data?.status === 'already_running') {
      error(t('admin.dashboard.toast.sendRunning'))
    } else {
      success(t('admin.dashboard.toast.sendStarted'))
    }
    pollJobUntilComplete('emailSend', prevRanAt)
  } catch {
    error(t('admin.dashboard.toast.sendFailed'))
  } finally {
    sendingEmails.value = false
  }
}

const loadDashboard = async () => {
  statsLoading.value = true
  errorOccurred.value = false
  try {
    const [statsResponse, topicsResponse, jobsResponse] = await Promise.all([
      api.admin.getStats(),
      api.admin.getTopicsStatus(),
      api.admin.getJobsStatus(),
    ])
    quickStats.value = statsResponse.data
    topics.value = topicsResponse.data
    applyJobsStatus(jobsResponse.data)
    // A batch may already be in flight (triggered from another tab, by cron, or
    // before a reload) — pick up the poll so the badge clears when it finishes.
    if (jobStatus.digestGeneration.running) {
      pollJobUntilComplete('digestGeneration', jobStatus.digestGeneration.lastRanAt)
    }
    if (jobStatus.emailSend.running) {
      pollJobUntilComplete('emailSend', jobStatus.emailSend.lastRanAt)
    }
  } catch {
    errorOccurred.value = true
    error(t('admin.dashboard.toast.loadFailed'))
  } finally {
    statsLoading.value = false
  }
}

onMounted(() => loadDashboard())
</script>
