<template>
  <!-- One archive issue: masthead, the stories left after filtering, end mark. -->
  <article class="digest-card" :lang="digest.content?.language ?? 'en'">
    <header class="border-t-2 border-[color:var(--rule)] pt-5 mb-7">
      <div class="flex items-end justify-between flex-wrap gap-3 mb-4">
        <div>
          <p class="kicker">{{ t('archive.issue.label', { number: issueNumber }) }} · {{ formatDate(digest.generatedAt, 'full') }}</p>
        </div>
        <div class="flex items-center gap-3">
          <span
            v-if="digest.emailSentAt"
            class="kicker"
            style="color: var(--leaf);"
          >
            ● {{ t('archive.issue.delivered', { time: formatTime(digest.emailSentAt) }) }}
          </span>
          <router-link
            :to="{ name: 'quiz', params: { digestId: digest.id } }"
            class="btn-editorial-ghost py-2 px-3"
            style="font-size: 0.625rem;"
          >
            {{ t('archive.issue.takeQuiz') }}
            <span aria-hidden="true">→</span>
          </router-link>
        </div>
      </div>
      <div
        v-if="topics.length > 0"
        class="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] font-body-curio text-[color:var(--mute)]"
      >
        <span class="kicker">{{ t('archive.issue.beats') }}</span>
        <span
          v-for="(topic, i) in topics"
          :key="topic"
          class="flex items-center gap-3"
        >
          {{ topicLabel(topic) }}
          <span v-if="i < topics.length - 1" class="text-[color:var(--rule)]">·</span>
        </span>
      </div>
    </header>

    <!-- Stories, renumbered from 01 so a filtered view reads as its own edition -->
    <ol class="space-y-10">
      <li
        v-for="(summary, i) in stories"
        :key="i"
        class="grid grid-cols-12 gap-x-6 gap-y-2"
      >
        <!-- Story number gutter -->
        <div class="col-span-12 sm:col-span-1">
          <span class="deco-num text-[28px] leading-none text-[color:var(--mute)]">{{ String(i + 1).padStart(2, '0') }}</span>
        </div>

        <!-- Body -->
        <div class="col-span-12 sm:col-span-11">
          <p class="kicker mb-2">{{ summary.topic ? topicLabel(summary.topic) : t('archive.story.fallbackKicker') }}</p>
          <h3 class="display-headline text-[clamp(22px,2.4vw,32px)] leading-[1.05] mb-3">
            {{ summary.headline }}
          </h3>
          <p
            :class="[
              'font-body-curio text-[15.5px] leading-[1.65] text-[color:var(--ink)] mb-4',
              dropcap && i === 0 ? 'dropcap' : ''
            ]"
          >
            {{ summary.summary }}
          </p>
          <p
            v-if="summary.why_it_matters"
            class="border-l-2 border-[color:var(--signal)] pl-4 font-display italic text-[15px] text-[color:var(--ink-soft)] leading-snug mb-4"
          >
            <span class="kicker kicker-signal not-italic mr-2">{{ t('archive.story.whyItMatters') }}</span>
            {{ summary.why_it_matters }}
          </p>
          <a
            v-if="safeExternalUrl(summary.source_url)"
            :href="safeExternalUrl(summary.source_url) ?? undefined"
            target="_blank"
            rel="noopener noreferrer"
            class="ink-link font-mono-curio text-[11px] uppercase tracking-[0.14em]"
          >
            {{ t('archive.story.source', { name: summary.source_name || t('archive.story.linkFallback') }) }} →
          </a>
        </div>
      </li>
    </ol>

    <!-- Issue end mark -->
    <div class="mt-10 flex items-center justify-center">
      <span class="kicker">{{ t('archive.issue.end') }}</span>
    </div>
  </article>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useFormat } from '@/composables/useFormat'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { safeExternalUrl } from '@/utils/safeUrl'
import type { Digest, NewsSummary } from '@/types/news'

defineProps<{
  digest: Digest
  /** The digest's stories after the beat filter and search narrowing. */
  stories: NewsSummary[]
  /** Canonical topic ids the issue was generated for. */
  topics: string[]
  /** Stable three-character issue number derived from the digest id. */
  issueNumber: string
  /** Open the first story with a drop cap (the page's lead issue). */
  dropcap: boolean
}>()

const { t } = useI18n()
const { formatDate, formatTime } = useFormat()
// Topic names are canonical ids (backend-stored); label them per edition at render time.
const { topicLabel } = useTopicLabels()
</script>

<style scoped>
.digest-card {
  content-visibility: auto;
  contain-intrinsic-size: 1px 800px;
}
</style>
