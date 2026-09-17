<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <PageMasthead
      :kicker="t('admin.users.kicker')"
      keypath="admin.users.headline"
      emphasis="readers"
      :emphasis-text="t('admin.users.readers')"
    />

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
    <ErrorState
      v-if="errorOccurred"
      :kicker="t('admin.common.loadFailed')"
      @retry="load(page)"
    />

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
              <span class="kicker" :style="user.deliveryEnabled ? 'color: var(--leaf)' : 'color: var(--mute)'">
                {{ user.deliveryEnabled ? '● ' + t('admin.common.on') : '○ ' + t('admin.common.off') }}
              </span>
            </td>
            <td class="py-3 text-right font-mono-curio text-[12px] text-[color:var(--mute)]">{{ formatDate(user.createdAt, 'short') }}</td>
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

      <PagerNav
        v-if="totalPages > 1"
        class="border-t-2 border-[color:var(--rule)] pt-6 mt-2"
        :page="page"
        :total-pages="totalPages"
        :newer-label="t('admin.common.pagination.earlier')"
        :older-label="t('admin.common.pagination.older')"
        @change="load"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useFormat } from '@/composables/useFormat'
import { usePagedAdminList } from '@/composables/usePagedAdminList'
import type { AdminUser } from '@/types/admin'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageMasthead from '@/components/ui/PageMasthead.vue'
import PagerNav from '@/components/ui/PagerNav.vue'

const { t } = useI18n()
const { formatDate } = useFormat()
const { error } = useToast()

const search = ref('')
const searchInput = ref('')

const { items: users, page, totalPages, loading, errorOccurred, load } = usePagedAdminList<AdminUser>(
  (nextPage) => api.admin.getUsers(nextPage, search.value),
  () => error(t('admin.users.toast.loadFailed'))
)

const applySearch = () => {
  search.value = searchInput.value.trim()
  load(0)
}

onMounted(() => load(0))
</script>
