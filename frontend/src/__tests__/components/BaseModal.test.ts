import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BaseModal from '@/components/ui/BaseModal.vue'

describe('BaseModal', () => {
  it('does not render content when show is false', () => {
    const wrapper = mount(BaseModal, {
      props: { show: false },
      global: { stubs: { Teleport: true } }
    })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('renders content when show is true', () => {
    const wrapper = mount(BaseModal, {
      props: { show: true, title: 'My Modal' },
      global: { stubs: { Teleport: true } }
    })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('My Modal')
  })

  it('renders title', () => {
    const wrapper = mount(BaseModal, {
      props: { show: true, title: 'Test Title' },
      global: { stubs: { Teleport: true } }
    })
    expect(wrapper.find('h3').text()).toBe('Test Title')
  })

  it('emits close when backdrop clicked', async () => {
    const wrapper = mount(BaseModal, {
      props: { show: true, title: 'Modal' },
      global: { stubs: { Teleport: true } }
    })
    await wrapper.find('[aria-hidden="true"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('emits close when close button clicked', async () => {
    const wrapper = mount(BaseModal, {
      props: { show: true, title: 'Modal' },
      global: { stubs: { Teleport: true } }
    })
    await wrapper.find('button[aria-label="Close dialog"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('renders slot content', () => {
    const wrapper = mount(BaseModal, {
      props: { show: true, title: 'Modal' },
      slots: { default: '<p>Modal body</p>' },
      global: { stubs: { Teleport: true } }
    })
    expect(wrapper.text()).toContain('Modal body')
  })
})
