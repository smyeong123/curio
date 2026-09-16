<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)] flex items-center justify-center px-6">
    <div class="w-full max-w-[640px] text-center">
      <p class="kicker kicker-signal mb-4">{{ t('common.notFound.kicker') }}</p>
      <p class="display-headline text-[clamp(120px,20vw,240px)] leading-[0.85] mb-2 deco-num">
        4<em class="italic-display">0</em>4
      </p>
      <i18n-t scope="global" keypath="common.notFound.headline" tag="h1" class="display-headline text-[clamp(28px,4vw,42px)] leading-tight mb-4">
        <template #page><em class="italic-display">{{ t('common.notFound.page') }}</em></template>
      </i18n-t>
      <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] mb-10 max-w-md mx-auto leading-relaxed">
        {{ t('common.notFound.body') }}
      </p>
      <div class="flex items-center justify-center gap-3">
        <button class="btn-editorial-ghost" @click="$router.back()">
          <span aria-hidden="true">←</span>
          {{ t('common.actions.back') }}
        </button>
        <router-link to="/" class="btn-editorial">
          {{ t('common.actions.home') }}
          <span aria-hidden="true">→</span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
