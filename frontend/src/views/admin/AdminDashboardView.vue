<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <PageMasthead
      :kicker="t('admin.dashboard.kicker')"
      keypath="admin.dashboard.headline"
      emphasis="desk"
      :emphasis-text="t('admin.dashboard.desk')"
    />

    <ErrorState
      v-if="errorOccurred"
      :kicker="t('admin.common.loadFailed')"
      :headline="t('admin.dashboard.loadFailedTitle')"
      @retry="loadDashboard"
    />

    <div v-else-if="statsLoading" class="border-t-2 border-[color:var(--rule)] pt-12 flex items-center justify-center gap-3 text-[color:var(--mute)]">
      <BaseSpinner :size="16" />
      <p class="kicker">{{ t('admin.dashboard.loading') }}</p>
    </div>

    <div v-else class="space-y-14">
      <!-- ── Quick stats ─────────────────────── -->
      <section class="grid grid-cols-3 border-t-2 border-b-2 border-[color:var(--rule)] divide-x divide-[color:var(--rule)]">
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none">{{ formatNumber(quickStats.totalUsers) }}</div>
          <p class="kicker mt-3">{{ t('admin.dashboard.stats.totalUsers') }}</p>
        </div>
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none text-[color:var(--leaf)]">{{ formatNumber(quickStats.emailsSentToday) }}</div>
          <p class="kicker mt-3">{{ t('admin.common.kpi.emailsToday') }}</p>
        </div>
        <div class="px-5 py-6">
          <div class="deco-num text-[44px] leading-none text-[color:var(--signal-deep)]">{{ formatNumber(quickStats.quizCompletionsToday) }}</div>
          <p class="kicker mt-3">{{ t('admin.common.kpi.quizzesToday') }}</p>
        </div>
      </section>

      <!-- ── Scheduled jobs ──────────────────── -->
      <section>
        <p class="kicker mb-4">{{ t('admin.dashboard.jobs.kicker') }}</p>
        <div class="space-y-0 border-t-2 border-b-2 border-[color:var(--rule)] divide-y divide-[color:var(--rule)]">
          <JobCard
            :title="t('admin.dashboard.jobs.generate.title')"
            :cron="t('admin.dashboard.jobs.generate.cron')"
            :body="t('admin.dashboard.jobs.generate.body')"
            :state="jobStatus.digestGeneration"
            :done-label="t('admin.dashboard.jobs.generate.done')"
            :busy="generating"
            :busy-label="t('admin.dashboard.jobs.running')"
            :result-text="formatJobResult('digests', jobStatus.digestGeneration.lastResult)"
            :detail-text="formatJobDetail('digests', jobStatus.digestGeneration.lastResult)"
            @run="handleGenerateDigests"
          >
            <template #controls>
              <select
                v-model="digestTopicFilter"
                class="bg-transparent border-b border-[color:var(--rule)] py-1 font-mono-curio text-[12px] text-[color:var(--ink)] focus:outline-none focus:border-[color:var(--signal)]"
              >
                <option value="">{{ t('admin.common.allBeats') }}</option>
                <option v-for="topic in allTopics" :key="topic" :value="topic">{{ topicLabel(topic) }}</option>
              </select>
            </template>
            <template #errors>
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
            </template>
          </JobCard>

          <JobCard
            :title="t('admin.dashboard.jobs.send.title')"
            :cron="t('admin.dashboard.jobs.send.cron')"
            :body="t('admin.dashboard.jobs.send.body')"
            :state="jobStatus.emailSend"
            :done-label="t('admin.dashboard.jobs.send.done')"
            :busy="sendingEmails"
            :busy-label="t('admin.dashboard.jobs.send.sending')"
            :result-text="formatJobResult('emails', jobStatus.emailSend.lastResult)"
            :detail-text="formatJobDetail('emails', jobStatus.emailSend.lastResult)"
            @run="handleSendEmails"
          />

          <JobCard
            :title="t('admin.dashboard.jobs.cleanup.title')"
            :cron="t('admin.dashboard.jobs.cleanup.cron')"
            :body="t('admin.dashboard.jobs.cleanup.body')"
            :state="jobStatus.cleanup"
            :done-label="t('admin.dashboard.jobs.cleanup.done')"
            :busy="cleaningUp"
            :busy-label="t('admin.dashboard.jobs.cleanup.recycling')"
            :result-text="formatJobResult('cleanup', jobStatus.cleanup.lastResult)"
            danger
            @run="showCleanupConfirm = true"
          />
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
                {{ topic.latestDigestGeneratedAt ? formatDateTime(topic.latestDigestGeneratedAt, 'compact') : '—' }}
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
import { onMounted, ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useFormat } from '@/composables/useFormat'
import { usePoller } from '@/composables/usePoller'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { ALL_TOPICS } from '@/data/topics'
import type { AdminStats, JobCardState, JobResult, JobStatusEntry, TopicStatus } from '@/types/admin'
import JobCard from '@/components/admin/JobCard.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import BaseSpinner from '@/components/ui/BaseSpinner.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageMasthead from '@/components/ui/PageMasthead.vue'

