<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <header class="mb-10">
      <p class="kicker kicker-signal mb-3">{{ t('admin.users.kicker') }}</p>
      <i18n-t scope="global" keypath="admin.users.headline" tag="h1" class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95] mb-6">
        <template #readers><em class="italic-display">{{ t('admin.users.readers') }}</em></template>
      </i18n-t>
      <div class="rule-double w-full"></div>
    </header>

    <!-- Search -->
    <div class="mb-8 flex items-end gap-3 border-t border-b border-[color:var(--rule)] py-4">
      <div class="flex-1">
        <label for="user-search" class="kicker mb-2 block">{{ t('admin.users.searchLabel') }}</label>
        <input
          id="user-search"
          v-model="searchInput"
          type="text"
          :placeholder="t('admin.users.searchPlaceholder')"
          class="w-full bg-transparent border-b-2 border-[color:var(--rule)] py-2 font-display text-[18px] focus:outline-none focus:border-[color:var(--signal)] placeholder:font-body-curio placeholder:text-[14px] placeholder:text-[color:var(--mute)]"
          @keyup.enter="applySearch"
        />
      </div>
      <button class="btn-editorial flex-shrink-0" @click="applySearch">
        {{ t('admin.common.search') }}
        <span aria-hidden="true">→</span>
      </button>
    </div>

    <!-- Error / loading / empty / table -->
    <section v-if="errorOccurred" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker kicker-signal mb-3">{{ t('admin.common.loadFailed') }}</p>
      <button class="btn-editorial-ghost" @click="load(page)">{{ t('admin.common.retry') }}</button>
    </section>

    <section v-else-if="loading" class="border-t-2 border-[color:var(--rule)] pt-12 text-center">
      <p class="kicker">{{ t('admin.users.loading') }}</p>
    </section>

    <section v-else>
      <table class="w-full text-[13.5px] border-t-2 border-[color:var(--rule)]">
        <thead>
          <tr class="border-b border-[color:var(--rule)]">
            <th class="text-left py-3 kicker">{{ t('admin.common.columns.email') }}</th>
            <th class="text-left py-3 kicker">{{ t('admin.common.columns.name') }}</th>
            <th class="text-right py-3 kicker">{{ t('admin.common.columns.beats') }}</th>
            <th class="text-right py-3 kicker">{{ t('admin.common.columns.delivery') }}</th>
            <th class="text-right py-3 kicker">{{ t('admin.common.columns.joined') }}</th>
            <th class="text-right py-3 kicker"></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="user in users"
            :key="user.id"
            class="border-b border-[color:var(--rule)]/40 last:border-0"
          >
            <td class="py-3 font-display text-[15px] text-[color:var(--ink)]">{{ user.email }}</td>
            <td class="py-3 font-body-curio text-[14px] text-[color:var(--ink-soft)]">{{ user.fullName || '—' }}</td>
            <td class="py-3 text-right num-tab text-[14px]">{{ user.topicsCount }}</td>
            <td class="py-3 text-right">
              <span :class="user.deliveryEnabled ? 'kicker' : 'kicker'" :style="user.deliveryEnabled ? 'color: var(--leaf)' : 'color: var(--mute)'">
                {{ user.deliveryEnabled ? '● ' + t('admin.common.on') : '○ ' + t('admin.common.off') }}
              </span>
            </td>
            <td class="py-3 text-right font-mono-curio text-[12px] text-[color:var(--mute)]">{{ formatDate(user.createdAt) }}</td>
            <td class="py-3 text-right">
              <router-link
                :to="{ name: 'admin-user-detail', params: { id: user.id } }"
                class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.14em]"
              >
                {{ t('admin.users.view') }} →
              </router-link>
            </td>
          </tr>
          <tr v-if="users.length === 0">
            <td colspan="6" class="py-8 text-center kicker">{{ t('admin.users.empty') }}</td>
          </tr>
        </tbody>
      </table>

      <nav v-if="totalPages > 1" class="flex items-center justify-between border-t-2 border-[color:var(--rule)] pt-6 mt-2">
        <button class="btn-editorial-ghost" :disabled="page === 0" @click="load(page - 1)">
          <span aria-hidden="true">←</span>
          {{ t('admin.common.pagination.earlier') }}
        </button>
        <i18n-t scope="global" keypath="admin.common.pagination.pageOf" tag="span" class="kicker">
          <template #page><span class="num-tab text-[color:var(--ink)]">{{ page + 1 }}</span></template>
          <template #total><span class="num-tab text-[color:var(--ink)]">{{ totalPages }}</span></template>
        </i18n-t>
        <button class="btn-editorial-ghost" :disabled="page >= totalPages - 1" @click="load(page + 1)">
          {{ t('admin.common.pagination.older') }}
          <span aria-hidden="true">→</span>
        </button>
      </nav>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'

const { t } = useI18n()
const { intlLocale } = useLocale()
const { error } = useToast()

interface AdminUser {
  id: string
  email: string
  fullName: string | null
  topicsCount: number
  deliveryEnabled: boolean
  createdAt: string
}

const users = ref<AdminUser[]>([])
const loading = ref(false)
const errorOccurred = ref(false)
const page = ref(0)
const totalPages = ref(0)
const search = ref('')
const searchInput = ref('')

const load = async (nextPage = 0) => {
  loading.value = true
  errorOccurred.value = false
  try {
    const response = await api.admin.getUsers(nextPage, search.value)
    users.value = response.data.content
    page.value = response.data.number
    totalPages.value = response.data.totalPages
  } catch {
    errorOccurred.value = true
    error(t('admin.users.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

const applySearch = () => {
  search.value = searchInput.value.trim()
  load(0)
}

const formatDate = (value: string) =>
  new Date(value).toLocaleDateString(intlLocale.value, { year: 'numeric', month: 'short', day: 'numeric' })

onMounted(() => load(0))
</script>
