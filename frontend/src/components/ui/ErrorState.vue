<template>
  <!-- The one "couldn't load — retry" block every data view shows. Spacing
       tightens when the headline or body is omitted (kicker only, kicker +
       headline, or all three). -->
  <section class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center" role="alert">
    <p :class="['kicker kicker-signal', headline ? 'mb-4' : 'mb-3']">{{ kicker }}</p>
    <h3 v-if="headline" :class="['display-headline text-[28px]', body ? 'mb-2' : 'mb-3']">{{ headline }}</h3>
    <p v-if="body" class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 max-w-md mx-auto">{{ body }}</p>
    <button type="button" class="btn-editorial-ghost" @click="emit('retry')">{{ retryLabel ?? t('common.actions.retry') }}</button>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  kicker: string
  headline?: string
  body?: string
  /** Button text; defaults to the shared retry copy. */
  retryLabel?: string
}>()

const emit = defineEmits<{ retry: [] }>()

const { t } = useI18n()
</script>
