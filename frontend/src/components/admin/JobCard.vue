<template>
  <!-- One row of the press-operations list: title, cron line, live badge,
       description, optional controls, last-run summary and the trigger button. -->
  <article class="py-6">
    <div class="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
      <div class="flex-1">
        <div class="flex items-baseline flex-wrap gap-3 mb-2">
          <p class="display-headline text-[22px] leading-tight">{{ title }}</p>
          <span class="kicker">{{ cron }}</span>
          <span v-if="state.running" class="kicker inline-flex items-center gap-1.5" style="color: var(--signal-deep);"><span class="inline-block w-[6px] h-[6px] rounded-full bg-current animate-pulse"></span>{{ t('admin.dashboard.jobs.running') }}</span>
          <span
            v-else-if="state.lastResult"
            class="kicker"
            :style="state.lastResult.failed ? 'color: #b91c1c' : 'color: var(--leaf)'"
          >● {{ state.lastResult.failed ? t('admin.dashboard.jobs.failed') : doneLabel }}</span>
        </div>
        <p :class="['font-body-curio text-[13.5px] text-[color:var(--ink-soft)] max-w-[60ch]', { 'mb-3': $slots.controls }]">
          {{ body }}
        </p>
        <slot name="controls" />
        <p v-if="state.lastRanAt" class="kicker mt-3">
          {{ t('admin.dashboard.jobs.lastRan', { time: formatDateTime(state.lastRanAt, 'short') }) }}
        </p>
        <p v-if="state.lastResult" class="font-body-curio text-[13px] text-[color:var(--ink)] mt-1">
          {{ resultText }}
        </p>
        <p v-if="detailText" class="kicker mt-1">{{ detailText }}</p>
        <slot name="errors" />
      </div>
      <button :class="buttonClass" :disabled="busy || state.running" @click="emit('run')">
        <span v-if="busy || state.running">{{ busyLabel }}</span>
        <span v-else>{{ t('admin.dashboard.jobs.runNow') }}</span>
        <BaseSpinner v-if="busy || state.running" :size="13" />
        <span v-else aria-hidden="true">{{ danger ? '↗' : '→' }}</span>
      </button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFormat } from '@/composables/useFormat'
import type { JobCardState } from '@/types/admin'
import BaseSpinner from '@/components/ui/BaseSpinner.vue'

const props = withDefaults(defineProps<{
  title: string
  cron: string
  body: string
  state: JobCardState
  /** Badge text once the last run succeeded ("Filed", "Delivered", …). */
  doneLabel: string
  /** The trigger request itself is in flight (before the server reports `running`). */
  busy: boolean
  /** Button text while busy or running. */
  busyLabel: string
  /** One-line summary of `state.lastResult`, formatted by the parent. */
  resultText: string
  /** Optional second line (quiz counts, beat filter, duration). */
  detailText?: string
  /** Destructive (red) button styling — the cleanup job. */
  danger?: boolean
}>(), { danger: false, detailText: '' })

const emit = defineEmits<{ run: [] }>()

const { t } = useI18n()
const { formatDateTime } = useFormat()

const buttonClass = computed(() =>
  props.danger
    ? 'inline-flex items-center gap-3 border border-red-400 bg-transparent px-6 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] text-red-600 hover:bg-red-600 hover:text-white transition-colors disabled:opacity-50'
    : 'btn-editorial flex-shrink-0'
)
</script>
