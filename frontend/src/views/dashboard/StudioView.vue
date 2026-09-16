<template>
  <div class="mx-auto max-w-[920px] px-5 py-10 sm:px-8 lg:px-12">
    <!-- ── Header ─────────────────────────────────────── -->
    <header class="mb-10">
      <p class="kicker kicker-signal mb-3">Section VI — The Press Room</p>
      <h1 class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95] mb-6">
        Run your own
        <em class="italic-display">edition</em>.
      </h1>
      <div class="rule-double w-full"></div>
      <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed mt-6 max-w-[68ch]">
        Don't want to wait for the morning delivery? Drive it yourself. Bring your own
        API key (optional), generate today's digest across every topic you've picked,
        then send it straight to your inbox. Every topic is open during the free testing period.
      </p>
    </header>

    <div class="space-y-14">
      <!-- ── § I — Your key ─────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker kicker-signal">§ I — Your key</p>
          <span class="kicker">Optional</span>
        </div>
        <ApiKeyManager />
        <p class="kicker mt-4 text-[color:var(--mute)]">
          No key? No problem — generation falls back to Curio's own key.
        </p>
      </section>

      <!-- ── § II — Generate ───────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">§ II — Generate today's digest</p>
          <span class="kicker">{{ overview?.topicCount ?? 0 }} topics</span>
        </div>

        <!-- No topics yet -->
        <div v-if="overview && overview.topicCount === 0" class="border-t border-b border-[color:var(--rule)] py-5 mb-6">
          <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-3">
            You haven't picked any topics yet. Choose your beats first.
          </p>
          <router-link to="/dashboard/settings" class="btn-editorial-ghost">
            Pick topics in Settings <span aria-hidden="true">→</span>
          </router-link>
        </div>

        <!-- Topic chips -->
        <div v-else-if="overview" class="flex flex-wrap gap-2 mb-6">
          <span
            v-for="topic in overview.topics"
            :key="topic"
            class="px-3 py-1.5 border border-[color:var(--rule)] bg-paper font-display text-[13px]"
          >{{ topic }}</span>
        </div>

        <!-- Progress -->
        <TaskProgress :task="digestTask" />

        <button
          class="btn-editorial mt-6"
          :disabled="generateDisabled"
          @click="generate"
        >
          <span v-if="isActive(digestTask)">Working…</span>
          <span v-else>Generate today's digest</span>
          <span aria-hidden="true">→</span>
        </button>
      </section>

      <!-- ── § III — Send ──────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">§ III — Send to your inbox</p>
          <span class="kicker">{{ overview?.email }}</span>
        </div>

        <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] leading-relaxed mb-2 max-w-[64ch]">
          Emails the most recent digest you haven't sent yet. Generate one above first if
          you don't have an unsent edition waiting.
        </p>
        <p
          v-if="overview"
          class="kicker mb-6"
          :style="overview.hasUnsentDigest ? 'color: var(--leaf)' : 'color: var(--mute)'"
        >
          {{ overview.hasUnsentDigest ? '● An unsent digest is ready to send' : '○ No unsent digest waiting' }}
        </p>

        <!-- Progress -->
        <TaskProgress :task="emailTask" />

        <button
          class="btn-editorial mt-6"
          :disabled="isActive(emailTask)"
          @click="sendEmail"
        >
          <span v-if="isActive(emailTask)">Sending…</span>
          <span v-else>Send to {{ overview?.email || 'my inbox' }}</span>
          <span aria-hidden="true">→</span>
        </button>
      </section>

      <!-- ── Latest edition ────────────────────────── -->
      <section v-if="overview?.latestDigest">
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">§ IV — Latest edition</p>
        </div>
        <div class="flex items-baseline justify-between gap-4 flex-wrap">
          <div>
            <p class="font-display text-[16px] mb-1">
              Generated {{ formatDateTime(overview.latestDigest.generatedAt) }}
            </p>
            <p class="kicker">
              {{ overview.latestDigest.emailSentAt
                ? `Emailed ${formatDateTime(overview.latestDigest.emailSentAt)}`
                : 'Not yet emailed' }}
            </p>
          </div>
          <router-link
            :to="`/dashboard/quiz/${overview.latestDigest.id}`"
            class="btn-editorial-ghost"
          >Open <span aria-hidden="true">→</span></router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, h, type PropType } from 'vue'
import ApiKeyManager from '@/components/settings/ApiKeyManager.vue'
import { useToast } from '@/composables/useToast'
import { api, type StudioStatus, type StudioTask } from '@/services/api'
import { getApiErrorMessage } from '@/utils/apiError'

const { error } = useToast()

const status = ref<StudioStatus | null>(null)
const digestTask = computed<StudioTask | undefined>(() => status.value?.digest)
const emailTask = computed<StudioTask | undefined>(() => status.value?.email)
const overview = computed(() => status.value?.overview)

const isActive = (task?: StudioTask) =>
  task?.state === 'QUEUED' || task?.state === 'RUNNING'

// Disabled only while a run is active or we KNOW the user has zero topics.
// `overview` being null means the status call hasn't succeeded yet — that must
// not disable the button (the backend skips gracefully if topics are missing).
const generateDisabled = computed(() =>
  isActive(digestTask.value) || overview.value?.topicCount === 0
)

