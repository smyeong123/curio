<template>
  <!-- Topic filter, collapsed by default so the digest leads the page. The
       header toggle opens the full taxonomy grouped by big topic (L1); each
       big topic is itself a nested disclosure whose subtopics stay hidden
       until clicked. Heights animate via grid-template-rows 0fr→1fr. -->
  <div class="pb-2" role="group" :aria-label="t('archive.filter.a11yRegion')">
    <!-- Outer filter toggle -->
    <button
      type="button"
      class="group/filter w-full flex items-center justify-between gap-3 cursor-pointer select-none"
      :aria-expanded="filterOpen"
      aria-controls="beat-filter-panel"
      @click="filterOpen = !filterOpen"
    >
      <span class="flex items-baseline gap-3 min-w-0">
        <span class="kicker flex-shrink-0">{{ t('archive.filter.label') }}</span>
        <span
          class="btn-glass chip-glass max-w-full overflow-hidden text-ellipsis"
          :class="activeTopic ? '' : 'btn-glass-ink'"
        >{{ activeTopic ? topicLabel(activeTopic) : t('archive.filter.all') }}</span>
        <span v-if="!activeTopic" class="kicker text-[color:var(--mute)] flex-shrink-0 hidden sm:inline">· {{ t('archive.filter.beatCount', { count: totalActive }) }}</span>
      </span>
      <span
        aria-hidden="true"
        class="font-mono-curio text-[13px] leading-none text-[color:var(--mute)] transition-all duration-300 ease-out motion-reduce:transition-none group-hover/filter:text-[color:var(--ink)]"
        :class="filterOpen ? 'rotate-90' : ''"
      >▸</span>
    </button>

    <!-- Collapsible filter panel -->
    <div
      id="beat-filter-panel"
      class="grid transition-[grid-template-rows] duration-300 ease-out motion-reduce:transition-none"
      :class="filterOpen ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'"
    >
      <!-- The clip box reaches past the chips (-mx/px, pb) so their glass
           shadows aren't cut off while open. -->
      <div class="overflow-hidden -mx-3" :inert="!filterOpen">
        <div class="px-3 pt-4 pb-3 space-y-3">
          <div class="flex items-center gap-2">
            <button
              type="button"
              :aria-pressed="!activeTopic"
              :class="['chip-glass', !activeTopic ? 'btn-glass btn-glass-ink' : 'btn-glass-clear']"
              @click="emit('select', '')"
            >
              {{ t('archive.filter.all') }}
            </button>
          </div>

          <div
            v-for="group in groups"
            :key="group.id"
            class="border-t border-[color:var(--rule)] pt-3 first:border-t-0 first:pt-0"
          >
            <!-- Big-topic disclosure header -->
            <button
              type="button"
              class="group/head w-full flex items-center justify-between gap-3 cursor-pointer select-none py-0.5"
              :aria-expanded="isGroupOpen(group.id)"
              :aria-controls="`filter-group-${group.id}`"
              @click="toggleGroup(group.id)"
            >
              <span class="kicker flex items-center gap-1.5 text-[color:var(--ink-soft)] transition-colors group-hover/head:text-[color:var(--ink)]">
                <AppIcon :name="group.icon" class="h-3.5 w-3.5 flex-shrink-0" aria-hidden="true" />
                {{ group.name }}
                <span class="text-[color:var(--mute)] normal-case tracking-normal">· {{ t('archive.filter.activeCount', { count: group.activeCount }) }}</span>
              </span>
              <span
                aria-hidden="true"
                class="font-mono-curio text-[13px] leading-none text-[color:var(--mute)] transition-transform duration-300 ease-out motion-reduce:transition-none"
                :class="isGroupOpen(group.id) ? 'rotate-90' : ''"
              >▸</span>
            </button>

            <!-- Subtopics — hidden until the big topic is clicked -->
            <div
              :id="`filter-group-${group.id}`"
              class="grid transition-[grid-template-rows] duration-300 ease-out motion-reduce:transition-none"
              :class="isGroupOpen(group.id) ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]'"
            >
              <div class="overflow-hidden -mx-3" :inert="!isGroupOpen(group.id)">
                <div class="flex flex-wrap gap-2 px-3 pt-3 pb-3">
                  <button
                    v-for="item in group.topics"
                    :key="item.name"
                    type="button"
                    :disabled="item.count === 0"
                    :aria-pressed="item.count > 0 ? activeTopic === item.name : undefined"
                    :class="['chip-glass gap-1.5', activeTopic === item.name ? 'btn-glass' : 'btn-glass-clear']"
                    @click="item.count > 0 && emit('select', activeTopic === item.name ? '' : item.name)"
                  >
                    {{ topicLabel(item.name) }}
                    <span
                      :class="[
                        'num-tab text-[10px]',
                        activeTopic === item.name ? 'opacity-80' : 'opacity-60'
                      ]"
                    >{{ item.count }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <p class="sr-only" aria-live="polite">
      {{ activeTopic ? t('archive.filter.a11yActive', { topic: topicLabel(activeTopic) }) : t('archive.filter.a11yNone') }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDisclosureSet } from '@/composables/useDisclosureSet'
import { useTopicLabels } from '@/composables/useTopicLabels'
import type { TopicIcon } from '@/data/topics'
import AppIcon from '@/components/ui/AppIcon.vue'

/** One selectable beat and how many visible stories cover it. */
export interface TopicItem {
  name: string
  count: number
}

/** A big topic (L1) with its beats; `activeCount` is shown on the collapsed header. */
export interface TopicGroup {
  id: string
  name: string
  icon: TopicIcon | 'archive'
  topics: TopicItem[]
  activeCount: number
}

defineProps<{
  groups: TopicGroup[]
  /** Canonical topic id of the active beat; empty string means all beats. */
  activeTopic: string
  /** Total selectable beats — the hint on the collapsed filter header. */
  totalActive: number
}>()

const emit = defineEmits<{ select: [topic: string] }>()

const { t } = useI18n()
// Topic names are canonical ids (backend-stored); label them per edition at render time.
const { topicLabel } = useTopicLabels()

const filterOpen = ref(false)

// Big-topic (L1) groups expanded inside the filter; each opens on click.
const { isOpen: isGroupOpen, toggle: toggleGroup } = useDisclosureSet()
</script>
