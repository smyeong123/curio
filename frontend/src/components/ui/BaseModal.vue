<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="show" class="fixed inset-0 z-50 overflow-y-auto">
        <div class="flex min-h-full items-center justify-center p-4">
          <div class="fixed inset-0 bg-[color:var(--ink)]/60 backdrop-blur-sm transition-opacity" aria-hidden="true" @click="$emit('close')" />
          <div ref="dialogRef" role="dialog" aria-modal="true" :aria-label="title" class="relative w-full max-w-md transform rounded-2xl bg-[color:var(--paper)] p-6 shadow-2xl transition-all border border-[color:var(--rule)]">
            <div class="flex items-start justify-between mb-5" v-if="title">
              <h3 class="text-base font-semibold text-[color:var(--ink)]">{{ title }}</h3>
              <button
                :aria-label="t('common.a11y.closeDialog')"
                class="ml-4 text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors rounded-lg p-0.5 hover:bg-paper-deep"
                @click="$emit('close')"
              >
                <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <slot />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFocusTrap } from '@/composables/useFocusTrap'

const { t } = useI18n()

const props = defineProps<{
  show: boolean
  title?: string
}>()

const emit = defineEmits<{
  close: []
}>()

// Dialog a11y: trap Tab within the modal, close on Escape, focus the first
// control on open, restore focus to the trigger on close, and lock body scroll.
const dialogRef = ref<HTMLElement | null>(null)
useFocusTrap(dialogRef, toRef(props, 'show'), { onEscape: () => emit('close') })
</script>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active .relative,
.modal-leave-active .relative {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-from .relative {
  transform: scale(0.95) translateY(-8px);
  opacity: 0;
}
.modal-leave-to .relative {
  transform: scale(0.95);
  opacity: 0;
}
</style>
