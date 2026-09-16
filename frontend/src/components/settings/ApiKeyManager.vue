<template>
  <div>
    <p class="font-body-curio text-[14.5px] text-[color:var(--ink-soft)] leading-relaxed mb-6 max-w-[64ch]">
      {{ t('settings.byok.intro') }}
    </p>

    <!-- Saved keys -->
    <div v-if="apiKeysLoading" class="kicker mb-4">{{ t('settings.byok.loading') }}</div>
    <div v-else-if="apiKeys.length === 0" class="border-t border-b border-[color:var(--rule)] py-4 mb-4">
      <p class="kicker">{{ t('settings.byok.empty') }}</p>
    </div>
    <ul v-else class="border-t border-b border-[color:var(--rule)] divide-y divide-[color:var(--rule)] mb-4">
      <li
        v-for="key in apiKeys"
        :key="key.provider"
        class="py-4 flex items-baseline justify-between gap-4 flex-wrap"
      >
        <div class="flex-1 min-w-0">
          <div class="flex items-baseline gap-3 mb-1">
            <span class="font-display text-[18px]">{{ providerLabel(key.provider) }}</span>
            <span
              class="kicker"
              :style="key.validated ? 'color: var(--leaf)' : 'color: var(--signal-deep)'"
            >
              {{ key.validated ? t('settings.byok.validated') : t('settings.byok.notValidated') }}
            </span>
          </div>
          <p class="font-mono-curio text-[12px] text-[color:var(--ink-soft)]">
            {{ key.keyPreview }}
          </p>
          <p v-if="key.lastUsedAt" class="kicker mt-1">
            {{ t('settings.byok.lastUsed', { when: formatRelative(key.lastUsedAt) }) }}
          </p>
        </div>
        <button
          class="kicker hover:text-red-600 transition-colors"
          style="color: var(--signal-deep);"
          @click="askDelete(key.provider)"
        >{{ t('settings.byok.remove') }}</button>
      </li>
    </ul>

    <button class="btn-editorial-ghost" @click="openAddModal">
      <span aria-hidden="true">+</span> {{ t('settings.byok.add') }}
    </button>

    <!-- Add-key modal -->
    <BaseModal
      :show="showAddModal"
      :title="hasKey(addForm.provider)
        ? t('settings.byok.addModal.titleReplace', { provider: providerLabel(addForm.provider) })
        : t('settings.byok.addModal.titleAdd', { provider: providerLabel(addForm.provider) })"
      @close="showAddModal = false"
    >
      <i18n-t scope="global" keypath="settings.byok.addModal.body" tag="p" class="font-body-curio text-[13.5px] text-[color:var(--ink-soft)] mb-5 leading-relaxed">
        <template #provider>
          <strong class="font-semibold text-[color:var(--ink)]">{{ providerLabel(addForm.provider) }}</strong>
        </template>
      </i18n-t>
      <form @submit.prevent="submitAddKey" class="space-y-5">
        <div>
          <label class="kicker mb-1 block">{{ t('settings.byok.addModal.provider') }}</label>
          <div class="grid grid-cols-3 border border-[color:var(--rule)]">
            <button
              v-for="(label, p) in providerOptions"
              :key="p"
              type="button"
              :class="[
                'px-3 py-2 font-mono-curio text-[11px] uppercase tracking-[0.14em] transition-colors',
                addForm.provider === p
                  ? 'bg-ink text-[color:var(--paper)]'
                  : 'bg-paper text-[color:var(--ink)] hover:bg-paper-deep'
              ]"
              @click="addForm.provider = p as Provider"
            >{{ label }}<span v-if="hasKey(p as Provider)" class="ml-1.5 text-[color:var(--leaf)]" aria-hidden="true">●</span></button>
          </div>
        </div>
        <div
          v-if="hasKey(addForm.provider)"
          class="border border-[color:var(--rule)] bg-paper-deep p-3 font-body-curio text-[13px] text-[color:var(--ink-soft)] leading-relaxed"
        >
          {{ t('settings.byok.addModal.replaceNotice', { provider: providerLabel(addForm.provider) }) }}
        </div>
        <BaseInput
          v-model="addForm.apiKey"
          :label="t('settings.byok.addModal.apiKey')"
          type="password"
          :placeholder="apiKeyPlaceholder(addForm.provider)"
          id="byok-key"
        />
        <BaseInput
          v-model="addForm.currentPassword"
          :label="t('settings.byok.addModal.confirmPassword')"
          type="password"
          :placeholder="t('settings.byok.addModal.passwordPlaceholder')"
          id="byok-password"
        />
        <div v-if="addError" class="border border-red-300 bg-red-50/40 p-3 font-mono-curio text-[11px] uppercase tracking-[0.12em] text-red-600">
          {{ addError }}
        </div>
        <div class="flex justify-end pt-3 border-t border-[color:var(--rule)]">
          <div class="flex gap-3">
            <button type="button" class="btn-editorial-ghost" @click="showAddModal = false">{{ t('common.actions.cancel') }}</button>
            <button type="submit" class="btn-editorial" :disabled="addSaving">
              <span v-if="addSaving">{{ t('settings.byok.addModal.saving') }}</span>
              <span v-else>{{ hasKey(addForm.provider) ? t('settings.byok.addModal.rotate') : t('settings.byok.addModal.save') }}</span>
            </button>
          </div>
        </div>
      </form>
    </BaseModal>

    <!-- Delete-key modal -->
    <BaseModal
      :show="!!deleteTarget"
      :title="t('settings.byok.removeModal.title', { provider: deleteTarget ? providerLabel(deleteTarget) : '' })"
      @close="deleteTarget = null"
    >
      <p class="font-body-curio text-[14.5px] text-[color:var(--ink-soft)] mb-5">
        {{ t('settings.byok.removeModal.body') }}
      </p>
      <form @submit.prevent="submitDelete" class="space-y-4">
        <BaseInput
          v-model="deletePassword"
          :label="t('settings.byok.removeModal.password')"
          type="password"
          :placeholder="t('settings.byok.addModal.passwordPlaceholder')"
          id="byok-delete-password"
        />
        <div v-if="deleteError" class="border border-red-300 bg-red-50/40 p-3 font-mono-curio text-[11px] uppercase tracking-[0.12em] text-red-600">
          {{ deleteError }}
        </div>
        <div class="flex justify-end gap-3 pt-3 border-t border-[color:var(--rule)]">
          <button type="button" class="btn-editorial-ghost" @click="deleteTarget = null">{{ t('common.actions.cancel') }}</button>
          <button
            type="submit"
            class="inline-flex items-center gap-3 bg-red-600 text-white px-5 py-3 font-mono-curio text-[12px] uppercase tracking-[0.14em] hover:bg-red-700 transition-colors disabled:opacity-50"
            :disabled="deletingKey"
          >
            <span v-if="deletingKey">{{ t('settings.byok.removeModal.removing') }}</span>
            <span v-else>{{ t('settings.byok.removeModal.remove') }}</span>
          </button>
        </div>
      </form>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
