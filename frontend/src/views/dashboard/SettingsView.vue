<template>
  <div class="mx-auto max-w-[920px] px-5 py-10 sm:px-8 lg:px-12">
    <!-- ── Header ─────────────────────────────────── -->
    <header class="mb-10">
      <p class="kicker kicker-signal mb-3">Section VII — Masthead</p>
      <h1 class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95] mb-6">
        Your
        <em class="italic-display">settings</em>.
      </h1>
      <div class="rule-double w-full"></div>
    </header>

    <div class="space-y-14">
      <!-- ── Profile ────────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">§ I — Profile</p>
          <span class="kicker">{{ userStore.profile?.email }}</span>
        </div>
        <form @submit.prevent="handleProfileUpdate" class="space-y-7">
          <BaseInput
            v-model="fullName"
            label="Full name"
            id="settings-name"
            placeholder="Your name"
          />
          <div class="flex items-center justify-between border-t border-b border-[color:var(--rule)] py-4">
            <div>
              <p class="font-display text-[16px] mb-1">Daily email digest</p>
              <p class="kicker">Receive your edition every morning at {{ formatHour(deliveryHour) }} · {{ timezone }}</p>
            </div>
            <button
              type="button"
              role="switch"
              :aria-checked="deliveryEnabled"
              aria-label="Toggle daily email digest"
              :class="[
                'relative inline-flex h-7 w-14 items-center border transition-colors focus:outline-none focus:ring-2 focus:ring-[color:var(--signal)]',
                deliveryEnabled ? 'bg-ink border-[color:var(--ink)]' : 'bg-paper border-[color:var(--rule)]'
              ]"
              @click="deliveryEnabled = !deliveryEnabled"
            >
              <span
                :class="[
                  'inline-block h-5 w-5 transform transition-transform',
                  deliveryEnabled ? 'translate-x-8 bg-[color:var(--signal)]' : 'translate-x-1 bg-[color:var(--ink)]'
                ]"
              ></span>
            </button>
          </div>
          <!-- Delivery time — per-user hour + timezone, persisted with preferences -->
          <div v-if="deliveryEnabled" class="space-y-4">
            <label class="flex items-center gap-3 cursor-pointer select-none">
              <input
                type="checkbox"
                v-model="autoTimezone"
                @change="onAutoTimezoneToggle"
                class="h-4 w-4 accent-[color:var(--signal)]"
              />
              <span class="font-body-curio text-[14px] text-[color:var(--ink)]">
                Automatically use my device's timezone
              </span>
            </label>
            <div class="grid gap-5 sm:grid-cols-2">
              <div>
                <label for="settings-delivery-hour" class="kicker mb-2 block">Delivery time</label>
                <select
                  id="settings-delivery-hour"
                  v-model.number="deliveryHour"
                  class="w-full bg-paper border border-[color:var(--rule)] px-3 py-2.5 font-body-curio text-[14px] text-[color:var(--ink)] focus:outline-none focus:ring-2 focus:ring-[color:var(--signal)]"
                >
                  <option v-for="h in 24" :key="h - 1" :value="h - 1">{{ formatHour(h - 1) }}</option>
                </select>
              </div>
              <div>
                <label for="settings-timezone" class="kicker mb-2 block">Timezone</label>
                <select
                  id="settings-timezone"
                  v-model="timezone"
                  :disabled="autoTimezone"
                  class="w-full bg-paper border border-[color:var(--rule)] px-3 py-2.5 font-body-curio text-[14px] text-[color:var(--ink)] focus:outline-none focus:ring-2 focus:ring-[color:var(--signal)] disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <option v-for="tz in timezones" :key="tz" :value="tz">{{ tz }}</option>
                </select>
                <p v-if="autoTimezone" class="kicker mt-1.5 text-[color:var(--mute)]">
                  Following your device · {{ timezone }}
                </p>
              </div>
            </div>
          </div>
          <button type="submit" class="btn-editorial" :disabled="savingProfile">
            <span v-if="savingProfile">Saving…</span>
            <span v-else>Save changes</span>
            <span aria-hidden="true">→</span>
          </button>
        </form>
      </section>

      <!-- ── Appearance ─────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">§ II — Lights</p>
        </div>
        <fieldset>
          <legend class="sr-only">Theme preference</legend>
          <div class="grid grid-cols-3 gap-0 border border-[color:var(--rule)]" role="radiogroup" aria-label="Theme preference">
            <button
              v-for="(option, i) in themeOptions"
              :key="option.value"
              type="button"
              role="radio"
              :aria-checked="themePreference === option.value"
              :class="[
                'px-3 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] transition-colors',
                i < themeOptions.length - 1 ? 'border-r border-[color:var(--rule)]' : '',
                themePreference === option.value
                  ? 'bg-ink text-[color:var(--paper)]'
                  : 'bg-paper text-[color:var(--ink)] hover:bg-paper-deep'
              ]"
              @click="setTheme(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </fieldset>
        <p class="kicker mt-3">System follows your device preference</p>
      </section>

      <!-- ── Password (non-OAuth only) ─────────── -->
      <section v-if="userStore.profile && !isOAuthUser">
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">§ III — Password</p>
        </div>
        <form @submit.prevent="handlePasswordChange" class="space-y-7">
          <BaseInput
            v-model="currentPassword"
            type="password"
            label="Current password"
            id="settings-current-password"
            placeholder="••••••••"
          />
          <BaseInput
            v-model="newPassword"
            type="password"
            label="New password"
            id="settings-new-password"
            placeholder="At least 8 characters"
          />
          <button
            type="submit"
            class="btn-editorial"
            :disabled="savingPassword || !currentPassword || newPassword.length < 8"
          >
            <span v-if="savingPassword">Updating…</span>
            <span v-else>Update password</span>
            <span aria-hidden="true">→</span>
          </button>
        </form>
      </section>

      <!-- ── Bring Your Own Key (BYOK) ─────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker kicker-signal">§ IV — Bring your own key</p>
        </div>
        <ApiKeyManager />
      </section>

      <!-- ── Beats ──────────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">§ V — Beats</p>
          <span :class="['kicker', selectedTopics.length >= 3 ? 'text-[color:var(--leaf)]' : 'text-[color:var(--mute)]']">
            {{ selectedTopics.length }} selected{{ selectedTopics.length < 3 ? ' · 3 min' : '' }}
          </span>
        </div>

        <div class="space-y-0 mb-7">
          <div
            v-for="(domain, dIdx) in topicHierarchy"
            :key="domain.id"
            class="border-t border-[color:var(--rule)] last:border-b"
          >
            <button
              class="w-full flex items-center justify-between px-2 py-4 hover:bg-paper-deep transition-colors text-left"
              :aria-expanded="settingsExpandedDomains.has(domain.id)"
              :aria-controls="`settings-domain-panel-${domain.id}`"
              @click="toggleSettingsDomain(domain.id)"
            >
              <div class="flex items-baseline gap-4">
                <span class="num-tab text-[11px] text-[color:var(--mute)]">{{ String(dIdx + 1).padStart(2, '0') }}</span>
                <span class="font-display text-[18px] font-medium flex items-center gap-2">
                  <AppIcon :name="domain.icon" class="h-[18px] w-[18px] flex-shrink-0 text-[color:var(--signal-deep)]" />
                  {{ domain.name }}
                </span>
              </div>
              <div class="flex items-center gap-3">
                <span v-if="getDomainCount(domain) > 0" class="kicker kicker-signal">
                  {{ getDomainCount(domain) }} on
                </span>
                <span
                  :class="[
                    'font-mono-curio text-[18px] transition-transform',
                    settingsExpandedDomains.has(domain.id) ? 'rotate-45 text-[color:var(--signal-deep)]' : 'text-[color:var(--mute)]'
                  ]"
                  aria-hidden="true"
                >+</span>
              </div>
            </button>
            <div v-if="settingsExpandedDomains.has(domain.id)" :id="`settings-domain-panel-${domain.id}`" class="bg-paper-deep border-t border-[color:var(--rule)] divide-y divide-[color:var(--rule)]/40">
              <div v-for="sub in domain.subcategories" :key="sub.id" class="px-5 py-4">
                <p class="kicker mb-3">{{ sub.name }}</p>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="topic in sub.topics"
                    :key="topic"
                    :class="[
                      'flex items-baseline gap-2 px-3 py-1.5 border font-display text-[13px] transition-all',
                      selectedTopics.includes(topic)
                        ? 'bg-ink text-[color:var(--paper)] border-[color:var(--ink)]'
                        : 'bg-paper text-[color:var(--ink)] border-[color:var(--rule)] hover:border-[color:var(--signal)]'
                    ]"
                    @click="toggleTopic(topic)"
                  >
                    <span aria-hidden="true" class="font-mono-curio text-[10px] opacity-70">
                      {{ selectedTopics.includes(topic) ? '✓' : '+' }}
                    </span>
                    {{ topic }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <button
          class="btn-editorial"
          :disabled="selectedTopics.length < 3 || savingPreferences"
          @click="handlePreferencesUpdate"
        >
          <span v-if="savingPreferences">Saving…</span>
          <span v-else>Update preferences</span>
          <span aria-hidden="true">→</span>
        </button>
      </section>

      <!-- ── Danger zone ──────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker kicker-signal">§ VI — Delete account</p>
        </div>
        <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-5 max-w-[60ch] leading-relaxed">
          Permanently delete your account, your edition history, your quiz scores, and all
          associated data. The press will go quiet for you. This cannot be undone.
        </p>
        <button
          class="inline-flex items-center gap-3 border border-red-300 bg-transparent px-6 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] text-red-600 hover:bg-red-600 hover:text-white transition-colors"
          @click="showDeleteModal = true"
        >
          Delete my account
          <span aria-hidden="true">↗</span>
        </button>
      </section>
    </div>

    <BaseModal :show="showDeleteModal" title="Delete account" @close="showDeleteModal = false">
      <p class="font-body-curio text-[14.5px] text-[color:var(--ink-soft)] mb-6 leading-relaxed">
        Are you sure you want to delete your account? All your editions, quiz history,
        and preferences will be permanently removed.
      </p>
      <div class="flex gap-3 justify-end">
        <button class="btn-editorial-ghost" @click="showDeleteModal = false">Cancel</button>
        <button
          class="inline-flex items-center gap-3 bg-red-600 text-white px-5 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] hover:bg-red-700 transition-colors disabled:opacity-50"
          :disabled="deleting"
          @click="handleDelete"
        >
          <span v-if="deleting">Deleting…</span>
          <span v-else>Delete my account</span>
        </button>
      </div>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useTheme, type ThemePreference } from '@/composables/useTheme'