// Polling is a self-scheduling setTimeout loop, NOT setInterval: the next poll
// is only armed after the previous response lands, so requests never overlap.
// fetchSeq additionally drops out-of-order responses (a stale "still RUNNING"
// arriving after a newer "finished" would otherwise freeze the progress bar).
let pollTimer: ReturnType<typeof setTimeout> | null = null
let polling = false
let fetchSeq = 0
// A single failed poll (backend restart, mobile network blip, a token-refresh
// race) must not permanently freeze the UI — count consecutive failures and
// only give up after a bound so a transient blip self-heals.
let consecutiveFailures = 0
const MAX_POLL_FAILURES = 5

const stopPolling = () => {
  polling = false
  if (pollTimer) { clearTimeout(pollTimer); pollTimer = null }
}

const fetchStatus = async () => {
  const seq = ++fetchSeq
  try {
    const res = await api.studio.getStatus()
    if (seq !== fetchSeq) return // a newer request has since started — drop this stale response
    status.value = res.data
    consecutiveFailures = 0
    // Stop polling once neither task is in flight.
    if (!isActive(res.data.digest) && !isActive(res.data.email)) {
      stopPolling()
    }
  } catch (e) {
    if (seq !== fetchSeq) return
    consecutiveFailures += 1
    if (consecutiveFailures >= MAX_POLL_FAILURES) {
      stopPolling()
      error('Lost connection to Studio — reload to refresh status')
      // Release the stuck in-flight tasks so isActive() clears and the
      // buttons re-enable for a manual retry.
      if (status.value) {
        if (isActive(status.value.digest)) {
          status.value.digest = { ...status.value.digest, state: 'FAILED', message: 'Lost connection' }
        }
        if (isActive(status.value.email)) {
          status.value.email = { ...status.value.email, state: 'FAILED', message: 'Lost connection' }
        }
      }
    }
    // Below the threshold, the loop stays alive so the next tick retries.
    throw e
  }
}

const scheduleNextPoll = () => {
  if (!polling) return
  pollTimer = setTimeout(async () => {
    try {
      await fetchStatus()
    } catch {
      /* failure already counted in fetchStatus */
    }
    scheduleNextPoll()
  }, 1500)
}

const startPolling = () => {
  if (polling) return
  polling = true
  consecutiveFailures = 0
  scheduleNextPoll()
}

const generate = async () => {
  try {
    const res = await api.studio.generate()
    if (status.value) status.value.digest = res.data
    startPolling()
  } catch (err: unknown) {
    error(getApiErrorMessage(err, 'Could not start digest generation'))
  }
}

const sendEmail = async () => {
  try {
    const res = await api.studio.sendEmail()
    if (status.value) status.value.email = res.data
    startPolling()
  } catch (err: unknown) {
    error(getApiErrorMessage(err, 'Could not start email send'))
  }
}

const formatDateTime = (iso: string | null) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

onMounted(async () => {
  try {
    await fetchStatus()
    if (isActive(digestTask.value) || isActive(emailTask.value)) startPolling()
  } catch {
    // Don't give up after one failed load (commonly a token-refresh race right
    // after login): let the bounded poll loop retry. It stops itself either on
    // the first successful idle status or after MAX_POLL_FAILURES.
    startPolling()
  }
})

onBeforeUnmount(stopPolling)

// ── Inline progress sub-component ───────────────────────────────────
// Renders a task's live state: a status line + a determinate/indeterminate bar.
const TaskProgress = {
  props: { task: { type: Object as PropType<StudioTask | undefined>, default: undefined } },
  setup(props: { task?: StudioTask }) {
    const t = () => props.task
    const running = () => t()?.state === 'QUEUED' || t()?.state === 'RUNNING'
    const stateColor = () => {
      switch (t()?.state) {
        case 'SUCCESS': return 'var(--leaf)'
        case 'FAILED': return 'var(--signal-deep)'
        case 'SKIPPED': return 'var(--mute)'
        case 'RUNNING':
        case 'QUEUED': return 'var(--signal-deep)'
        default: return 'var(--mute)'
      }
    }
    const pct = () => {
      const task = t()
      if (!task) return 0
      if (task.total && task.total > 0 && typeof task.current === 'number') {
        return Math.max(6, Math.round((task.current / task.total) * 100))
      }
      return 0
    }
    const label = (): string => {
      const task = t()
      if (!task || task.state === 'IDLE') return ''
      if (running()) {
        const sub = task.total && task.total > 0 ? ` (${task.current}/${task.total})` : ''
        return (task.phase || 'Working…') + sub
      }
      return task.message || task.state
    }
    return () => {
      const task = t()
      if (!task || task.state === 'IDLE') return null
      const showBar = running()
      const determinate = (task.total ?? 0) > 0
      return h('div', { class: 'border-t border-b border-[color:var(--rule)] py-4' }, [
        h('p', {
          class: 'font-mono-curio text-[12px] uppercase tracking-[0.12em] mb-3',
          style: { color: stateColor() },
        }, label()),
        showBar
          ? h('div', { class: 'h-1.5 w-full bg-paper-deep overflow-hidden' }, [
              determinate
                ? h('div', {
                    class: 'h-full transition-all duration-500',
                    style: { width: pct() + '%', backgroundColor: 'var(--signal)' },
                  })
                : h('div', {
                    class: 'h-full w-1/3 animate-pulse',
                    style: { backgroundColor: 'var(--signal)' },
                  }),
            ])
          : null,
      ])
    }
  },
}
</script>
