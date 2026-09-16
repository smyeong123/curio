<template>
  <form @submit.prevent="handleSubmit" class="space-y-7">
    <BaseInput
      v-model="email"
      label="Email"
      type="email"
      placeholder="you@example.com"
      :error="errors.email"
      id="login-email"
    />
    <BaseInput
      v-model="password"
      label="Password"
      type="password"
      placeholder="Your password"
      :error="errors.password"
      id="login-password"
    />
    <BaseButton type="submit" :loading="loading" class="w-full justify-center py-4" size="lg">
      <template #trailing>
        <span aria-hidden="true">→</span>
      </template>
      Sign in
    </BaseButton>
    <div class="relative py-1">
      <div class="absolute inset-0 flex items-center">
        <span class="w-full border-t border-[color:var(--rule)]" />
      </div>
      <div class="relative flex justify-center">
        <span class="bg-paper px-3 kicker">or</span>
      </div>
    </div>
    <button
      type="button"
      class="w-full inline-flex items-center justify-center gap-3 border border-[color:var(--rule)] bg-transparent px-4 py-4 font-mono-curio text-[12px] uppercase tracking-[0.14em] text-[color:var(--ink)] hover:bg-[color:var(--ink)] hover:text-[color:var(--paper)] transition-colors"
      @click="$emit('google')"
    >
      <svg class="h-4 w-4" viewBox="0 0 24 24" aria-hidden="true">
        <path fill="#EA4335" d="M12 10.3v3.9h5.5c-.2 1.2-.9 2.2-1.9 3l3.1 2.4c1.8-1.6 2.8-4 2.8-6.8 0-.7-.1-1.3-.2-1.9H12Z" />
        <path fill="#34A853" d="M12 22c2.6 0 4.8-.9 6.4-2.3l-3.1-2.4c-.9.6-2 .9-3.3.9-2.5 0-4.7-1.7-5.5-4l-3.2 2.5A10 10 0 0 0 12 22Z" />
        <path fill="#4A90E2" d="M6.5 14.2a6 6 0 0 1 0-3.8L3.3 8A10 10 0 0 0 2 12c0 1.5.3 2.9.9 4.2l3.6-2Z" />
        <path fill="#FBBC05" d="M12 5.8c1.4 0 2.6.5 3.6 1.4l2.7-2.7C16.7 3 14.5 2 12 2a10 10 0 0 0-8.7 5l3.2 2.5c.8-2.4 3-4 5.5-4Z" />
      </svg>
      Continue with Google
    </button>
    <p
      v-if="serverError"
      class="flex items-center justify-center gap-2 text-center text-[12px] text-red-600 font-mono-curio uppercase tracking-[0.14em] border border-red-200 bg-red-50/60 px-3 py-3"
    >
      <AppIcon name="warning" class="h-3.5 w-3.5 flex-shrink-0" />
      {{ serverError }}
    </p>
  </form>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const emit = defineEmits<{
  submit: [email: string, password: string]
  google: []
}>()

defineProps<{
  loading?: boolean
  serverError?: string
}>()

const email = ref('')
const password = ref('')
const errors = reactive({ email: '', password: '' })

const handleSubmit = () => {
  errors.email = ''
  errors.password = ''

  if (!email.value) errors.email = 'Email is required'
  if (!password.value) errors.password = 'Password is required'

  if (errors.email || errors.password) return

  emit('submit', email.value, password.value)
}
</script>
