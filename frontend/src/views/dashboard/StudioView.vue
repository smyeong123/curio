<template>
  <div class="mx-auto max-w-[920px] px-5 py-10 sm:px-8 lg:px-12">
    <PageMasthead
      :kicker="t('studio.header.kicker')"
      keypath="studio.header.headline"
      emphasis="edition"
      :emphasis-text="t('studio.header.edition')"
    >
      <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed mt-6 max-w-[68ch]">
        {{ t('studio.header.lede') }}
      </p>
    </PageMasthead>

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

        <TaskProgress :task="digestTask" />

        <button
          class="btn-editorial mt-6"
          :disabled="generateDisabled"
          @click="generate"
        >
          <span v-if="isTaskActive(digestTask)">{{ t('studio.generate.working') }}</span>
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

        <TaskProgress :task="emailTask" />

        <button
          class="btn-editorial mt-6"
          :disabled="isTaskActive(emailTask)"
          @click="sendEmail"
        >
          <span v-if="isTaskActive(emailTask)">{{ t('studio.send.sending') }}</span>
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
              {{ t('studio.latest.generated', { time: stamp(overview.latestDigest.generatedAt) }) }}
            </p>
            <p class="kicker">
              {{ overview.latestDigest.emailSentAt
                ? t('studio.latest.emailed', { time: stamp(overview.latestDigest.emailSentAt) })
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
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ApiKeyManager from '@/components/settings/ApiKeyManager.vue'
import PageMasthead from '@/components/ui/PageMasthead.vue'
import TaskProgress from '@/components/studio/TaskProgress.vue'
import { useToast } from '@/composables/useToast'
import { useFormat } from '@/composables/useFormat'
import { usePoller } from '@/composables/usePoller'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { api } from '@/services/api'
import { isTaskActive, type StudioStatus, type StudioTask } from '@/types/studio'
import { getApiErrorMessage } from '@/utils/apiError'

const { t } = useI18n()
const { formatDateTime } = useFormat()
const { topicLabel } = useTopicLabels()
const { error } = useToast()

const status = ref<StudioStatus | null>(null)
const digestTask = computed<StudioTask | undefined>(() => status.value?.digest)
const emailTask = computed<StudioTask | undefined>(() => status.value?.email)
const overview = computed(() => status.value?.overview)

const anyActive = (s: StudioStatus) => isTaskActive(s.digest) || isTaskActive(s.email)

// Disabled only while a run is active or we KNOW the user has zero topics.
// `overview` being null means the status call hasn't succeeded yet — that must
// not disable the button (the backend skips gracefully if topics are missing).
const generateDisabled = computed(() =>
  isTaskActive(digestTask.value) || overview.value?.topicCount === 0
)

// Live progress while a task is in flight. A single failed poll (backend
// restart, mobile network blip, a token-refresh race) must not freeze the UI,
// so the loop only gives up after a run of consecutive failures.
const poller = usePoller({
  intervalMs: 1500,
  maxConsecutiveFailures: 5,
  tick: async (isCurrent) => {
    const { data } = await api.studio.getStatus()
    if (!isCurrent()) return 'continue'
    status.value = data
    return anyActive(data) ? 'continue' : 'done'
  },
  onFailed: () => {
    error(t('studio.errors.lostConnection'))
    // Release the stuck in-flight tasks so isTaskActive() clears and the
    // buttons re-enable for a manual retry.
    if (!status.value) return
    const message = t('studio.errors.lostConnectionShort')
    if (isTaskActive(status.value.digest)) {
      status.value.digest = { ...status.value.digest, state: 'FAILED', message }
    }
    if (isTaskActive(status.value.email)) {
      status.value.email = { ...status.value.email, state: 'FAILED', message }
    }
  },
})

// Idempotent: restarting a live poll would reset its failure budget and
// orphan the tick in flight.
const startPolling = () => {
  if (!poller.active.value) poller.start()
}

// One-off status load; resolves to whether a task is still in flight.
const fetchStatus = async () => {
  const { data } = await api.studio.getStatus()
  status.value = data
  return anyActive(data)
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

const stamp = (iso: string | null) => (iso ? formatDateTime(iso, 'compact') : '—')

onMounted(async () => {
  try {
    if (await fetchStatus()) startPolling()
  } catch {
    // Don't give up after one failed load (commonly a token-refresh race right
    // after login): let the bounded poll loop retry. It stops itself either on
    // the first successful idle status or once the failure budget is spent.
    startPolling()
  }
})
</script>
