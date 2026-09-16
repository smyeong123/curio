<template>
  <div v-if="error" class="flex min-h-screen items-center justify-center bg-paper px-4">
    <div class="w-full max-w-md text-center">
      <div class="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-red-50 text-red-400">
        <AppIcon name="warning" class="h-7 w-7" />
      </div>
      <h1 class="mb-2 text-2xl font-bold text-[color:var(--ink)]">{{ t('common.errors.boundaryTitle') }}</h1>
      <p class="mb-6 text-sm text-[color:var(--mute)]">
        {{ t('common.errors.boundaryBody') }}
      </p>
      <div class="flex items-center justify-center gap-3">
        <BaseButton variant="outline" @click="handleRetry">
          {{ t('common.actions.retry') }}
        </BaseButton>
        <router-link to="/">
          <BaseButton>
            <template #leading>
              <AppIcon name="home" class="h-4 w-4" />
            </template>
            {{ t('common.actions.home') }}
          </BaseButton>
        </router-link>
      </div>
      <!-- Raw error internals are a debugging aid only — never shown in production -->
      <details v-if="isDev && errorMessage" class="mt-6 text-left">
        <summary class="cursor-pointer text-xs text-[color:var(--mute)] hover:text-[color:var(--ink)]">{{ t('common.errors.details') }}</summary>
        <pre class="mt-2 overflow-auto rounded-lg bg-slate-800 p-3 text-xs text-slate-300">{{ errorMessage }}</pre>
      </details>
    </div>
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
import { ref, watch, onErrorCaptured } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BaseButton from '@/components/ui/BaseButton.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const { t } = useI18n()
const error = ref(false)
const errorMessage = ref('')
const isDev = import.meta.env.DEV

const route = useRoute()

onErrorCaptured((err: Error) => {
  error.value = true
  errorMessage.value = err.message || String(err)
  return false
})

// Reset the boundary on any navigation so a single view error doesn't strand
// the user on the error screen (e.g. after clicking "Go home").
watch(
  () => route.fullPath,
  () => {
    error.value = false
    errorMessage.value = ''
  }
)

const handleRetry = () => {
  error.value = false
  errorMessage.value = ''
}
</script>
