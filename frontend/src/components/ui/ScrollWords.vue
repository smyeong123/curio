<template>
  <component :is="tag" ref="root" class="scroll-words" :style="{ '--sw-p': filled }">
    <!-- Words fill in from faint to full ink as the paragraph scrolls through
         the viewport. Screen readers get the plain sentence once; the per-word
         spans are presentation only. (Comment lives inside the root so the
         component stays single-root and class/attrs fall through.) -->
    <span class="sr-only">{{ text }}</span>
    <span aria-hidden="true">
      <template v-for="(word, i) in words" :key="i">
        <span class="scroll-word" :style="{ '--sw-i': i }">{{ word }}</span>{{ i < words.length - 1 ? ' ' : '' }}
      </template>
    </span>
  </component>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ text: string; tag?: string }>(), { tag: 'p' })

const root = ref<HTMLElement | null>(null)
const words = computed(() => props.text.split(/\s+/).filter(Boolean))
// How many words are filled, as a float: word i is fully inked once this
// passes i + 1. Starts "all filled" so SSR/no-JS/reduced-motion read normally.
const filled = ref(Number.MAX_SAFE_INTEGER)

let frame = 0
let active = false // false under reduced motion: the words stay fully inked
const update = () => {
  frame = 0
  const el = root.value
  if (!el) return
  const vh = window.innerHeight
  const r = el.getBoundingClientRect()
  // 0 when the top edge enters the lower 10% of the viewport, 1 once the
  // paragraph's middle has risen to 40% of the viewport height.
  const start = vh * 0.9
  const end = vh * 0.4 - r.height / 2
  const p = Math.min(1, Math.max(0, (start - r.top) / (start - end)))
  filled.value = p * (words.value.length + 1)
}
const onScroll = () => {
  if (!frame) frame = requestAnimationFrame(update)
}

onMounted(() => {
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
  active = true
  update()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll, { passive: true })
})

// A locale switch swaps the sentence (and its word count) without any scroll,
// so re-measure once the new words have rendered.
watch(() => props.text, () => {
  if (active) nextTick(update)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onScroll)
  if (frame) cancelAnimationFrame(frame)
})
</script>
