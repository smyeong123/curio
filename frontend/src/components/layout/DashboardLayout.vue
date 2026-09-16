<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)]">
    <!-- ── Sidebar (desktop) ──────────────────────────────────────── -->
    <aside class="fixed inset-y-0 left-0 z-40 hidden w-[260px] flex-col border-r border-[color:var(--rule)] bg-paper md:flex">
      <!-- Masthead -->
      <div class="px-6 pt-7 pb-5 border-b border-[color:var(--rule)]">
        <router-link to="/dashboard/archive" class="block">
          <div class="flex items-baseline justify-between">
            <span class="display-headline text-[34px] leading-none">Curio</span>
            <span class="kicker num-tab">Vol. 047</span>
          </div>
          <p class="kicker mt-2.5">— Daily for AI model news</p>
        </router-link>
      </div>

      <!-- Issue date -->
      <div class="px-6 py-4 border-b border-[color:var(--rule)]">
        <p class="kicker mb-1">Today's edition</p>
        <p class="font-display text-[15px] leading-tight">{{ todayLong }}</p>
      </div>

      <!-- Nav -->
      <nav class="flex-1 px-3 py-5 space-y-1">
        <p class="px-3 mb-2 kicker">Sections</p>
        <router-link
          v-for="(item, i) in navItems"
          :key="item.to"
          :to="item.to"
          :class="[
            'group flex items-baseline gap-3 px-3 py-2.5 transition-colors',
            isActive(item.match)
              ? 'bg-ink text-[color:var(--paper)]'
              : 'text-[color:var(--ink)] hover:bg-paper-deep'
          ]"
        >
          <span
            :class="[
              'num-tab text-[10px] flex-shrink-0',
              isActive(item.match) ? 'opacity-70' : 'text-[color:var(--mute)]'
            ]"
          >{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="font-display text-[16px] leading-tight">{{ item.label }}</span>
        </router-link>

        <template v-if="authStore.user?.isAdmin">
          <div class="my-4 mx-3 border-t border-[color:var(--rule)]"></div>
          <p class="px-3 mb-2 kicker kicker-signal">Newsroom</p>
          <router-link
            v-for="(item, i) in adminNavItems"
            :key="item.to"
            :to="item.to"
            :class="[
              'group flex items-baseline gap-3 px-3 py-2.5 transition-colors',
              isActive(item.match)
                ? 'bg-ink text-[color:var(--paper)]'
                : 'text-[color:var(--ink)] hover:bg-paper-deep'
            ]"
          >
            <span
              :class="[
                'num-tab text-[10px] flex-shrink-0',
                isActive(item.match) ? 'opacity-70' : 'text-[color:var(--mute)]'
              ]"
            >{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="font-display text-[16px] leading-tight">{{ item.label }}</span>
          </router-link>
        </template>
      </nav>

      <!-- Foot: theme toggle + sign out -->
      <div class="border-t border-[color:var(--rule)] p-4 space-y-2">
        <button
          class="w-full flex items-center justify-between px-3 py-2 border border-[color:var(--rule)] hover:bg-paper-deep transition-colors"
          :aria-pressed="isDark"
          aria-label="Toggle theme"
          @click="toggleTheme"
        >
          <span class="kicker">Lights</span>
          <span class="font-mono-curio text-[11px] tracking-[0.18em] uppercase">
            <span :class="!isDark ? 'text-[color:var(--ink)]' : 'text-[color:var(--mute)]'">Day</span>
            <span class="mx-2 text-[color:var(--mute)]">·</span>
            <span :class="isDark ? 'text-[color:var(--ink)]' : 'text-[color:var(--mute)]'">Night</span>
          </span>
        </button>
        <button
          class="w-full flex items-center justify-between px-3 py-2 hover:bg-paper-deep transition-colors"
          @click="handleLogout"
        >
          <span class="font-display text-[15px]">Sign out</span>
          <span aria-hidden="true" class="font-mono-curio text-[14px] text-[color:var(--mute)]">↗</span>
        </button>
      </div>
    </aside>

    <!-- ── Mobile masthead ────────────────────────────────────────── -->
    <header class="sticky top-0 z-30 bg-paper/95 backdrop-blur border-b border-[color:var(--rule)] px-5 py-3 md:hidden">
      <div class="flex items-center justify-between">
        <router-link to="/dashboard/archive" class="flex items-baseline gap-2">
          <span class="display-headline text-[28px] leading-none">Curio</span>
          <span class="kicker">Vol. 047</span>
        </router-link>
        <button
          class="inline-flex h-11 w-11 items-center justify-center border border-[color:var(--rule)] bg-paper text-[color:var(--ink)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--signal)]"
          aria-label="Open menu"
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
          aria-label="Close menu"
          @click="closeMenu"
        />
        <aside
          ref="drawerRef"
          role="dialog"
          aria-modal="true"
          aria-label="Main menu"
          class="absolute inset-y-0 left-0 flex w-[280px] flex-col border-r border-[color:var(--rule)] bg-paper"
        >
          <div class="flex items-center justify-between border-b border-[color:var(--rule)] px-5 py-4">
            <span class="display-headline text-[26px] leading-none">Curio</span>
            <button
              class="inline-flex h-11 w-11 items-center justify-center border border-[color:var(--rule)] text-[color:var(--ink)]"
              aria-label="Close menu"
              @click="closeMenu"
            >
              <span class="font-mono-curio text-[16px]">×</span>
            </button>
          </div>

          <nav class="flex-1 px-3 py-5 space-y-1">
            <p class="px-3 mb-2 kicker">Sections</p>
            <router-link
              v-for="(item, i) in navItems"
              :key="`mobile-${item.to}`"
              :to="item.to"
              :class="[
                'flex items-baseline gap-3 px-3 py-3 transition-colors',
                isActive(item.match)
                  ? 'bg-ink text-[color:var(--paper)]'
                  : 'text-[color:var(--ink)]'
              ]"
              @click="closeMenu"
            >
              <span class="num-tab text-[10px] text-[color:var(--mute)]">{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="font-display text-[16px] leading-tight">{{ item.label }}</span>
            </router-link>

            <template v-if="authStore.user?.isAdmin">
              <div class="my-4 mx-3 border-t border-[color:var(--rule)]"></div>
              <p class="px-3 mb-2 kicker kicker-signal">Newsroom</p>
              <router-link
                v-for="(item, i) in adminNavItems"
                :key="`mobile-${item.to}`"
                :to="item.to"
                :class="[
                  'flex items-baseline gap-3 px-3 py-3 transition-colors',
                  isActive(item.match)
                    ? 'bg-ink text-[color:var(--paper)]'
                    : 'text-[color:var(--ink)]'
                ]"
                @click="closeMenu"
              >
                <span class="num-tab text-[10px] text-[color:var(--mute)]">{{ String(i + 1).padStart(2, '0') }}</span>
                <span class="font-display text-[16px] leading-tight">{{ item.label }}</span>
              </router-link>
            </template>
          </nav>

          <div class="border-t border-[color:var(--rule)] p-4 space-y-2">
            <button
              class="w-full flex items-center justify-between px-3 py-2 border border-[color:var(--rule)]"
              :aria-pressed="isDark"
              aria-label="Toggle theme"
              @click="toggleTheme"
            >
              <span class="kicker">Lights</span>
              <span class="font-mono-curio text-[11px] tracking-[0.18em] uppercase">
                <span :class="!isDark ? 'text-[color:var(--ink)]' : 'text-[color:var(--mute)]'">Day</span>
                <span class="mx-2 text-[color:var(--mute)]">·</span>
                <span :class="isDark ? 'text-[color:var(--ink)]' : 'text-[color:var(--mute)]'">Night</span>
              </span>
            </button>
            <button
              class="w-full flex items-center justify-between px-3 py-2"
              @click="handleLogout"
            >
              <span class="font-display text-[15px]">Sign out</span>
              <span aria-hidden="true" class="font-mono-curio text-[14px] text-[color:var(--mute)]">↗</span>
            </button>
          </div>
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
        aria-label="Service announcement"
        class="flex items-start justify-between gap-4 border-b border-[color:var(--rule)] bg-[color:var(--signal)]/10 px-5 py-3"
      >
        <p class="font-body-curio text-[13.5px] leading-relaxed text-[color:var(--ink-soft)]">
          <strong class="text-[color:var(--ink)]">Notice —</strong>
          Curio is free while we test, through the end of July 2026. From August–September,
          payments open and paid plans with extra features arrive — the free digest stays.
          Questions? Email
          <a href="mailto:sangmyeonglee123@gmail.com" class="underline underline-offset-2 hover:text-[color:var(--ink)]">Sangmyeong Lee</a>.
        </p>
        <button
          type="button"
          class="shrink-0 p-1 text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors"
          aria-label="Dismiss notice"
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
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { useFocusTrap } from '@/composables/useFocusTrap'
import { focusPageHeading } from '@/composables/useFocusOnEnter'
import { useEnsureTimezone } from '@/composables/useEnsureTimezone'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

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
  const w = d.toLocaleDateString('en-US', { weekday: 'long' })
  const m = d.toLocaleDateString('en-US', { month: 'long' })
  const day = d.getDate()
  const year = d.getFullYear()
  return `${w}, ${m} ${day} · ${year}`
})

