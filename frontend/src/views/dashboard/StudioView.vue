<template>
  <div class="mx-auto max-w-[920px] px-5 py-10 sm:px-8 lg:px-12">
    <!-- ── Header ─────────────────────────────────────── -->
    <header class="mb-10">
      <p class="kicker kicker-signal mb-3">{{ t('studio.header.kicker') }}</p>
      <i18n-t scope="global" keypath="studio.header.headline" tag="h1" class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95] mb-6">
        <template #edition><em class="italic-display">{{ t('studio.header.edition') }}</em></template>
      </i18n-t>
      <div class="rule-double w-full"></div>
      <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed mt-6 max-w-[68ch]">
        {{ t('studio.header.lede') }}
      </p>
    </header>

    <div class="space-y-14">
      <!-- ── § I — Your key ─────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker kicker-signal">{{ t('studio.key.kicker') }}</p>
          <span class="kicker">{{ t('studio.key.optional') }}</span>
        </div>
        <ApiKeyManager />
        <p class="kicker mt-4 text-[color:var(--mute)]">
          {{ t('studio.key.fallback') }}
        </p>
      </section>

      <!-- ── § II — Generate ───────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">{{ t('studio.generate.kicker') }}</p>
          <span class="kicker">{{ t('studio.generate.topicCount', { count: overview?.topicCount ?? 0 }) }}</span>
        </div>

        <!-- No topics yet -->
        <div v-if="overview && overview.topicCount === 0" class="border-t border-b border-[color:var(--rule)] py-5 mb-6">
          <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-3">
            {{ t('studio.generate.noTopics') }}
          </p>
          <router-link to="/dashboard/settings" class="btn-editorial-ghost">
            {{ t('studio.generate.pickTopics') }} <span aria-hidden="true">→</span>
          </router-link>
        </div>

        <!-- Topic chips -->
        <div v-else-if="overview" class="flex flex-wrap gap-2 mb-6">
          <span
            v-for="topic in overview.topics"
            :key="topic"
            class="px-3 py-1.5 border border-[color:var(--rule)] bg-paper font-display text-[13px]"
          >{{ topicLabel(topic) }}</span>
        </div>

        <!-- Progress -->
        <TaskProgress :task="digestTask" />

        <button
          class="btn-editorial mt-6"
          :disabled="generateDisabled"
          @click="generate"
        >
          <span v-if="isActive(digestTask)">{{ t('studio.generate.working') }}</span>
          <span v-else>{{ t('studio.generate.button') }}</span>
          <span aria-hidden="true">→</span>
        </button>
      </section>

      <!-- ── § III — Send ──────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">{{ t('studio.send.kicker') }}</p>
          <span class="kicker">{{ overview?.email }}</span>
        </div>

        <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] leading-relaxed mb-2 max-w-[64ch]">
          {{ t('studio.send.body') }}
        </p>
        <p
          v-if="overview"
          class="kicker mb-6"
          :style="overview.hasUnsentDigest ? 'color: var(--leaf)' : 'color: var(--mute)'"
        >
          {{ overview.hasUnsentDigest ? t('studio.send.ready') : t('studio.send.none') }}
        </p>

        <!-- Progress -->
        <TaskProgress :task="emailTask" />

        <button
          class="btn-editorial mt-6"
          :disabled="isActive(emailTask)"
          @click="sendEmail"
        >
          <span v-if="isActive(emailTask)">{{ t('studio.send.sending') }}</span>
          <span v-else>{{ t('studio.send.button', { target: overview?.email || t('studio.send.myInbox') }) }}</span>
          <span aria-hidden="true">→</span>
        </button>
      </section>

      <!-- ── Latest edition ────────────────────────── -->
      <section v-if="overview?.latestDigest">
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">{{ t('studio.latest.kicker') }}</p>
        </div>
        <div class="flex items-baseline justify-between gap-4 flex-wrap">
          <div>
            <p class="font-display text-[16px] mb-1">
              {{ t('studio.latest.generated', { time: formatDateTime(overview.latestDigest.generatedAt) }) }}
            </p>
            <p class="kicker">
              {{ overview.latestDigest.emailSentAt
                ? t('studio.latest.emailed', { time: formatDateTime(overview.latestDigest.emailSentAt) })
                : t('studio.latest.notEmailed') }}
            </p>
          </div>
          <router-link
            :to="`/dashboard/quiz/${overview.latestDigest.id}`"
            class="btn-editorial-ghost"
          >{{ t('studio.latest.open') }} <span aria-hidden="true">→</span></router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, h, type PropType } from 'vue'
