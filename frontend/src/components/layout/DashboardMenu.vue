<template>
  <nav class="flex-1 px-3 py-5 space-y-1">
    <template v-for="(section, s) in sections" :key="section.key">
      <div v-if="s > 0" class="my-4 mx-3 border-t border-[color:var(--rule)]"></div>
      <p :class="section.headingClass">{{ section.heading }}</p>
      <router-link
        v-for="(item, i) in section.items"
        :key="item.to"
        :to="item.to"
        :class="linkClass(isActive(item.match))"
        @click="emit('navigate')"
      >
        <span :class="numberClass(isActive(item.match))">{{ String(i + 1).padStart(2, '0') }}</span>
        <span class="font-display text-[16px] leading-tight">{{ item.label }}</span>
      </router-link>
    </template>
  </nav>
</template>

<script setup lang="ts">
// Section list shared by the desktop sidebar and the mobile drawer. The admin
// "Newsroom" group follows the reader sections behind a divider.

import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

export interface MenuItem {
  label: string
  to: string
  /** Route-path prefixes that count as "this section is open". */
  match: readonly string[]
}

const props = withDefaults(defineProps<{
  navItems: MenuItem[]
  adminItems: MenuItem[]
  showAdmin?: boolean
  /** Drawer layout: taller touch rows, no hover state. */
  compact?: boolean
}>(), { showAdmin: false, compact: false })

const emit = defineEmits<{ navigate: [] }>()

const { t } = useI18n()
const route = useRoute()

// A section is open while the current path starts with one of its prefixes.
const isActive = (match: readonly string[]) => match.some((path) => route.path.startsWith(path))

const sections = computed(() => {
  const groups = [
    { key: 'sections', heading: t('layout.sections'), headingClass: 'px-3 mb-2 kicker', items: props.navItems },
  ]
  if (props.showAdmin) {
    groups.push({ key: 'newsroom', heading: t('layout.newsroom'), headingClass: 'px-3 mb-2 kicker kicker-signal', items: props.adminItems })
  }
  return groups
})

const linkClass = (active: boolean) => [
  props.compact ? 'flex items-baseline gap-3 px-3 py-3 transition-colors' : 'flex items-baseline gap-3 px-3 py-2.5 transition-colors',
  active
    ? 'bg-ink text-[color:var(--paper)]'
    : props.compact ? 'text-[color:var(--ink)]' : 'text-[color:var(--ink)] hover:bg-paper-deep',
]

const numberClass = (active: boolean) =>
  props.compact
    ? 'num-tab text-[10px] text-[color:var(--mute)]'
    : ['num-tab text-[10px] flex-shrink-0', active ? 'opacity-70' : 'text-[color:var(--mute)]']
</script>
