import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { createTestingPinia } from '@pinia/testing'
import DashboardLayout from '@/components/layout/DashboardLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { i18n } from '@/i18n'

const stub = { template: '<div/>' }
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: stub },
    {
      path: '/dashboard',
      component: DashboardLayout,
      children: [
        { path: 'archive', name: 'archive', component: stub },
        { path: 'quiz-history', component: stub },
        { path: 'studio', component: stub },
        { path: 'settings', component: stub },
      ],
    },
    { path: '/admin/:section', component: stub },
  ],
})

// The desktop sidebar and the mobile drawer are both in the DOM (Tailwind's
// md: breakpoints don't apply in jsdom); the drawer is the one with role=dialog.
const desktopSidebar = (w: ReturnType<typeof mountLayout>) =>
  w.findAll('aside').find((aside) => aside.attributes('role') !== 'dialog')!
const drawer = (w: ReturnType<typeof mountLayout>) => w.get('[role="dialog"][aria-label="Main menu"]')
const navLabels = (root: { findAll: (s: string) => { text: () => string }[] }) =>
  root.findAll('nav a').map((a) => a.text())

const mountLayout = () =>
  mount(DashboardLayout, {
    global: {
      plugins: [
        router,
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            auth: { user: { id: 'u1', email: 'admin@example.com', fullName: 'Admin', isAdmin: true } },
          },
        }),
      ],
    },
  })

describe('DashboardLayout menus', () => {
  beforeEach(async () => {
    i18n.global.locale.value = 'en'
    await router.push('/dashboard/archive')
    await router.isReady()
  })

  it('renders the same sections in the sidebar and the mobile drawer', async () => {
    const w = mountLayout()
    await flushPromises()

    expect(w.find('[role="dialog"]').exists()).toBe(false)
    await w.get('button[aria-label="Open menu"]').trigger('click')

    const dialog = drawer(w)
    expect(dialog.text()).toContain('Sections')
    expect(dialog.text()).toContain('Newsroom')

    const sidebarLabels = navLabels(desktopSidebar(w))
    expect(sidebarLabels.length).toBe(9)
    expect(sidebarLabels[0]).toContain('Today')
    expect(sidebarLabels[4]).toContain('Dashboard')
    expect(navLabels(dialog)).toEqual(sidebarLabels)

    // The active section is marked identically in both menus.
    const activeHref = (root: ReturnType<typeof drawer>) =>
      root.findAll('nav a').filter((a) => a.classes()).find((a) => a.classes().includes('bg-ink'))?.attributes('href')
    expect(activeHref(desktopSidebar(w))).toBe('/dashboard/archive')
    expect(activeHref(dialog)).toBe('/dashboard/archive')
  })

  it('closes the drawer when a section is chosen', async () => {
    const w = mountLayout()
    await flushPromises()
    await w.get('button[aria-label="Open menu"]').trigger('click')

    await drawer(w).findAll('nav a')[1]!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/dashboard/quiz-history')
    expect(w.find('[role="dialog"]').exists()).toBe(false)
  })

  it('signs out from the drawer foot and returns home', async () => {
    const w = mountLayout()
    await flushPromises()
    const authStore = useAuthStore()

    await w.get('button[aria-label="Open menu"]').trigger('click')
    const signOut = drawer(w).findAll('button').find((b) => b.text().includes('Sign out'))!
    await signOut.trigger('click')
    await flushPromises()

    expect(authStore.logout).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('home')
    expect(w.find('[role="dialog"]').exists()).toBe(false)
  })

  it('toggles the theme from either foot', async () => {
    const w = mountLayout()
    await flushPromises()

    const sidebarToggle = desktopSidebar(w).get('button[aria-label="Toggle theme"]')
    expect(sidebarToggle.attributes('aria-pressed')).toBe('false')
    await sidebarToggle.trigger('click')
    expect(sidebarToggle.attributes('aria-pressed')).toBe('true')

    await w.get('button[aria-label="Open menu"]').trigger('click')
    const drawerToggle = drawer(w).get('button[aria-label="Toggle theme"]')
    expect(drawerToggle.attributes('aria-pressed')).toBe('true')
    await drawerToggle.trigger('click')
    expect(drawerToggle.attributes('aria-pressed')).toBe('false')
    expect(sidebarToggle.attributes('aria-pressed')).toBe('false')
  })
})
