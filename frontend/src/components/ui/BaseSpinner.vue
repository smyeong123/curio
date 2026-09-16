<template>
  <svg
    class="animate-spin shrink-0"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    role="status"
    :aria-label="t('common.a11y.loading')"
  >
    <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="3" stroke-opacity="0.25" />
    <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
  </svg>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

withDefaults(defineProps<{ size?: number | string }>(), { size: 14 })

const { t } = useI18n()
</script>
