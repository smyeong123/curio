<template>
  <router-link v-if="onInk" to="/" class="flex items-baseline gap-3">
    <span class="display-headline text-[36px] leading-none">Curio</span>
    <span class="kicker" style="color: var(--paper); opacity: 0.6;">{{ t('layout.masthead.vol') }}</span>
  </router-link>
  <div v-else class="mb-10 flex items-baseline justify-between gap-4">
    <router-link to="/" class="flex items-baseline gap-3">
      <span class="display-headline text-[28px] leading-none">Curio</span>
      <span class="kicker">{{ t('layout.masthead.vol') }}</span>
    </router-link>
    <LanguageToggle class="text-[12px]" />
  </div>
</template>

<script setup lang="ts">
// Brand + volume line at the top of the auth pages. On the ink aside the brand
// stands alone (the edition toggle sits in that aside's colophon row); on paper
// it shares a row with the toggle.

import { useI18n } from 'vue-i18n'
import LanguageToggle from '@/components/ui/LanguageToggle.vue'

withDefaults(defineProps<{ onInk?: boolean }>(), { onInk: false })

const { t } = useI18n()
</script>
