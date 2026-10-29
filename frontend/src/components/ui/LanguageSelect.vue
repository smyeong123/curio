<template>
  <!-- "row": full-width sidebar-foot row above the Lights (theme) toggle.
       "inline": compact masthead / footer control. -->
  <div :class="variant === 'row' ? rowClass : inlineClass">
    <span v-if="variant === 'row'" class="kicker">{{ t('common.language.label') }}</span>
    <!-- Visual label only: the overlaid <select> already announces its name and value. -->
    <span aria-hidden="true" :class="variant === 'row' ? 'font-mono-curio text-[11px] tracking-[0.18em] uppercase text-[color:var(--ink)]' : ''">
      <span v-if="variant === 'inline'" class="sm:hidden">{{ locale.toUpperCase() }}</span>
      <span :lang="locale" :class="variant === 'inline' ? 'hidden sm:inline' : ''">{{ t(`common.language.${locale}`) }}</span>
    </span>
    <select
      :value="locale"
      :aria-label="t('common.language.select')"
      class="select-glass-overlay"
      @change="onChange"
    >
      <option
        v-for="option in locales"
        :key="option"
        :value="option"
        :lang="option"
      >
        {{ t(`common.language.${option}`) }}
      </option>
    </select>
  </div>
</template>

<script setup lang="ts">
// Edition (UI language) dropdown: a Liquid Glass capsule (.select-glass). A
// native <select> stretched invisibly over a styled label, so the trigger keeps
// the editorial type while the option list stays a real select (keyboard,
// screen readers, mobile pickers; a glass panel where base-select exists). Each
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
const rowClass = `select-glass relative w-full flex items-center justify-between py-2 pl-4 pr-9 ${focusRing}`
const inlineClass = `select-glass relative inline-flex items-center whitespace-nowrap py-1.5 pl-3 pr-8 font-mono-curio uppercase tracking-[0.14em] ${focusRing}`

const onChange = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  if (isLocale(value)) setLocale(value)
}
</script>