/**
 * Self-contained Bring-Your-Own-Key manager: lists the user's saved provider
 * keys and handles the add/validate/delete flow (password re-auth required).
 * Used in both Settings and the Digest Studio. Emits `change` with the current
 * key list whenever it loads or is mutated, so a host page can react (e.g. to
 * show whether a validated key is on file).
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from '@/composables/useToast'
import { api } from '@/services/api'
import BaseInput from '@/components/ui/BaseInput.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import { getApiErrorMessage, getApiErrorStatus } from '@/utils/apiError'

type Provider = 'CLAUDE' | 'GEMINI' | 'OPENAI'

interface ApiKeyRow {
  provider: Provider
  keyPreview: string
  validated: boolean
  validatedAt: string | null
  lastUsedAt: string | null
  updatedAt: string
}

const emit = defineEmits<{ (e: 'change', keys: ApiKeyRow[]): void }>()

const { t } = useI18n()
const { success, info } = useToast()

const apiKeys = ref<ApiKeyRow[]>([])
const apiKeysLoading = ref(true)

const showAddModal = ref(false)
const addSaving = ref(false)
const addError = ref('')
const addForm = ref({
  provider: 'CLAUDE' as Provider,
  apiKey: '',
  currentPassword: '',
})

const deleteTarget = ref<Provider | null>(null)
const deletePassword = ref('')
const deleteError = ref('')
const deletingKey = ref(false)

// Provider names are product names — the same in every edition.
const providerOptions: Record<Provider, string> = {
  CLAUDE: 'Claude',
  GEMINI: 'Gemini',
  OPENAI: 'OpenAI',
}

const providerLabel = (p: Provider) => providerOptions[p] ?? p

const hasKey = (p: Provider) => apiKeys.value.some((k) => k.provider === p)

const apiKeyPlaceholder = (p: Provider) => {
  switch (p) {
    case 'CLAUDE': return 'sk-ant-api03-…'
    case 'GEMINI': return 'AIza…'
    case 'OPENAI': return 'sk-proj-…'
  }
}

const formatRelative = (iso: string) => {
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (seconds < 60) return t('settings.byok.relative.justNow')
  if (seconds < 3600) return t('settings.byok.relative.minutes', { count: Math.floor(seconds / 60) })
  if (seconds < 86400) return t('settings.byok.relative.hours', { count: Math.floor(seconds / 3600) })
  return t('settings.byok.relative.days', { count: Math.floor(seconds / 86400) })
}

const loadApiKeys = async () => {
  apiKeysLoading.value = true
  try {
    const res = await api.apiKeys.list()
    apiKeys.value = res.data
    emit('change', apiKeys.value)
  } catch {
    // Silent — list is optional and 401s redirect via interceptor.
  } finally {
    apiKeysLoading.value = false
  }
}

const openAddModal = () => {
  addForm.value = { provider: 'CLAUDE', apiKey: '', currentPassword: '' }
  addError.value = ''
  showAddModal.value = true
}

const submitAddKey = async () => {
  addError.value = ''
  if (!addForm.value.apiKey || addForm.value.apiKey.length < 16) {
    addError.value = t('settings.byok.errors.tooShort')
    return
  }
  if (!addForm.value.currentPassword) {
    addError.value = t('settings.byok.errors.passwordRequired')
    return
  }
  addSaving.value = true
  try {
    // The backend always validates now (no opt-out), so a resolved save means
    // the key was accepted. Still guard the response in case validation was
    // skipped server-side (e.g. validator unavailable) — an unvalidated key is
    // never used, so surface that instead of a misleading success.
    const res = await api.apiKeys.save(
      addForm.value.provider,
      addForm.value.apiKey,
      addForm.value.currentPassword,
    )
    showAddModal.value = false
    if (res.data?.validated === false) {
      info(t('settings.byok.toasts.savedUnvalidated'))
    } else {
      success(t('settings.byok.toasts.saved'))
    }
    await loadApiKeys()
  } catch (e: unknown) {
    addError.value = getApiErrorMessage(e,
      getApiErrorStatus(e) === 401 ? t('settings.byok.errors.wrongPassword') : t('settings.byok.errors.saveFailed'))
  } finally {
    addSaving.value = false
  }
}

const askDelete = (provider: Provider) => {
  deleteTarget.value = provider
  deletePassword.value = ''
  deleteError.value = ''
}

const submitDelete = async () => {
  if (!deleteTarget.value) return
  deletingKey.value = true
  deleteError.value = ''
  try {
    await api.apiKeys.delete(deleteTarget.value, deletePassword.value)
    success(t('settings.byok.toasts.removed'))
    deleteTarget.value = null
    await loadApiKeys()
  } catch (e: unknown) {
    deleteError.value = getApiErrorMessage(e,
      getApiErrorStatus(e) === 401 ? t('settings.byok.errors.wrongPassword') : t('settings.byok.errors.removeFailed'))
  } finally {
    deletingKey.value = false
  }
}

onMounted(loadApiKeys)

defineExpose({ reload: loadApiKeys })
</script>