import { api } from '@/services/api'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import ApiKeyManager from '@/components/settings/ApiKeyManager.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { TOPIC_HIERARCHY } from '@/data/topics'
import type { TopicL1 } from '@/data/topics'
import { detectBrowserTimezone } from '@/utils/timezone'
import { getApiErrorMessage } from '@/utils/apiError'

const userStore = useUserStore()
const authStore = useAuthStore()
const router = useRouter()
const { success, error } = useToast()

const { preference: themePreference, setTheme } = useTheme()

const themeOptions: Array<{ value: ThemePreference; label: string }> = [
  { value: 'light', label: 'Day' },
  { value: 'dark', label: 'Night' },
  { value: 'system', label: 'System' },
]

const fullName = ref('')
const deliveryEnabled = ref(true)
const savingProfile = ref(false)

// Per-user delivery time (backend-backed via /user/preferences).
const deliveryHour = ref<number>(8)
const timezone = ref<string>('UTC')
// true = timezone auto-follows this device (default); false = user pinned a zone.
const autoTimezone = ref<boolean>(true)

// When re-enabling auto, snap the displayed zone back to the device's.
const onAutoTimezoneToggle = () => {
  if (autoTimezone.value) timezone.value = detectBrowserTimezone()
}

const FALLBACK_TZS = [
  'UTC', 'America/Los_Angeles', 'America/Denver', 'America/Chicago', 'America/New_York',
  'America/Sao_Paulo', 'Europe/London', 'Europe/Paris', 'Europe/Berlin', 'Europe/Moscow',
  'Africa/Johannesburg', 'Asia/Dubai', 'Asia/Kolkata', 'Asia/Singapore', 'Asia/Shanghai',
  'Asia/Tokyo', 'Asia/Seoul', 'Australia/Sydney', 'Pacific/Auckland',
]

