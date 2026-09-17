<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)]">
    <!-- ── Sidebar (desktop) ──────────────────────────────────────── -->
    <aside class="fixed inset-y-0 left-0 z-40 hidden w-[260px] flex-col border-r border-[color:var(--rule)] bg-paper md:flex">
      <!-- Masthead -->
      <div class="px-6 pt-7 pb-5 border-b border-[color:var(--rule)]">
        <router-link to="/dashboard/archive" class="block">
          <div class="flex items-baseline justify-between">
            <span class="display-headline text-[34px] leading-none">Curio</span>
            <span class="kicker num-tab">{{ t('layout.masthead.vol') }}</span>
          </div>
          <p class="kicker mt-2.5">{{ t('layout.masthead.tagline') }}</p>
        </router-link>
      </div>

      <!-- Issue date -->
      <div class="px-6 py-4 border-b border-[color:var(--rule)]">
        <p class="kicker mb-1">{{ t('layout.today') }}</p>
        <p class="font-display text-[15px] leading-tight">{{ todayLong }}</p>
      </div>

      <DashboardMenu
        :nav-items="navItems"
        :admin-items="adminNavItems"
        :show-admin="isAdmin"
      />
      <DashboardMenuFoot :is-dark="isDark" @toggle-theme="toggleTheme" @sign-out="handleLogout" />
    </aside>

    <!-- ── Mobile masthead ────────────────────────────────────────── -->
    <header class="sticky top-0 z-30 bg-paper/95 backdrop-blur border-b border-[color:var(--rule)] px-5 py-3 md:hidden">
      <div class="flex items-center justify-between">
        <router-link to="/dashboard/archive" class="flex items-baseline gap-2">
          <span class="display-headline text-[28px] leading-none">Curio</span>
          <span class="kicker">{{ t('layout.masthead.vol') }}</span>
        </router-link>
        <button
          class="inline-flex h-11 w-11 items-center justify-center border border-[color:var(--rule)] bg-paper text-[color:var(--ink)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--signal)]"
          :aria-label="t('layout.menu.open')"
          @click="isMenuOpen = true"
        >
          <span class="font-mono-curio text-[18px]">≡</span>
        </button>
      </div>
    </header>

    <!-- ── Mobile drawer ──────────────────────────────────────────── -->
    <Transition name="drawer-fade">
      <div v-if="isMenuOpen" class="fixed inset-0 z-50 md:hidden">
        <button
          class="absolute inset-0 bg-[color:var(--ink)]/60"
          :aria-label="t('layout.menu.close')"
          @click="closeMenu"
        />
        <aside
          ref="drawerRef"
          role="dialog"
          aria-modal="true"
          :aria-label="t('layout.menu.main')"
          class="absolute inset-y-0 left-0 flex w-[280px] flex-col border-r border-[color:var(--rule)] bg-paper"
        >
          <div class="flex items-center justify-between border-b border-[color:var(--rule)] px-5 py-4">
            <span class="display-headline text-[26px] leading-none">Curio</span>
            <button
              class="inline-flex h-11 w-11 items-center justify-center border border-[color:var(--rule)] text-[color:var(--ink)]"
              :aria-label="t('layout.menu.close')"
              @click="closeMenu"
            >
              <span class="font-mono-curio text-[16px]">×</span>
            </button>
          </div>

          <DashboardMenu
            compact
            :nav-items="navItems"
            :admin-items="adminNavItems"
            :show-admin="isAdmin"
            @navigate="closeMenu"
          />
          <DashboardMenuFoot :is-dark="isDark" @toggle-theme="toggleTheme" @sign-out="handleLogout" />
        </aside>
      </div>
    </Transition>

    <!-- ── Main ───────────────────────────────────────────────────── -->
    <!-- Only the content area transitions; the sidebar stays mounted. -->
    <main class="min-h-screen md:ml-[260px]">
      <!-- ── Paid-plans notice (dismissible) ─────────────────────── -->
      <div
        v-if="showPlanNotice"
        role="region"
        :aria-label="t('layout.notice.label')"
        class="flex items-start justify-between gap-4 border-b border-[color:var(--rule)] bg-[color:var(--signal)]/10 px-5 py-3"
      >
        <p class="font-body-curio text-[13.5px] leading-relaxed text-[color:var(--ink-soft)]">
          <strong class="text-[color:var(--ink)]">{{ t('layout.notice.lead') }}</strong>{{ ' ' }}
          <i18n-t scope="global" keypath="layout.notice.body" tag="span">
            <template #contact>
              <a href="mailto:sangmyeonglee123@gmail.com" class="underline underline-offset-2 hover:text-[color:var(--ink)]">{{ t('layout.notice.contactName') }}</a>
            </template>
          </i18n-t>
        </p>
        <button
          type="button"
          class="shrink-0 p-1 text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors"
          :aria-label="t('layout.notice.dismiss')"
          @click="dismissPlanNotice"
        >
          ✕
        </button>
      </div>

      <router-view v-slot="{ Component, route: contentRoute }">
        <transition name="page" mode="out-in" @after-enter="focusPageHeading">
          <component :is="Component" :key="contentRoute.path" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { useLocale } from '@/composables/useLocale'
