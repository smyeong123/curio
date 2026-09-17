<template>
  <div class="mx-auto max-w-[920px] px-5 py-10 sm:px-8 lg:px-12">
    <PageMasthead
      :kicker="t('settings.header.kicker')"
      keypath="settings.header.headline"
      emphasis="settings"
      :emphasis-text="t('settings.header.settings')"
    />

    <div class="space-y-14">
      <!-- ── Profile ────────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">{{ t('settings.profile.kicker') }}</p>
          <span class="kicker">{{ userStore.profile?.email }}</span>
        </div>
        <form @submit.prevent="handleProfileUpdate" class="space-y-7">
          <BaseInput
            v-model="fullName"
            :label="t('settings.profile.fullName')"
            id="settings-name"
            :placeholder="t('settings.profile.fullNamePlaceholder')"
          />
          <div class="flex items-center justify-between border-t border-b border-[color:var(--rule)] py-4">
            <div>
              <p class="font-display text-[16px] mb-1">{{ t('settings.profile.dailyDigest') }}</p>
              <p class="kicker">{{ t('settings.profile.dailyDigestHint', { time: formatHour(deliveryHour), timezone }) }}</p>
            </div>
            <button
              type="button"
              role="switch"
              :aria-checked="deliveryEnabled"
              :aria-label="t('settings.profile.toggleDigest')"
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
                {{ t('settings.profile.autoTimezone') }}
              </span>
            </label>
            <div class="grid gap-5 sm:grid-cols-2">
              <div>
                <label for="settings-delivery-hour" class="kicker mb-2 block">{{ t('settings.profile.deliveryTime') }}</label>
                <select
                  id="settings-delivery-hour"
                  v-model.number="deliveryHour"
                  class="w-full bg-paper border border-[color:var(--rule)] px-3 py-2.5 font-body-curio text-[14px] text-[color:var(--ink)] focus:outline-none focus:ring-2 focus:ring-[color:var(--signal)]"
                >
                  <option v-for="h in 24" :key="h - 1" :value="h - 1">{{ formatHour(h - 1) }}</option>
                </select>
              </div>
              <div>
                <label for="settings-timezone" class="kicker mb-2 block">{{ t('settings.profile.timezone') }}</label>
                <select
                  id="settings-timezone"
                  v-model="timezone"
                  :disabled="autoTimezone"
                  class="w-full bg-paper border border-[color:var(--rule)] px-3 py-2.5 font-body-curio text-[14px] text-[color:var(--ink)] focus:outline-none focus:ring-2 focus:ring-[color:var(--signal)] disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <option v-for="tz in timezones" :key="tz" :value="tz">{{ tz }}</option>
                </select>
                <p v-if="autoTimezone" class="kicker mt-1.5 text-[color:var(--mute)]">
                  {{ t('settings.profile.followingDevice', { timezone }) }}
                </p>
              </div>
            </div>
          </div>
          <button type="submit" class="btn-editorial" :disabled="savingProfile">
            <span v-if="savingProfile">{{ t('settings.profile.saving') }}</span>
            <span v-else>{{ t('settings.profile.save') }}</span>
            <span aria-hidden="true">→</span>
          </button>
        </form>
      </section>

      <!-- ── Appearance ─────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">{{ t('settings.lights.kicker') }}</p>
        </div>
        <SegmentedControl
          :options="themeOptions"
          :model-value="themePreference"
          :label="t('settings.lights.legend')"
          @update:model-value="setTheme"
        />
        <p class="kicker mt-3">{{ t('settings.lights.hint') }}</p>
      </section>

      <!-- ── Edition (UI language) ──────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">{{ t('settings.edition.kicker') }}</p>
        </div>
        <SegmentedControl
          :options="localeOptions"
          :model-value="edition"
          :label="t('common.language.label')"
          @update:model-value="chooseEdition"
        />
        <p class="kicker mt-3">{{ t('settings.edition.hint') }}</p>
      </section>

      <!-- ── Password (non-OAuth only) ─────────── -->
      <section v-if="userStore.profile && !isOAuthUser">
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">{{ t('settings.password.kicker') }}</p>
        </div>
        <form @submit.prevent="handlePasswordChange" class="space-y-7">
          <BaseInput
            v-model="currentPassword"
            type="password"
            :label="t('settings.password.current')"
            id="settings-current-password"
            placeholder="••••••••"
          />
          <BaseInput
            v-model="newPassword"
            type="password"
            :label="t('settings.password.new')"
            id="settings-new-password"
            :placeholder="t('settings.password.newPlaceholder')"
          />
          <button
            type="submit"
            class="btn-editorial"
            :disabled="savingPassword || !currentPassword || newPassword.length < 8"
          >
            <span v-if="savingPassword">{{ t('settings.password.updating') }}</span>
            <span v-else>{{ t('settings.password.update') }}</span>
            <span aria-hidden="true">→</span>
          </button>
        </form>
      </section>

      <!-- ── Bring Your Own Key (BYOK) ─────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker kicker-signal">{{ t('settings.byok.kicker') }}</p>
        </div>
        <ApiKeyManager />
      </section>

      <!-- ── Beats ──────────────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker">{{ t('settings.beats.kicker') }}</p>
          <span :class="['kicker', selectedTopics.length >= 3 ? 'text-[color:var(--leaf)]' : 'text-[color:var(--mute)]']">
            {{ t('settings.beats.selectedCount', { count: selectedTopics.length }) }}{{ selectedTopics.length < 3 ? t('settings.beats.minHint') : '' }}
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
              :aria-expanded="expandedDomains.has(domain.id)"
              :aria-controls="`settings-domain-panel-${domain.id}`"
              @click="toggleDomain(domain.id)"
            >
              <div class="flex items-baseline gap-4">
                <span class="num-tab text-[11px] text-[color:var(--mute)]">{{ String(dIdx + 1).padStart(2, '0') }}</span>
                <span class="font-display text-[18px] font-medium flex items-center gap-2">
                  <AppIcon :name="domain.icon" class="h-[18px] w-[18px] flex-shrink-0 text-[color:var(--signal-deep)]" />
                  {{ groupName(domain) }}
                </span>
              </div>
              <div class="flex items-center gap-3">
                <span v-if="domainCount(domain) > 0" class="kicker kicker-signal">
                  {{ t('settings.beats.domainOn', { count: domainCount(domain) }) }}
                </span>
                <span
                  :class="[
                    'font-mono-curio text-[18px] transition-transform',
                    expandedDomains.has(domain.id) ? 'rotate-45 text-[color:var(--signal-deep)]' : 'text-[color:var(--mute)]'
                  ]"
                  aria-hidden="true"
                >+</span>
              </div>
            </button>
            <div v-if="expandedDomains.has(domain.id)" :id="`settings-domain-panel-${domain.id}`" class="bg-paper-deep border-t border-[color:var(--rule)] divide-y divide-[color:var(--rule)]/40">
              <div v-for="sub in domain.subcategories" :key="sub.id" class="px-5 py-4">
                <p class="kicker mb-3">{{ groupName(sub) }}</p>
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
                    {{ topicLabel(topic) }}
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
          <span v-if="savingPreferences">{{ t('settings.beats.saving') }}</span>
          <span v-else>{{ t('settings.beats.update') }}</span>
          <span aria-hidden="true">→</span>
        </button>
      </section>

      <!-- ── Danger zone ──────────────────────── -->
      <section>
        <div class="flex items-baseline justify-between mb-5 border-b border-[color:var(--rule)] pb-2">
          <p class="kicker kicker-signal">{{ t('settings.danger.kicker') }}</p>
        </div>
        <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)] mb-5 max-w-[60ch] leading-relaxed">
          {{ t('settings.danger.body') }}
        </p>
        <button
          class="inline-flex items-center gap-3 border border-red-300 bg-transparent px-6 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] text-red-600 hover:bg-red-600 hover:text-white transition-colors"
          @click="showDeleteModal = true"
        >
          {{ t('settings.danger.delete') }}
          <span aria-hidden="true">↗</span>
        </button>
      </section>
    </div>

    <BaseModal :show="showDeleteModal" :title="t('settings.danger.modalTitle')" @close="showDeleteModal = false">
      <p class="font-body-curio text-[14.5px] text-[color:var(--ink-soft)] mb-6 leading-relaxed">
        {{ t('settings.danger.confirm') }}
      </p>
      <div class="flex gap-3 justify-end">
        <button class="btn-editorial-ghost" @click="showDeleteModal = false">{{ t('common.actions.cancel') }}</button>
        <button
          class="inline-flex items-center gap-3 bg-red-600 text-white px-5 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] hover:bg-red-700 transition-colors disabled:opacity-50"
          :disabled="deleting"
          @click="handleDelete"
        >
          <span v-if="deleting">{{ t('settings.danger.deleting') }}</span>
          <span v-else>{{ t('settings.danger.delete') }}</span>
        </button>
      </div>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useTheme, type ThemePreference } from '@/composables/useTheme'
