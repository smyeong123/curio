<template>
  <div class="min-h-screen">
    <!-- Boot splash: the first navigation is gated on the silent session
         refresh (router guard), so until the router resolves there is nothing
         to render. Show the masthead instead of a blank page; the refresh call
         itself is capped at 8 s so this can never hang indefinitely. -->
    <div
      v-if="!routerReady"
      class="fixed inset-0 z-50 flex flex-col items-center justify-center gap-3 bg-paper text-[color:var(--ink)]"
      :aria-label="t('common.app.loading')"
    >
      <span class="display-headline text-[42px] leading-none">Curio</span>
      <span class="kicker">{{ t('common.app.opening') }}</span>
    </div>
    <ErrorBoundary>
      <router-view v-slot="{ Component, route }">
        <transition name="page" mode="out-in" @after-enter="focusPageHeading">
          <!-- Key by the top-level matched record so the dashboard shell stays
               mounted across sub-navigation; its inner router-view transitions
               the content. Other top-level pages still cross-fade normally. -->
          <component :is="Component" :key="route.matched[0]?.path ?? route.path" />
        </transition>
      </router-view>
    </ErrorBoundary>
    <ToastContainer />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ToastContainer from '@/components/ui/ToastContainer.vue'
import ErrorBoundary from '@/components/ErrorBoundary.vue'
import { focusPageHeading } from '@/composables/useFocusOnEnter'
// Session initialization is handled in the router guard (router/index.ts)

const { t } = useI18n()
const routerReady = ref(false)
void useRouter().isReady().then(() => { routerReady.value = true })
</script>
