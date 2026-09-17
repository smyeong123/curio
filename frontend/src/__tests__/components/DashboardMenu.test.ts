import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import DashboardMenu from '@/components/layout/DashboardMenu.vue'
import DashboardMenuFoot from '@/components/layout/DashboardMenuFoot.vue'
import { i18n } from '@/i18n'

const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/:path(.*)*', component: { template: '<div/>' } }],
})

const navItems = [
  { label: 'Today', to: '/dashboard/archive', match: ['/dashboard/archive'] },
  { label: 'Settings', to: '/dashboard/settings', match: ['/dashboard/settings'] },
]
const adminItems = [{ label: 'Users', to: '/admin/users', match: ['/admin/users'] }]

const mountMenu = (props: Partial<InstanceType<typeof DashboardMenu>['$props']> = {}) =>
  mount(DashboardMenu, {
    props: { navItems, adminItems, ...props },
    global: { plugins: [router] },
  })

describe('DashboardMenu', () => {
  beforeEach(async () => {
    i18n.global.locale.value = 'en'
    await router.push('/dashboard/settings')
    await router.isReady()
  })

  it('numbers the reader sections and hides the newsroom for non-admins', () => {
    const w = mountMenu()
    const links = w.findAll('a')
    expect(links.map((a) => a.text())).toEqual(['01Today', '02Settings'])
    expect(w.text()).toContain('Sections')
    expect(w.text()).not.toContain('Newsroom')
    expect(links[1]!.classes()).toContain('bg-ink')
    expect(links[0]!.classes()).toContain('hover:bg-paper-deep')
  })

  it('marks the section whose path prefix matches the current route', async () => {
    await router.push('/dashboard/archive/anything')
    const w = mountMenu()
    const links = w.findAll('a')
    expect(links[0]!.classes()).toContain('bg-ink')
    expect(links[1]!.classes()).not.toContain('bg-ink')
  })

  it('adds the newsroom group behind a divider for admins', () => {
    const w = mountMenu({ showAdmin: true })
    expect(w.text()).toContain('Newsroom')
    expect(w.findAll('a').map((a) => a.attributes('href'))).toEqual([
      '/dashboard/archive',
      '/dashboard/settings',
      '/admin/users',
    ])
    expect(w.find('.border-t').exists()).toBe(true)
  })

  it('emits navigate on a section click and drops hover styling when compact', async () => {
    const w = mountMenu({ compact: true })
    expect(w.findAll('a')[0]!.classes()).not.toContain('hover:bg-paper-deep')
    expect(w.findAll('a')[0]!.classes()).toContain('py-3')
    await w.findAll('a')[0]!.trigger('click')
    expect(w.emitted('navigate')).toHaveLength(1)
  })
})

describe('DashboardMenuFoot', () => {
  it('emits toggle-theme and sign-out and reflects the theme state', async () => {
    const w = mount(DashboardMenuFoot, { props: { isDark: true }, global: { plugins: [router] } })
    const theme = w.get('button[aria-label="Toggle theme"]')
    expect(theme.attributes('aria-pressed')).toBe('true')
    await theme.trigger('click')
    expect(w.emitted('toggle-theme')).toHaveLength(1)

    const signOut = w.findAll('button').find((b) => b.text().includes('Sign out'))!
    await signOut.trigger('click')
    expect(w.emitted('sign-out')).toHaveLength(1)

    // The edition dropdown is the shared LanguageSelect in its sidebar row shape.
    expect(w.get('select[aria-label="Language"]').element.parentElement!.textContent).toContain('Edition')
  })
})
