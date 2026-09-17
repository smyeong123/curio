<template>
  <!-- A Studio task's live state: a status line plus a determinate bar when the
       backend reports progress counts, an indeterminate one otherwise. -->
  <div v-if="task && task.state !== 'IDLE'" class="border-t border-b border-[color:var(--rule)] py-4">
    <p class="font-mono-curio text-[12px] uppercase tracking-[0.12em] mb-3" :style="{ color: stateColor }">{{ label }}</p>
    <div v-if="running" class="h-1.5 w-full bg-paper-deep overflow-hidden">
      <div
        v-if="determinate"
        class="h-full transition-all duration-500"
        :style="{ width: pct + '%', backgroundColor: 'var(--signal)' }"
      ></div>
      <div v-else class="h-full w-1/3 animate-pulse" :style="{ backgroundColor: 'var(--signal)' }"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { isTaskActive, type StudioTask, type StudioTaskState } from '@/types/studio'

const props = defineProps<{ task?: StudioTask }>()

const { t } = useI18n()

const running = computed(() => isTaskActive(props.task))
const determinate = computed(() => (props.task?.total ?? 0) > 0)

const stateColor = computed(() => {
  switch (props.task?.state) {
    case 'SUCCESS': return 'var(--leaf)'
    case 'FAILED': return 'var(--signal-deep)'
    case 'SKIPPED': return 'var(--mute)'
    case 'RUNNING':
    case 'QUEUED': return 'var(--signal-deep)'
    default: return 'var(--mute)'
  }
})

const pct = computed(() => {
  const task = props.task
  if (task?.total && task.total > 0 && typeof task.current === 'number') {
    return Math.max(6, Math.round((task.current / task.total) * 100))
  }
  return 0
})

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

const label = computed(() => {
  const task = props.task
  if (!task || task.state === 'IDLE') return ''
  if (running.value) {
    const sub = task.total && task.total > 0 ? ` (${task.current}/${task.total})` : ''
    return (task.phase || t('studio.progress.working')) + sub
  }
  return task.message || stateLabel(task.state)
})
</script>
