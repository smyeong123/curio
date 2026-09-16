<template>
  <div class="fixed top-5 right-5 z-[100] flex flex-col gap-2 pointer-events-none">
    <!-- Assertive region: errors interrupt the screen reader immediately. -->
    <div aria-live="assertive" role="alert" class="contents">
      <TransitionGroup name="toast">
        <div
          v-for="toast in assertiveToasts"
          :key="toast.id"
          :class="[
            'flex items-start gap-3 rounded-xl px-4 py-3 text-sm shadow-lg min-w-[300px] max-w-[380px] pointer-events-auto border',
            'bg-[color:var(--paper)] text-[color:var(--ink)] border-[color:var(--signal-deep)]/40'
          ]"
        >
          <div class="w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5 bg-[color:var(--signal)]/15 text-[color:var(--signal-deep)]">
            <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
            </svg>
          </div>
          <span class="flex-1 font-medium">{{ toast.message }}</span>
          <button aria-label="Dismiss notification" class="text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors flex-shrink-0" @click="removeToast(toast.id)">
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </TransitionGroup>
    </div>
    <!-- Polite region: success/info wait for a pause. -->
    <div aria-live="polite" role="status" class="contents">
    <TransitionGroup name="toast">
      <div
        v-for="toast in politeToasts"
        :key="toast.id"
        :class="[
          'flex items-start gap-3 rounded-xl px-4 py-3 text-sm shadow-lg min-w-[300px] max-w-[380px] pointer-events-auto border',
          'bg-[color:var(--paper)] text-[color:var(--ink)]',
          toast.type === 'success' && 'border-[color:var(--leaf)]/40',
          toast.type === 'error' && 'border-[color:var(--signal-deep)]/40',
          toast.type === 'info' && 'border-[color:var(--rule)]'
        ]"
      >
        <div :class="[
          'w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5',
          toast.type === 'success' && 'bg-[color:var(--leaf)]/15 text-[color:var(--leaf)]',
          toast.type === 'error' && 'bg-[color:var(--signal)]/15 text-[color:var(--signal-deep)]',
          toast.type === 'info' && 'bg-paper-deep text-[color:var(--mute)]'
        ]">
          <svg v-if="toast.type === 'success'" class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
          </svg>
          <svg v-else-if="toast.type === 'error'" class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
          </svg>
          <svg v-else class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
          </svg>
        </div>
        <span class="flex-1 font-medium">{{ toast.message }}</span>
        <button aria-label="Dismiss notification" class="text-slate-400 hover:text-slate-600 transition-colors flex-shrink-0" @click="removeToast(toast.id)">
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </TransitionGroup>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useToast } from '@/composables/useToast'

const { toasts, removeToast } = useToast()

// Errors go in the assertive region; success/info in the polite region.
const assertiveToasts = computed(() => toasts.value.filter((t) => t.type === 'error'))
const politeToasts = computed(() => toasts.value.filter((t) => t.type !== 'error'))
</script>

<style scoped>
.toast-enter-active { transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.toast-leave-active { transition: all 0.2s ease; }
.toast-enter-from { opacity: 0; transform: translateX(20px) scale(0.95); }
.toast-leave-to { opacity: 0; transform: translateX(20px); }
</style>
