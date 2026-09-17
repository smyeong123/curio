<template>
  <section class="border border-[color:var(--leaf)] bg-paper-deep p-6">
    <p class="kicker mb-3" style="color: var(--leaf);">{{ t('auth.common.mailSentKicker') }}</p>
    <i18n-t scope="global" keypath="auth.common.mailSentHeadline" tag="p" class="display-headline text-[22px] leading-tight mb-2">
      <template #inbox><em class="italic-display">{{ t('auth.common.inbox') }}</em></template>
    </i18n-t>
    <i18n-t scope="global" :keypath="bodyKeypath" tag="p" class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-6 leading-relaxed">
      <template #email><strong class="font-semibold text-[color:var(--ink)]">{{ email }}</strong></template>
    </i18n-t>
    <router-link to="/login" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]">
      {{ t('auth.common.backToSignIn') }}
    </router-link>
  </section>
</template>

<script setup lang="ts">
// "Mail's away" confirmation shown after a reset link has been emailed.

import { useI18n } from 'vue-i18n'

defineProps<{
  /** Address the mail went to, rendered bold inside the body line. */
  email: string
  /** Catalog key of the body line; its message must carry an `{email}` slot. */
  bodyKeypath: string
}>()

const { t } = useI18n()
</script>
