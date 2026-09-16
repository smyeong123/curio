<template>
  <form @submit.prevent="handleSubmit" class="space-y-7">
    <BaseInput
      v-model="fullName"
      label="Full name"
      placeholder="Jane Reader"
      :error="errors.fullName"
      id="register-name"
    />
    <BaseInput
      v-model="email"
      label="Email"
      type="email"
      placeholder="you@example.com"
      :error="errors.email"
      id="register-email"
    />
    <BaseInput
      v-model="password"
      label="Password"
      type="password"
      placeholder="At least 8 characters"
      :error="errors.password"
      id="register-password"
    />
    <BaseInput
      v-model="confirmPassword"
      label="Confirm password"
      type="password"
      placeholder="Type it again"
      :error="errors.confirmPassword"
      id="register-confirm"
    />
    <BaseButton type="submit" :loading="loading" class="w-full justify-center py-4" size="lg">
      <template #trailing>
        <span aria-hidden="true">→</span>
      </template>
      Create account
    </BaseButton>
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
  submit: [data: { email: string; password: string; fullName: string }]
}>()

defineProps<{
  loading?: boolean
  serverError?: string
}>()

const fullName = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const errors = reactive({ fullName: '', email: '', password: '', confirmPassword: '' })

const handleSubmit = () => {
  errors.fullName = ''
  errors.email = ''
  errors.password = ''
  errors.confirmPassword = ''

  if (!fullName.value) errors.fullName = 'Full name is required'
  if (!email.value) errors.email = 'Email is required'
  if (!password.value) errors.password = 'Password is required'
  else if (password.value.length < 8) errors.password = 'Password must be at least 8 characters'
  if (password.value !== confirmPassword.value) errors.confirmPassword = 'Passwords do not match'

  if (errors.fullName || errors.email || errors.password || errors.confirmPassword) return

  emit('submit', {
    email: email.value,
    password: password.value,
    fullName: fullName.value
  })
}
</script>
