<template>
  <!-- The "← Newer · Page N of M · Older →" strip under a paged list. Labels
       and the counter message are props so each view keeps its own wording;
       extra classes fall through to the nav. -->
  <nav class="flex items-center justify-between">
    <button class="btn-editorial-ghost" :disabled="page === 0" @click="emit('change', page - 1)">
      <span aria-hidden="true">←</span>
      {{ newerLabel }}
    </button>
    <i18n-t scope="global" :keypath="counterKeypath" tag="span" class="kicker">
      <template #page><span class="num-tab text-[color:var(--ink)]">{{ page + 1 }}</span></template>
      <template #total><span class="num-tab text-[color:var(--ink)]">{{ totalPages }}</span></template>
    </i18n-t>
    <button class="btn-editorial-ghost" :disabled="page >= totalPages - 1" @click="emit('change', page + 1)">
      {{ olderLabel }}
      <span aria-hidden="true">→</span>
    </button>
  </nav>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  /** Zero-based current page. */
  page: number
  totalPages: number
  newerLabel: string
  olderLabel: string
  /** Catalog message for the counter; it must take `{page}` and `{total}`. */
  counterKeypath?: string
}>(), { counterKeypath: 'admin.common.pagination.pageOf' })

const emit = defineEmits<{ change: [page: number] }>()
</script>
