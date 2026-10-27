<template>
  <!-- Foot of the accent aside on the auth pages: colophon line, language menu
       and today's date stamp. -->
  <div class="flex items-end justify-between">
    <p class="kicker" style="opacity: 0.85;">{{ t('auth.common.colophon') }}</p>
    <div class="flex items-baseline gap-5">
      <LanguageSelect class="text-[11px]" style="opacity: 0.85;" />
      <p class="kicker" style="opacity: 0.85;">{{ todayShort }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFormat } from '@/composables/useFormat'
import LanguageSelect from '@/components/ui/LanguageSelect.vue'

const { t } = useI18n()
const { formatDate } = useFormat()

const todayShort = computed(() => formatDate(new Date(), 'stamp').toUpperCase())
</script>