import { useFormat } from '@/composables/useFormat'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { focusPageHeading } from '@/composables/useFocusOnEnter'
import { useEnsureTimezone } from '@/composables/useEnsureTimezone'
import DashboardMenu from '@/components/layout/DashboardMenu.vue'
import DashboardMenuFoot from '@/components/layout/DashboardMenuFoot.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const { locale, intlLocale } = useLocale()
const { formatDate } = useFormat()

// One-time announcement: free testing period ends July 2026, paid plans Aug–Sep.
const PLAN_NOTICE_KEY = 'curio.notice.paidPlans2026'
const canUseStorage = typeof localStorage !== 'undefined'
const showPlanNotice = ref(canUseStorage && localStorage.getItem(PLAN_NOTICE_KEY) !== '1')
function dismissPlanNotice() {
  showPlanNotice.value = false
  if (canUseStorage) localStorage.setItem(PLAN_NOTICE_KEY, '1')
}

// Once per app entry, make sure the user's delivery timezone reflects their actual
// location so their digest lands at 08:00 local (not 08:00 UTC). No-op if already set.
const { ensure: ensureTimezone } = useEnsureTimezone()
onMounted(ensureTimezone)

const isMenuOpen = ref(false)

const todayLong = computed(() => {
  const d = new Date()
  // Korean reads naturally as one Intl-formatted string ("2026년 9월 16일 수요일");
  // the English masthead keeps its hand-set "Wednesday, September 16 · 2026".
  if (locale.value === 'ko') return formatDate(d, 'full')
  const w = d.toLocaleDateString(intlLocale.value, { weekday: 'long' })
  const m = d.toLocaleDateString(intlLocale.value, { month: 'long' })
  const day = d.getDate()
  const year = d.getFullYear()
  return `${w}, ${m} ${day} · ${year}`
})

const isAdmin = computed(() => authStore.user?.isAdmin === true)

const navItems = computed(() => [
  { label: t('layout.nav.today'), to: '/dashboard/archive', match: ['/dashboard/archive', '/dashboard/quiz/'] },
  { label: t('layout.nav.quizHistory'), to: '/dashboard/quiz-history', match: ['/dashboard/quiz-history'] },
  { label: t('layout.nav.studio'), to: '/dashboard/studio', match: ['/dashboard/studio'] },
  { label: t('layout.nav.settings'), to: '/dashboard/settings', match: ['/dashboard/settings'] },
])

const adminNavItems = computed(() => [
  { label: t('layout.adminNav.dashboard'), to: '/admin/dashboard', match: ['/admin/dashboard'] },
  { label: t('layout.adminNav.digests'), to: '/admin/digests', match: ['/admin/digests'] },
  { label: t('layout.adminNav.users'), to: '/admin/users', match: ['/admin/users'] },
  { label: t('layout.adminNav.stats'), to: '/admin/stats', match: ['/admin/stats'] },
  { label: t('layout.adminNav.audit'), to: '/admin/audit', match: ['/admin/audit'] },
])

const closeMenu = () => {
  isMenuOpen.value = false
}

// Set when the drawer closes because of a sign-out: the page is being left, so
// the focus trap must not hand focus back to a trigger that is about to unmount.
let skipFocusRestore = false

const handleLogout = async () => {
  skipFocusRestore = true
  closeMenu()
  await authStore.logout()
  router.push({ name: 'home' })
}

watch(() => route.path, closeMenu)

// The menu's Lights switch shares useTheme with the Settings selector, so the
// two stay in sync and a saved 'system' preference survives a toggle.
const { preference, setTheme } = useTheme()
const isDark = computed(
  () =>
    preference.value === 'dark' ||
    (preference.value === 'system' &&
      window.matchMedia('(prefers-color-scheme: dark)').matches)
)
const toggleTheme = () => setTheme(isDark.value ? 'light' : 'dark')

// ── Mobile drawer a11y: Escape, focus trap, scroll lock (shared composable) ──
const drawerRef = ref<HTMLElement | null>(null)
useFocusTrap(drawerRef, isMenuOpen, {
  onEscape: closeMenu,
  restoreFocus: () => {
    const restore = !skipFocusRestore
    skipFocusRestore = false
    return restore
  },
})
</script>

<style scoped>
.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 0.2s ease;
}
.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}
</style>