const { t } = useI18n()
const { formatNumber, formatDateTime, formatDuration } = useFormat()
const { topicLabel } = useTopicLabels()
const { error, success } = useToast()

const statsLoading = ref(false)
const errorOccurred = ref(false)
const quickStats = ref<AdminStats>({ totalUsers: 0, emailsSentToday: 0, quizCompletionsToday: 0 })
const topics = ref<TopicStatus[]>([])

// Canonical topic names (data/topics.ts) are what the backend filters on;
// they are rendered through topicLabel() but sent as-is.
const digestTopicFilter = ref('')
const allTopics = ALL_TOPICS

const generating = ref(false)
const sendingEmails = ref(false)
const cleaningUp = ref(false)
const showCleanupConfirm = ref(false)

type JobKey = 'digestGeneration' | 'emailSend' | 'cleanup'

const jobStatus = reactive<Record<JobKey, JobCardState>>({
  digestGeneration: { running: false, lastRanAt: null, lastResult: null },
  emailSend: { running: false, lastRanAt: null, lastResult: null },
  cleanup: { running: false, lastRanAt: null, lastResult: null },
})

// Registry job names, keyed by the card they drive.
const JOB_NAMES: ReadonlyArray<[JobKey, JobStatusEntry['jobName']]> = [
  ['digestGeneration', 'digest-generation'],
  ['emailSend', 'email-send'],
  ['cleanup', 'cleanup'],
]

// Generate/send are fire-and-forget batches (202 + background run), so their
// "Running…" badge follows the server and is polled. Cleanup is synchronous:
// its badge is the local request flag and the registry never overrides it.
type PollableJob = 'digestGeneration' | 'emailSend'
const isPollable = (key: JobKey): key is PollableJob => key !== 'cleanup'

const applyJobsStatus = (jobs: JobStatusEntry[]) => {
  for (const [key, jobName] of JOB_NAMES) {
    const job = jobs.find((j) => j.jobName === jobName)
    if (!job) continue
    if (job.lastStatus !== 'NEVER_RAN') {
      jobStatus[key].lastRanAt = job.lastRanAt
      jobStatus[key].lastResult = job.lastStatus === 'FAILED' ? { failed: true } : job.lastResult
    }
    // Server-reported live state wins over the client-local optimistic flag, so
    // the "Running…" badge is correct on load and in other tabs.
    if (isPollable(key)) jobStatus[key].running = job.running
  }
}

const hasDigestErrors = (result: JobResult | null): boolean => {
  if (!result) return false
  if (result.failed) return true
  if ((result.digestFail ?? 0) > 0) return true
  if (result.sampleErrors && result.sampleErrors.length > 0) return true
  return false
}

