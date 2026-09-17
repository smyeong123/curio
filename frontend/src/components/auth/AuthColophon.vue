<template>
  <!-- Foot of the ink aside on the auth pages: colophon line, edition toggle
       and today's date stamp. -->
  <div class="flex items-end justify-between">
    <p class="kicker" style="color: var(--paper); opacity: 0.5;">{{ t('auth.common.colophon') }}</p>
    <div class="flex items-baseline gap-5">
      <LanguageToggle class="text-[11px]" style="color: var(--paper); opacity: 0.6;" />
      <p class="kicker" style="color: var(--paper); opacity: 0.5;">{{ todayShort }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFormat } from '@/composables/useFormat'
import LanguageToggle from '@/components/ui/LanguageToggle.vue'

const { t } = useI18n()
const { formatDate } = useFormat()

const todayShort = computed(() => formatDate(new Date(), 'stamp').toUpperCase())
</script>