const timezones = computed<string[]>(() => {
  const supported = (Intl as unknown as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf
  let list: string[] = []
  if (typeof supported === 'function') {
    try { list = supported('timeZone') } catch { list = [] }
  }
  if (!list.length) list = [...FALLBACK_TZS]
  if (!list.includes('UTC')) list = ['UTC', ...list]
  if (timezone.value && !list.includes(timezone.value)) list = [timezone.value, ...list]
  return list
})

const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`

const currentPassword = ref('')
const newPassword = ref('')
const savingPassword = ref(false)

const isOAuthUser = computed(() => userStore.profile?.hasPassword === false)

const topicHierarchy = TOPIC_HIERARCHY

const settingsExpandedDomains = ref<Set<string>>(new Set())
const toggleSettingsDomain = (id: string) => {
  if (settingsExpandedDomains.value.has(id)) settingsExpandedDomains.value.delete(id)
  else settingsExpandedDomains.value.add(id)
}
const selectedTopics = ref<string[]>([])

const getDomainCount = (domain: TopicL1) =>
  domain.subcategories.reduce(
    (acc, sub) => acc + sub.topics.filter((t) => selectedTopics.value.includes(t)).length,
    0
  )

const savingPreferences = ref(false)

const showDeleteModal = ref(false)
const deleting = ref(false)

onMounted(async () => {
  try {
    await Promise.all([
      userStore.fetchProfile(),
      userStore.fetchPreferences(),
    ])
    fullName.value = userStore.profile?.fullName || ''
    deliveryEnabled.value = userStore.profile?.deliveryEnabled ?? true
    deliveryHour.value = userStore.deliveryHour ?? 8
    autoTimezone.value = userStore.timezoneAuto ?? true
    // In auto mode show the live device zone; when pinned show the saved one.
    timezone.value = autoTimezone.value
      ? detectBrowserTimezone()
      : (userStore.timezone ?? detectBrowserTimezone())
    selectedTopics.value = [...userStore.preferences]
  } catch {
    error('Failed to load settings')
  }
})

const toggleTopic = (topic: string) => {
  const index = selectedTopics.value.indexOf(topic)
  if (index === -1) {
    selectedTopics.value.push(topic)
  } else {
    selectedTopics.value.splice(index, 1)
  }
}

const handleProfileUpdate = async () => {
  savingProfile.value = true
  try {
    await userStore.updateProfile({ fullName: fullName.value, deliveryEnabled: deliveryEnabled.value })
    // Delivery time is stored on /user/preferences (which also carries the topic
    // list). Persist it against the last-SAVED topics (userStore.preferences), NOT the
    // live Beats editor (selectedTopics) — that section has its own Save button, so a
    // profile save must never commit unsaved topic edits. A valid user always has 3+.
    if (userStore.preferences.length >= 3) {
      await userStore.updatePreferences(userStore.preferences, {
        // Auto → persist the current device zone; pinned → the user's chosen zone.
        timezone: autoTimezone.value ? detectBrowserTimezone() : timezone.value,
        deliveryHour: deliveryHour.value,
        timezoneAuto: autoTimezone.value,
      })
    }
    success('Profile updated')
  } catch (err: unknown) {
    error(getApiErrorMessage(err, 'Failed to update profile'))
  } finally {
    savingProfile.value = false
  }
}

const handlePasswordChange = async () => {
  savingPassword.value = true
  try {
    await api.user.changePassword(currentPassword.value, newPassword.value)
    success('Password updated')
    currentPassword.value = ''
    newPassword.value = ''
  } catch (err: unknown) {
    error(getApiErrorMessage(err, 'Failed to update password'))
  } finally {
    savingPassword.value = false
  }
}

const handlePreferencesUpdate = async () => {
  savingPreferences.value = true
  try {
    await userStore.updatePreferences(selectedTopics.value)
    success('Preferences updated')
  } catch (err: unknown) {
    error(getApiErrorMessage(err, 'Failed to update preferences'))
  } finally {
    savingPreferences.value = false
  }
}

const handleDelete = async () => {
  deleting.value = true
  try {
    await api.user.deleteAccount()
    authStore.clearTokens()
    success('Account deleted')
    router.push({ name: 'home' })
  } catch (err: unknown) {
    error(getApiErrorMessage(err, 'Failed to delete account'))
  } finally {
    deleting.value = false
  }
}
</script>
