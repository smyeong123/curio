<template>
  <!-- The editorial page header every dashboard/admin view opens with:
       signal kicker, display h1 with one emphasised word, double rule. The h1
       must stay an h1 — focusPageHeading() moves focus to it after navigation. -->
  <header class="mb-10">
    <div class="flex items-end justify-between flex-wrap gap-4 mb-6">
      <div>
        <p class="kicker kicker-signal mb-3">{{ kicker }}</p>
        <i18n-t scope="global" :keypath="keypath" tag="h1" :class="headlineClass">
          <template #[emphasis]><em class="italic-display"><slot name="emphasis">{{ emphasisText }}</slot></em></template>
        </i18n-t>
      </div>
      <slot v-if="$slots.aside" name="aside" />
    </div>
    <div class="rule-double w-full"></div>
    <slot />
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  /** Small signal-coloured line above the headline. */
  kicker: string
  /** i18n keypath of the headline; must contain exactly one `{emphasis}` slot. */
  keypath: string
  /** Name of the named slot in the headline message, e.g. `settings` for "Your {settings}." */
  emphasis: string
  /** Text rendered inside the emphasised <em> (when the `emphasis` slot is not used). */
  emphasisText?: string
  /** `compact` for detail pages that don't need the full display size. */
  size?: 'display' | 'compact'
}>(), { emphasisText: '', size: 'display' })

const headlineClass = computed(() =>
  props.size === 'compact'
    ? 'display-headline text-[clamp(36px,5vw,64px)] leading-[0.95]'
    : 'display-headline text-[clamp(48px,7vw,96px)] leading-[0.95]'
)
</script>
