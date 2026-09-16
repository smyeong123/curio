import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'

vi.mock('@/services/api', () => ({
  api: {
    user: {
      getProfile: vi.fn(),
      updateProfile: vi.fn(),
      getPreferences: vi.fn(),
      updatePreferences: vi.fn()
    }
  }
}))

import { api } from '@/services/api'

const mockProfile = {
  id: 'user-1',
  email: 'test@example.com',
  fullName: 'Test User',
  isAdmin: false,
  deliveryEnabled: true,
  emailVerified: true,
  createdAt: '2026-01-01'
}

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts with null profile and empty preferences', () => {
    const store = useUserStore()
    expect(store.profile).toBeNull()
    expect(store.preferences).toEqual([])
  })

  it('fetchProfile sets profile', async () => {
    vi.mocked(api.user.getProfile).mockResolvedValue({ data: mockProfile } as any)
    const store = useUserStore()

    await store.fetchProfile()

    expect(store.profile).toEqual(mockProfile)
  })

  it('updateProfile updates profile', async () => {
    const updated = { ...mockProfile, fullName: 'Updated Name' }
    vi.mocked(api.user.updateProfile).mockResolvedValue({ data: updated } as any)
    const store = useUserStore()

    await store.updateProfile({ fullName: 'Updated Name' })

    expect(store.profile?.fullName).toBe('Updated Name')
  })

  it('fetchPreferences sets topics', async () => {
    vi.mocked(api.user.getPreferences).mockResolvedValue({ data: { topics: ['Claude (Anthropic)', 'Gemini (Google DeepMind)'] } } as any)
    const store = useUserStore()

    await store.fetchPreferences()

    expect(store.preferences).toEqual(['Claude (Anthropic)', 'Gemini (Google DeepMind)'])
  })

  it('updatePreferences sets topics locally', async () => {
    vi.mocked(api.user.updatePreferences).mockResolvedValue({ data: {} } as any)
    const store = useUserStore()

    await store.updatePreferences(['Claude (Anthropic)', 'Grok (xAI)', 'New & Emerging Models'])

    expect(store.preferences).toEqual(['Claude (Anthropic)', 'Grok (xAI)', 'New & Emerging Models'])
  })

  it('fetchPreferences reads the digest language, defaulting to null when absent', async () => {
    vi.mocked(api.user.getPreferences).mockResolvedValue({ data: { topics: ['DeepSeek'], language: 'ko' } } as any)
    const store = useUserStore()

    await store.fetchPreferences()
    expect(store.language).toBe('ko')

    vi.mocked(api.user.getPreferences).mockResolvedValue({ data: { topics: ['DeepSeek'] } } as any)
    await store.fetchPreferences()
    expect(store.language).toBeNull()
  })

  it('updatePreferences forwards the language and mirrors it locally', async () => {
    vi.mocked(api.user.updatePreferences).mockResolvedValue({ data: {} } as any)
    const store = useUserStore()
    const topics = ['Claude (Anthropic)', 'Grok (xAI)', 'New & Emerging Models']

    await store.updatePreferences(topics, { language: 'ko' })

    expect(api.user.updatePreferences).toHaveBeenCalledWith(topics, { language: 'ko' })
    expect(store.language).toBe('ko')
  })
})
