<template>
  <div>
    <label
      v-if="label"
      :for="id"
      class="kicker mb-2 block"
    >
      {{ label }}
    </label>
    <input
      :id="id"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :class="[
        'block w-full border-b-2 bg-transparent px-2 py-3 font-display text-[18px] text-[color:var(--ink)] placeholder:text-[color:var(--mute)] placeholder:font-body-curio placeholder:text-[15px] transition-all duration-150',
        'focus:outline-none focus:border-[color:var(--signal)]',
        error
          ? 'border-red-300 text-red-900 placeholder-red-300'
          : 'border-[color:var(--rule)] hover:border-[color:var(--ink)]',
        disabled ? 'cursor-not-allowed opacity-50' : ''
      ]"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
    <p v-if="error" class="mt-2 flex items-center gap-1 text-xs text-red-600 font-mono-curio uppercase tracking-[0.12em]">
      <AppIcon name="warning" class="h-3.5 w-3.5 flex-shrink-0" />
      {{ error }}
    </p>
  </div>
</template>

<script setup lang="ts">
import AppIcon from '@/components/ui/AppIcon.vue'

withDefaults(defineProps<{
  modelValue?: string
  label?: string
  type?: string
  placeholder?: string
  error?: string
  disabled?: boolean
  id?: string
}>(), {
  modelValue: '',
  type: 'text',
  disabled: false
})

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>