import { useI18n } from 'vue-i18n'
import ApiKeyManager from '@/components/settings/ApiKeyManager.vue'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { api, type StudioStatus, type StudioTask, type StudioTaskState } from '@/services/api'
import { getApiErrorMessage } from '@/utils/apiError'

const { t } = useI18n()
const { intlLocale } = useLocale()
const { topicLabel } = useTopicLabels()
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
      error(t('studio.errors.lostConnection'))
      // Release the stuck in-flight tasks so isActive() clears and the
      // buttons re-enable for a manual retry.
      if (status.value) {
        const message = t('studio.errors.lostConnectionShort')
        if (isActive(status.value.digest)) {
          status.value.digest = { ...status.value.digest, state: 'FAILED', message }
        }
        if (isActive(status.value.email)) {
          status.value.email = { ...status.value.email, state: 'FAILED', message }
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
    error(getApiErrorMessage(err, t('studio.errors.generateFailed')))
  }
}

const sendEmail = async () => {
  try {
    const res = await api.studio.sendEmail()
    if (status.value) status.value.email = res.data
    startPolling()
  } catch (err: unknown) {
    error(getApiErrorMessage(err, t('studio.errors.sendFailed')))
  }
}

const formatDateTime = (iso: string | null) => {
  if (!iso) return '—'
  const d = new Date(iso)
  return d.toLocaleString(intlLocale.value, {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

// Bare task states (shown only when the backend sends no message) render
// through the catalog so the Korean edition doesn't leak "SUCCESS"/"FAILED".
const stateLabel = (state: StudioTaskState): string => {
  switch (state) {
    case 'QUEUED': return t('studio.progress.state.queued')
    case 'RUNNING': return t('studio.progress.state.running')
    case 'SUCCESS': return t('studio.progress.state.success')
    case 'FAILED': return t('studio.progress.state.failed')
    case 'SKIPPED': return t('studio.progress.state.skipped')
    default: return state
  }
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
// Reads the parent's `t` (same global i18n scope) so the label re-renders on
// an edition switch.
const TaskProgress = {
  props: { task: { type: Object as PropType<StudioTask | undefined>, default: undefined } },
  setup(props: { task?: StudioTask }) {
    const current = () => props.task
    const running = () => current()?.state === 'QUEUED' || current()?.state === 'RUNNING'
    const stateColor = () => {
      switch (current()?.state) {
        case 'SUCCESS': return 'var(--leaf)'
        case 'FAILED': return 'var(--signal-deep)'
        case 'SKIPPED': return 'var(--mute)'
        case 'RUNNING':
        case 'QUEUED': return 'var(--signal-deep)'
        default: return 'var(--mute)'
      }
    }
    const pct = () => {
      const task = current()
      if (!task) return 0
      if (task.total && task.total > 0 && typeof task.current === 'number') {
        return Math.max(6, Math.round((task.current / task.total) * 100))
      }
      return 0
    }
    const label = (): string => {
      const task = current()
      if (!task || task.state === 'IDLE') return ''
      if (running()) {
        const sub = task.total && task.total > 0 ? ` (${task.current}/${task.total})` : ''
        return (task.phase || t('studio.progress.working')) + sub
      }
      return task.message || stateLabel(task.state)
    }
    return () => {
      const task = current()
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
