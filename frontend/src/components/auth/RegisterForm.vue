<template>
  <form @submit.prevent="handleSubmit" class="space-y-7">
    <BaseInput
      v-model="fullName"
      :label="t('auth.form.fullNameLabel')"
      :placeholder="t('auth.register.fullNamePlaceholder')"
      :error="errors.fullName"
      id="register-name"
    />
    <BaseInput
      v-model="email"
      :label="t('auth.form.emailLabel')"
      type="email"
      :placeholder="t('auth.form.emailPlaceholder')"
      :error="errors.email"
      id="register-email"
    />
    <BaseInput
      v-model="password"
      :label="t('auth.form.passwordLabel')"
      type="password"
      :placeholder="t('auth.form.passwordMinPlaceholder')"
      :error="errors.password"
      id="register-password"
    />
    <BaseInput
      v-model="confirmPassword"
      :label="t('auth.form.confirmPasswordLabel')"
      type="password"
      :placeholder="t('auth.form.confirmPasswordPlaceholder')"
      :error="errors.confirmPassword"
      id="register-confirm"
    />
    <BaseButton type="submit" :loading="loading" class="w-full justify-center py-4" size="lg">
      <template #trailing>
        <span aria-hidden="true">→</span>
      </template>
      {{ t('auth.register.submit') }}
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
import { useI18n } from 'vue-i18n'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const { t } = useI18n()

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

  if (!fullName.value) errors.fullName = t('auth.form.errors.fullNameRequired')
  if (!email.value) errors.email = t('auth.form.errors.emailRequired')
  if (!password.value) errors.password = t('auth.form.errors.passwordRequired')
  else if (password.value.length < 8) errors.password = t('auth.form.errors.passwordTooShort')
  if (password.value !== confirmPassword.value) errors.confirmPassword = t('auth.form.errors.passwordMismatch')

  if (errors.fullName || errors.email || errors.password || errors.confirmPassword) return

  emit('submit', {
    email: email.value,
    password: password.value,
    fullName: fullName.value
  })
}
</script>
