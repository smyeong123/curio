<template>
  <fieldset>
    <legend :id="legendId" class="sr-only">{{ label }}</legend>
    <div
      class="grid gap-0 border border-[color:var(--rule)]"
      :style="{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }"
      role="radiogroup"
      :aria-labelledby="legendId"
    >
      <button
        v-for="(option, i) in options"
        :key="option.value"
        type="button"
        role="radio"
        :lang="option.lang"
        :aria-checked="model === option.value"
        :class="[
          'px-3 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] transition-colors',
          i < options.length - 1 ? 'border-r border-[color:var(--rule)]' : '',
          model === option.value
            ? 'bg-ink text-[color:var(--paper)]'
            : 'bg-paper text-[color:var(--ink)] hover:bg-paper-deep'
        ]"
        @click="model = option.value"
      >
        {{ option.label }}
      </button>
    </div>
  </fieldset>
</template>

<script setup lang="ts" generic="T extends string">
// A one-of-N choice rendered as a row of equal cells (Settings → Lights). Exposed
// to assistive tech as a radiogroup named by the screen-reader-only legend;
// the visible section heading sits outside.

import { useId } from 'vue'

defineProps<{
  options: Array<{ value: T; label: string; lang?: string }>
  /** Accessible name of the control: the fieldset legend, screen-reader only. */
  label: string
}>()

const model = defineModel<T>({ required: true })

const legendId = useId()
</script>
