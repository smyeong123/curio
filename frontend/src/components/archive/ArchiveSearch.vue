<template>
  <!-- Server-side full-text search over the whole retained archive (30 days),
       wider than the 7-day browse window below it. -->
  <div class="pb-5 border-b border-[color:var(--rule)]">
    <label for="archive-search" class="kicker">{{ t('archive.search.label') }}</label>
    <div class="mt-3 flex items-center gap-2">
      <input
        id="archive-search"
        v-model="query"
        type="search"
        :placeholder="t('archive.search.placeholder')"
        autocomplete="off"
        class="w-full max-w-md bg-transparent border border-[color:var(--rule)] px-3 py-2 font-body-curio text-[14px] text-[color:var(--ink)] placeholder:text-[color:var(--mute)] focus:border-[color:var(--ink)] focus:outline-none"
      />
      <button v-if="query" class="btn-editorial-ghost py-2 px-3" @click="emit('clear')">
        {{ t('archive.search.clear') }}
      </button>
    </div>
    <p class="kicker mt-2 text-[color:var(--mute)]" aria-live="polite">
      <template v-if="loading">{{ t('archive.search.searching') }}</template>
      <template v-else-if="active">
        {{ t('archive.search.matchCount', matchCount) }} · {{ t('archive.search.fullArchive') }}
      </template>
      <template v-else>{{ t('archive.search.hint') }}</template>
    </p>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const query = defineModel<string>('query', { required: true })

defineProps<{
  /** A search request is in flight (debounced keystrokes included). */
  loading: boolean
  /** The query is long enough to search; the status line then reports matches. */
  active: boolean
  /** Stories the active search renders after beat filtering and narrowing. */
  matchCount: number
}>()

const emit = defineEmits<{ clear: [] }>()

const { t } = useI18n()
</script>
