<template>
  <!-- "switch": sidebar-foot row, same shape as the Lights (theme) toggle -->
  <button
    v-if="variant === 'switch'"
    type="button"
    class="w-full flex items-center justify-between px-3 py-2 border border-[color:var(--rule)] hover:bg-paper-deep transition-colors"
    :aria-label="switchLabel"
    @click="toggleLocale"
  >
    <span class="kicker">{{ t('common.language.label') }}</span>
    <span class="font-mono-curio text-[11px] tracking-[0.18em] uppercase">
      <span lang="en" :class="locale === 'en' ? 'text-[color:var(--ink)]' : 'text-[color:var(--mute)]'">{{ t('common.language.en') }}</span>
      <span class="mx-2 text-[color:var(--mute)]" aria-hidden="true">·</span>
      <span lang="ko" :class="locale === 'ko' ? 'text-[color:var(--ink)]' : 'text-[color:var(--mute)]'">{{ t('common.language.ko') }}</span>
    </span>
  </button>

  <!-- "link": compact masthead / footer control naming the other edition -->
  <button
    v-else
    type="button"
    class="whitespace-nowrap font-mono-curio uppercase tracking-[0.14em] text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors"
    :lang="otherLocale"
    :aria-label="switchLabel"
    @click="toggleLocale"
  >
    {{ otherName }}
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocale } from '@/composables/useLocale'

withDefaults(defineProps<{ variant?: 'switch' | 'link' }>(), { variant: 'link' })

const { t } = useI18n()
const { locale, toggleLocale } = useLocale()

const otherLocale = computed(() => (locale.value === 'en' ? 'ko' : 'en'))
const otherName = computed(() => t(`common.language.${otherLocale.value}`))
const switchLabel = computed(() => t('common.language.switchTo', { language: otherName.value }))
</script>