import { useLocale } from '@/composables/useLocale'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { useTopicSelection } from '@/composables/useTopicSelection'
import { api } from '@/services/api'
import type { Locale } from '@/i18n'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import PageMasthead from '@/components/ui/PageMasthead.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import ApiKeyManager from '@/components/settings/ApiKeyManager.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { TOPIC_HIERARCHY } from '@/data/topics'
import { detectBrowserTimezone } from '@/utils/timezone'
import { getApiErrorMessage } from '@/utils/apiError'

const userStore = useUserStore()
const authStore = useAuthStore()
const router = useRouter()
const { t } = useI18n()
const { success, error } = useToast()
const { topicLabel, groupName } = useTopicLabels()

const { preference: themePreference, setTheme } = useTheme()

const themeOptions = computed<Array<{ value: ThemePreference; label: string }>>(() => [
  { value: 'light', label: t('settings.lights.day') },
  { value: 'dark', label: t('settings.lights.night') },
  { value: 'system', label: t('settings.lights.system') },
])

// Edition (English / Korean) for the ACCOUNT. Picking one flips the UI locale at
// once (useLocale) AND saves the digest/quiz/email language to /user/preferences.
// The masthead and sidebar toggles change only what is on screen, so the active
// radio reflects the SAVED account edition, falling back to the UI locale until
// preferences have loaded.
const { locale, setLocale } = useLocale()
const localeOptions = computed<Array<{ value: Locale; label: string; lang: Locale }>>(() => [
  { value: 'en', label: t('common.language.en'), lang: 'en' },
  { value: 'ko', label: t('common.language.ko'), lang: 'ko' },
])
const edition = computed<Locale>(() => (userStore.language as Locale | null) ?? locale.value)
const chooseEdition = async (value: Locale) => {
  setLocale(value)
  const previous = userStore.language
  userStore.language = value
  // A user who never finished onboarding has no topic list yet; the preferences
  // endpoint requires one, so their edition is captured when onboarding saves.
  if (userStore.preferences.length < 3) return
  try {
    await userStore.updatePreferences(userStore.preferences, { language: value })
    success(t('settings.edition.saved'))
  } catch (err: unknown) {
    userStore.language = previous
    error(getApiErrorMessage(err, t('settings.edition.saveFailed')))
  }
}

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

