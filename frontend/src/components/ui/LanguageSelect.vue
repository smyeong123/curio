<template>
  <!-- "row": sidebar-foot row, same shape as the Lights (theme) toggle.
       "inline": compact masthead / footer control. -->
  <div :class="variant === 'row' ? rowClass : inlineClass">
    <span v-if="variant === 'row'" class="kicker">{{ t('common.language.label') }}</span>
    <!-- Visual label only: the overlaid <select> already announces its name and value. -->
    <span aria-hidden="true" :class="variant === 'row' ? 'font-mono-curio text-[11px] tracking-[0.18em] uppercase text-[color:var(--ink)]' : ''">
      <span v-if="variant === 'inline'" class="sm:hidden">{{ locale.toUpperCase() }}</span>
      <span :lang="locale" :class="variant === 'inline' ? 'hidden sm:inline' : ''">{{ t(`common.language.${locale}`) }}</span>
      <span :class="variant === 'row' ? 'ml-2 text-[color:var(--mute)]' : 'ml-1.5'">▾</span>
    </span>
    <select
      :value="locale"
      :aria-label="t('common.language.select')"
      class="absolute inset-0 h-full w-full cursor-pointer appearance-none opacity-0"
      @change="onChange"
    >
      <option
        v-for="option in locales"
        :key="option"
        :value="option"
        :lang="option"
        class="bg-paper text-[color:var(--ink)]"
      >
        {{ t(`common.language.${option}`) }}
      </option>
    </select>
  </div>
</template>

<script setup lang="ts">
// Edition (UI language) dropdown. A native <select> stretched invisibly over a
// styled label, so the trigger keeps the editorial type while the option list
// stays the platform's own (keyboard, screen readers, mobile pickers). Each
// option names its edition in its own language; below `sm` the inline trigger
// shrinks to the locale code (EN / KO) so phone mastheads don't overflow.
// Changes only what is on screen; the account's digest language is chosen in Settings.

import { useI18n } from 'vue-i18n'
import { useLocale } from '@/composables/useLocale'
import { isLocale } from '@/i18n'

withDefaults(defineProps<{ variant?: 'row' | 'inline' }>(), { variant: 'inline' })

const { t } = useI18n()
const { locale, locales, setLocale } = useLocale()

const focusRing = 'has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[color:var(--signal)]'
const rowClass = `relative w-full flex items-center justify-between px-3 py-2 border border-[color:var(--rule)] hover:bg-paper-deep transition-colors ${focusRing}`
const inlineClass = `relative inline-flex items-center whitespace-nowrap font-mono-curio uppercase tracking-[0.14em] text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors ${focusRing}`

const onChange = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  if (isLocale(value)) setLocale(value)
}
</script>
