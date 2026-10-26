<template>
  <nav ref="navRef" class="relative flex-1 px-3 py-5 space-y-1">
    <template v-for="(section, s) in sections" :key="section.key">
      <div v-if="s > 0" class="my-4 mx-3 border-t border-[color:var(--rule)]"></div>
      <p :class="section.headingClass">{{ section.heading }}</p>
      <router-link
        v-for="(item, i) in section.items"
        :key="item.to"
        :to="item.to"
        :class="linkClass(isActive(item.match))"
        :data-active="isActive(item.match) || undefined"
        @click="emit('navigate')"
      >
        <span :class="numberClass(isActive(item.match))">{{ String(i + 1).padStart(2, '0') }}</span>
        <span class="font-display text-[16px] leading-tight">{{ item.label }}</span>
      </router-link>
    </template>
    <!-- Desktop: one ink slab that slides to the open section. Last child so
         the space-y sibling margins are unchanged. -->
    <span
      v-if="slab"
      class="menu-indicator"
      :class="{ 'is-animated': slabAnimated }"
      :style="{ transform: `translateY(${slab.top}px)`, height: `${slab.height}px` }"
      aria-hidden="true"
    ></span>
  </nav>
</template>

<script setup lang="ts">
// Section list shared by the desktop sidebar and the mobile drawer. The admin
// "Newsroom" group follows the reader sections behind a divider.

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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

// The sliding slab is measured from the active link, so it only exists once
// the menu has real layout (not in the drawer, which opens fresh each time,
// and not before first paint). Until then the active link paints its own
// ink background, which is also the no-JS / test-DOM rendering. With the slab
// up, every label stays ink — the slab's difference blend does the inverting.
const navRef = ref<HTMLElement | null>(null)
const slab = ref<{ top: number; height: number } | null>(null)
const slabAnimated = ref(false)

const placeSlab = async () => {
  await nextTick()
  if (props.compact) return
  const link = navRef.value?.querySelector<HTMLElement>('[data-active]')
  slab.value = link && link.offsetHeight ? { top: link.offsetTop, height: link.offsetHeight } : null
}

let resizeObserver: ResizeObserver | null = null
onMounted(() => {
  placeSlab().then(() => requestAnimationFrame(() => (slabAnimated.value = true)))
  if (typeof ResizeObserver !== 'undefined' && navRef.value) {
    resizeObserver = new ResizeObserver(() => placeSlab())
    resizeObserver.observe(navRef.value)
  }
})
onBeforeUnmount(() => resizeObserver?.disconnect())
watch(() => [route.path, props.showAdmin, props.navItems, props.adminItems], placeSlab)

const linkClass = (active: boolean) => [
  props.compact ? 'flex items-baseline gap-3 px-3 py-3 transition-colors' : 'relative z-[1] flex items-baseline gap-3 px-3 py-2.5 transition-colors duration-300',
  active
    ? slab.value ? 'text-[color:var(--ink)]' : 'bg-ink text-[color:var(--paper)]'
    : props.compact ? 'text-[color:var(--ink)]' : 'text-[color:var(--ink)] hover:bg-paper-deep',
]

const numberClass = (active: boolean) =>
  props.compact
    ? 'num-tab text-[10px] text-[color:var(--mute)]'
    : ['num-tab text-[10px] flex-shrink-0', active ? 'opacity-70' : 'text-[color:var(--mute)]']
</script>