// 24-hour "HH:00" in both editions — it is the format the backend schedules
// on, and the Korean copy elsewhere quotes delivery hours the same way.
const formatHour = (h: number) => `${String(h).padStart(2, '0')}:00`

const currentPassword = ref('')
const newPassword = ref('')
const savingPassword = ref(false)

const isOAuthUser = computed(() => userStore.profile?.hasPassword === false)

const topicHierarchy = TOPIC_HIERARCHY

// The Beats editor works on its own copy of the topic list (filled from the
// store on mount) so edits only reach the server through its own Save button.
const { selectedTopics, toggleTopic, expandedDomains, toggleDomain, domainCount } = useTopicSelection()

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
    error(t('settings.toasts.loadFailed'))
  }
})

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
    success(t('settings.toasts.profileUpdated'))
  } catch (err: unknown) {
    error(getApiErrorMessage(err, t('settings.toasts.profileFailed')))
  } finally {
    savingProfile.value = false
  }
}

const handlePasswordChange = async () => {
  savingPassword.value = true
  try {
    await api.user.changePassword(currentPassword.value, newPassword.value)
    success(t('settings.toasts.passwordUpdated'))
    currentPassword.value = ''
    newPassword.value = ''
  } catch (err: unknown) {
    error(getApiErrorMessage(err, t('settings.toasts.passwordFailed')))
  } finally {
    savingPassword.value = false
  }
}

const handlePreferencesUpdate = async () => {
  savingPreferences.value = true
  try {
    await userStore.updatePreferences(selectedTopics.value)
    success(t('settings.toasts.preferencesUpdated'))
  } catch (err: unknown) {
    error(getApiErrorMessage(err, t('settings.toasts.preferencesFailed')))
  } finally {
    savingPreferences.value = false
  }
}

const handleDelete = async () => {
  deleting.value = true
  try {
    await api.user.deleteAccount()
    authStore.clearTokens()
    success(t('settings.toasts.accountDeleted'))
    router.push({ name: 'home' })
  } catch (err: unknown) {
    error(getApiErrorMessage(err, t('settings.toasts.deleteFailed')))
  } finally {
    deleting.value = false
  }
}
</script>
