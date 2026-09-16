import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/services/api'
import type { User, UpdateProfileRequest, DeliverySettings, DigestLanguage } from '@/types/user'

export const useUserStore = defineStore('user', () => {
  const profile = ref<User | null>(null)
  const preferences = ref<string[]>([])
  // Per-user delivery time (backend-backed via /user/preferences). null = default 08:00 UTC.
  const deliveryHour = ref<number | null>(null)
  const timezone = ref<string | null>(null)
  // true = timezone auto-follows the device; false = user pinned a fixed zone.
  const timezoneAuto = ref<boolean>(true)
  // Edition the digest, quiz and email are written in (account-level; the UI locale
  // is a separate, device-level choice — see useLocale). null until preferences load.
  const language = ref<DigestLanguage | null>(null)

  const fetchProfile = async () => {
    const response = await api.user.getProfile()
    profile.value = response.data
    return response.data
  }

  const updateProfile = async (data: UpdateProfileRequest) => {
    const response = await api.user.updateProfile(data)
    profile.value = response.data
    return response.data
  }

  const fetchPreferences = async () => {
    const response = await api.user.getPreferences()
    preferences.value = response.data.topics
    deliveryHour.value = response.data.deliveryHour ?? null
    timezone.value = response.data.timezone ?? null
    timezoneAuto.value = response.data.timezoneAuto ?? true
    language.value = response.data.language ?? null
    return response.data
  }

  const updatePreferences = async (topics: string[], delivery?: DeliverySettings) => {
    const response = await api.user.updatePreferences(topics, delivery)
    preferences.value = topics
    if (delivery?.deliveryHour !== undefined) deliveryHour.value = delivery.deliveryHour ?? null
    if (delivery?.timezone !== undefined) timezone.value = delivery.timezone ?? null
    if (delivery?.timezoneAuto !== undefined) timezoneAuto.value = delivery.timezoneAuto
    if (delivery?.language !== undefined) language.value = delivery.language
    return response.data
  }

  return {
    profile,
    preferences,
    deliveryHour,
    timezone,
    timezoneAuto,
    language,
    fetchProfile,
    updateProfile,
    fetchPreferences,
    updatePreferences
  }
})
