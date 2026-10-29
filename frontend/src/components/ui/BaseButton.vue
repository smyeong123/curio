<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="[
      'inline-flex items-center justify-center gap-2 font-body-curio font-semibold tracking-[-0.01em]',
      sizeClasses,
      variantClasses,
      disabled || loading ? 'cursor-not-allowed opacity-55' : ''
    ]"
  >
    <svg v-if="loading" class="h-4 w-4 flex-shrink-0 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4Z" />
    </svg>
    <span v-if="$slots.leading && !loading" class="inline-flex items-center">
      <slot name="leading" />
    </span>
    <span class="truncate">
      <slot />
    </span>
    <span v-if="$slots.trailing && !loading" class="inline-flex items-center">
      <slot name="trailing" />
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  type?: 'button' | 'submit' | 'reset'
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  disabled?: boolean
  loading?: boolean
}>(), {
  type: 'button',
  variant: 'primary',
  size: 'md',
  disabled: false,
  loading: false
})

// Liquid Glass capsules (main.css); the focus ring is the global orange spark.
const sizeClasses = computed(() => ({
  sm: 'min-h-[40px] px-4 py-2 text-xs',
  md: 'min-h-[44px] px-5 py-2.5 text-sm',
  lg: 'min-h-[52px] px-6 py-3 text-[15px]'
}[props.size]))

const variantClasses = computed(() => ({
  primary: 'btn-glass',
  secondary: 'btn-glass btn-glass-ink',
  outline: 'btn-glass-clear',
  ghost: 'rounded-full text-[color:var(--mute)] transition-colors duration-200 hover:bg-ink/6 hover:text-[color:var(--ink)]',
  danger: 'btn-glass btn-glass-danger'
}[props.variant]))
</script>
