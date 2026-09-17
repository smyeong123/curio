<template>
  <div class="border-t border-[color:var(--rule)] p-4 space-y-2">
    <LanguageToggle variant="switch" />
    <button
      class="w-full flex items-center justify-between px-3 py-2 border border-[color:var(--rule)] hover:bg-paper-deep transition-colors"
      :aria-pressed="isDark"
      :aria-label="t('layout.theme.toggle')"
      @click="emit('toggle-theme')"
    >
      <span class="kicker">{{ t('layout.theme.label') }}</span>
      <span class="font-mono-curio text-[11px] tracking-[0.18em] uppercase">
        <span :class="isDark ? 'text-[color:var(--mute)]' : 'text-[color:var(--ink)]'">{{ t('layout.theme.day') }}</span>
        <span class="mx-2 text-[color:var(--mute)]">·</span>
        <span :class="isDark ? 'text-[color:var(--ink)]' : 'text-[color:var(--mute)]'">{{ t('layout.theme.night') }}</span>
      </span>
    </button>
    <button
      class="w-full flex items-center justify-between px-3 py-2 hover:bg-paper-deep transition-colors"
      @click="emit('sign-out')"
    >
      <span class="font-display text-[15px]">{{ t('layout.signOut') }}</span>
      <span aria-hidden="true" class="font-mono-curio text-[14px] text-[color:var(--mute)]">↗</span>
    </button>
  </div>
</template>

<script setup lang="ts">
// Edition + theme switches and sign-out, at the foot of both menus.

import { useI18n } from 'vue-i18n'
import LanguageToggle from '@/components/ui/LanguageToggle.vue'

defineProps<{ isDark: boolean }>()
const emit = defineEmits<{ 'toggle-theme': []; 'sign-out': [] }>()

const { t } = useI18n()
</script>
