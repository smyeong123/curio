import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  // Reset scroll on forward navigation; restore saved position on back/forward.
  // Anchor links (to.hash) scroll to their target. Delayed slightly so the
  // page transition's leave animation finishes before we jump.
  scrollBehavior(to, _from, savedPosition) {
    return new Promise((resolve) => {
      setTimeout(() => {
        if (savedPosition) {
          resolve(savedPosition)
        } else if (to.hash) {
          resolve({ el: to.hash, behavior: 'smooth' })
        } else {
          resolve({ top: 0 })
        }
      }, 180)
    })
  },
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue')
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue')
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/RegisterView.vue')
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/views/auth/ResetPasswordView.vue')
    },
    {
      path: '/verify',
      name: 'verify-code',
      component: () => import('@/views/auth/VerifyCodeView.vue')
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('@/views/legal/PrivacyView.vue')
    },
    {
      path: '/terms',
      name: 'terms',
      component: () => import('@/views/legal/TermsView.vue')
    },
    {
      path: '/contact',
      name: 'contact',
      component: () => import('@/views/legal/ContactView.vue')
    },
    {
      path: '/onboarding',
      name: 'onboarding',
      component: () => import('@/views/OnboardingView.vue'),
      meta: { requiresAuth: true }
    },
    // Authenticated app shell: DashboardLayout (persistent sidebar) stays
    // mounted while the nested <router-view> transitions the content area.
    // Admin routes use absolute paths so they share this single shell
    // instance too (no layout remount when crossing dashboard ↔ admin).
    {
      path: '/dashboard',
      component: () => import('@/components/layout/DashboardLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'archive',
          name: 'archive',
          component: () => import('@/views/dashboard/ArchiveView.vue')
        },
        {
          path: 'quiz/:digestId',
          name: 'quiz',
          component: () => import('@/views/dashboard/QuizView.vue')
        },
        {
          path: 'quiz-history',
          name: 'quiz-history',
          component: () => import('@/views/dashboard/QuizHistoryView.vue')
        },
        {
          path: 'studio',
          name: 'studio',
          component: () => import('@/views/dashboard/StudioView.vue')
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/dashboard/SettingsView.vue')
        },
        {
          path: '/admin/dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/AdminDashboardView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: '/admin/digests',
          name: 'admin-digests',
          component: () => import('@/views/admin/AdminDigestsView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: '/admin/users',
          name: 'admin-users',
          component: () => import('@/views/admin/UsersView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: '/admin/users/:id',
          name: 'admin-user-detail',
          component: () => import('@/views/admin/UserDetailView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: '/admin/stats',
          name: 'admin-stats',
          component: () => import('@/views/admin/StatsView.vue'),
          meta: { requiresAdmin: true }
        },
        {
          path: '/admin/audit',
          name: 'admin-audit',
          component: () => import('@/views/admin/AuditLogView.vue'),
          meta: { requiresAdmin: true }
        }
      ]
    },
    {
      path: '/admin',
      redirect: { name: 'admin-dashboard' }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue')
    }
  ]
})

// Handle stale bundle chunk load errors after deployment. One-shot per path:
// if the freshly served bundle ALSO fails to load (CDN outage, broken deploy),
// reloading on every navigation would loop forever — reload once, then let the
// error surface. The guard clears on the next successful navigation.
const CHUNK_RELOAD_KEY = 'curio:chunk-reloaded'
router.onError((error, to) => {
  if (error.message.includes('Failed to fetch dynamically imported module') ||
      error.message.includes('Importing a module script failed')) {
    let alreadyReloaded = false
    try {
      alreadyReloaded = sessionStorage.getItem(CHUNK_RELOAD_KEY) === to.fullPath
      if (!alreadyReloaded) sessionStorage.setItem(CHUNK_RELOAD_KEY, to.fullPath)
    } catch { /* storage unavailable — fall through to a single best-effort reload */ }
    if (!alreadyReloaded) {
      window.location.href = to.fullPath
    }
  }
})

router.afterEach(() => {
  try { sessionStorage.removeItem(CHUNK_RELOAD_KEY) } catch { /* ignore */ }
})

// On first navigation, attempt silent token refresh via httpOnly cookie
let sessionInitialized = false

router.beforeEach(async (to, from) => {
  const authStore = useAuthStore()

  if (!sessionInitialized) {
    sessionInitialized = true
    // If we have a stored user but no access token (page reload), try silent refresh
    if (!authStore.accessToken && authStore.user) {
      try {
        await authStore.refreshToken()
      } catch {
        authStore.clearTokens()
      }
    }
  }

  // Entering the admin section (from outside it): re-verify the role against
  // the server so a demoted admin loses the UI now, not at token expiry. Not
  // re-checked on admin-internal navigation — every admin API call is
  // server-authorized anyway, this only keeps the shell honest.
  if (to.meta.requiresAdmin && !from.meta.requiresAdmin && authStore.isAuthenticated) {
    await authStore.syncRole()
  }

  if (to.name === 'home' && authStore.isAuthenticated) {
    return { name: 'archive' }
  } else if ((to.name === 'login' || to.name === 'register') && authStore.isAuthenticated) {
    return { name: 'archive' }
  } else if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  } else if (to.meta.requiresAdmin && !authStore.user?.isAdmin) {
    // Straight to archive: routing via 'home' would immediately re-trigger the
    // authenticated-home redirect above — an avoidable double navigation.
    return { name: 'archive' }
  }
})

export default router