const navItems = [
  { label: 'Today', to: '/dashboard/archive', match: ['/dashboard/archive', '/dashboard/quiz/'] },
  { label: 'Quiz history', to: '/dashboard/quiz-history', match: ['/dashboard/quiz-history'] },
  { label: 'Digest studio', to: '/dashboard/studio', match: ['/dashboard/studio'] },
  { label: 'Settings', to: '/dashboard/settings', match: ['/dashboard/settings'] },
] as const

const adminNavItems = [
  { label: 'Dashboard', to: '/admin/dashboard', match: ['/admin/dashboard'] },
  { label: 'All digests', to: '/admin/digests', match: ['/admin/digests'] },
  { label: 'Users', to: '/admin/users', match: ['/admin/users'] },
  { label: 'Stats', to: '/admin/stats', match: ['/admin/stats'] },
  { label: 'Audit log', to: '/admin/audit', match: ['/admin/audit'] },
] as const

const isActive = (matches: readonly string[]) =>
  matches.some((path) => route.path.startsWith(path))

const closeMenu = () => {
  isMenuOpen.value = false
}

// Declared before handleLogout (which assigns it) so every reference follows
// the declaration; used by the drawer focus-trap section further down.
let skipFocusRestore = false

const handleLogout = async () => {
  // Skip focus restoration; we're navigating away, not returning to the trigger.
  skipFocusRestore = true
  closeMenu()
  await authStore.logout()
  router.push({ name: 'home' })
}

watch(() => route.path, closeMenu)

// ── Theme toggle (single source of truth: useTheme) ───────────────
// Reuse the shared composable so the sidebar toggle stays in sync with the
// Settings selector and preserves the 'system' preference.
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
  // On a sign-out close we're navigating away, so the trigger no longer exists —
  // skip the focus restore for that case only.
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
