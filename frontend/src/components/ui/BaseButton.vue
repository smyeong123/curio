<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="[
      'inline-flex items-center justify-center gap-2 font-mono-curio uppercase tracking-[0.14em] transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-[color:var(--paper)]',
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

const sizeClasses = computed(() => ({
  sm: 'px-3.5 py-2 text-xs',
  md: 'px-4 py-2.5 text-sm',
  lg: 'px-5 py-3 text-sm'
}[props.size]))

const variantClasses = computed(() => ({
  primary: 'bg-[color:var(--ink)] text-[color:var(--paper)] hover:bg-[color:var(--signal)] focus:ring-[color:var(--ink)]',
  secondary: 'bg-[color:var(--signal)] text-[color:var(--paper)] hover:bg-[color:var(--signal-deep)] focus:ring-[color:var(--signal)]',
  outline: 'border border-[color:var(--rule)] bg-transparent text-[color:var(--ink)] hover:bg-[color:var(--ink)] hover:text-[color:var(--paper)] focus:ring-[color:var(--ink)]',
  ghost: 'text-[color:var(--mute)] hover:bg-[color:var(--paper-deep)] hover:text-[color:var(--ink)] focus:ring-[color:var(--mute)]',
  danger: 'bg-red-600 text-white hover:bg-red-500 focus:ring-red-300'
}[props.variant]))
</script>