const formatJobResult = (type: 'digests' | 'emails' | 'cleanup', result: JobResult | null): string => {
  if (!result) return ''
  if (result.failed) return t('admin.dashboard.result.failed')
  if (type === 'digests') {
    const success = result.digestSuccess ?? 0
    const total = result.usersProcessed ?? 0
    const fail = result.digestFail ?? 0
    const skipped = result.digestSkipped ?? 0
    return skipped > 0
      ? t('admin.dashboard.result.digestsSkipped', { success, total, fail, skipped })
      : t('admin.dashboard.result.digests', { success, total, fail })
  }
  if (type === 'emails') {
    return t('admin.dashboard.result.emails', {
      sent: result.sentCount ?? 0,
      fail: result.failCount ?? 0,
      total: result.usersScanned ?? 0,
    })
  }
  return t('admin.dashboard.result.cleanup', {
    count: result.deletedDigests ?? 0,
    cutoff: result.cutoffDate ? formatDateTime(result.cutoffDate, 'compact') : '—',
  })
}

/** Second line under the result: quiz counts + beat filter (digests), users due (emails), duration. */
const formatJobDetail = (type: 'digests' | 'emails', result: JobResult | null): string => {
  if (!result || result.failed) return ''
  const parts: string[] = []
  if (type === 'digests') {
    if (result.quizSuccess !== undefined || result.quizFail !== undefined) {
      parts.push(t('admin.dashboard.result.quizzes', { success: result.quizSuccess ?? 0, fail: result.quizFail ?? 0 }))
    }
    if (result.topicFilter?.length) {
      parts.push(t('admin.dashboard.result.beats', { beats: result.topicFilter.map(topicLabel).join(', ') }))
    }
  } else if (result.usersProcessed !== undefined) {
    parts.push(t('admin.dashboard.result.due', { due: result.usersProcessed }))
  }
  if (result.durationMs !== undefined) {
    parts.push(t('admin.dashboard.result.took', { duration: formatDuration(result.durationMs) }))
  }
  return parts.join(' · ')
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

// The manual generate/send jobs run in the background and the registry only
// records a result on COMPLETION, so after a trigger we poll /admin/jobs/status
// until the job's lastRanAt advances past what it was at trigger time — then
// the fresh counts are on screen. One poller per job; both stop on unmount.
const JOB_POLL_INTERVAL_MS = 3000
// The batches legitimately take minutes (the POST itself allows 600s), so the
// poll budget comfortably exceeds that before the badge gives up.
const MAX_JOB_POLL_ATTEMPTS = 220 // × 3s ≈ 11 min, past the backend's 600s ceiling

const pollBaseline: Record<PollableJob, string | null> = { digestGeneration: null, emailSend: null }

const makeJobPoller = (key: PollableJob) =>
  usePoller({
    intervalMs: JOB_POLL_INTERVAL_MS,
    maxAttempts: MAX_JOB_POLL_ATTEMPTS,
    tick: async (isCurrent) => {
      const jobs = (await api.admin.getJobsStatus()).data
      if (!isCurrent()) return 'continue'
      applyJobsStatus(jobs)
      const ranAt = jobStatus[key].lastRanAt
      if (!ranAt || ranAt === pollBaseline[key]) return 'continue'
      jobStatus[key].running = false // completed — a fresh run was recorded
      return 'done'
    },
    onExhausted: () => {
      // Don't silently revert to idle: tell the admin the job may still be going.
      jobStatus[key].running = false
      error(t('admin.dashboard.toast.jobSlow'))
    },
  })

const jobPollers: Record<PollableJob, ReturnType<typeof usePoller>> = {
  digestGeneration: makeJobPoller('digestGeneration'),
  emailSend: makeJobPoller('emailSend'),
}

const pollJobUntilComplete = (key: PollableJob, prevRanAt: string | null) => {
  pollBaseline[key] = prevRanAt
  jobStatus[key].running = true
  jobPollers[key].start()
}

// Generate/send are fire-and-forget: the backend returns 202 { status }
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
