<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <PageMasthead
      :kicker="t('admin.audit.kicker')"
      keypath="admin.audit.headline"
      emphasis="trail"
      :emphasis-text="t('admin.audit.trail')"
    >
      <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] mt-4 leading-relaxed max-w-[64ch]">
        {{ t('admin.audit.intro') }}
      </p>
    </PageMasthead>

    <ErrorState
      v-if="errorOccurred"
      :kicker="t('admin.common.loadFailed')"
      @retry="load(page)"
    />

    <section v-else-if="loading" class="border-t-2 border-[color:var(--rule)] pt-12 text-center">
      <p class="kicker">{{ t('admin.audit.loading') }}</p>
    </section>

    <section v-else-if="entries.length === 0" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker mb-3">{{ t('admin.audit.empty.kicker') }}</p>
      <p class="font-body-curio text-[14px] text-[color:var(--ink-soft)]">
        {{ t('admin.audit.empty.body') }}
      </p>
    </section>

    <div v-else class="space-y-8">
      <table class="w-full border-t-2 border-b-2 border-[color:var(--rule)] text-left">
        <thead>
          <tr class="border-b border-[color:var(--rule)]">
            <th class="kicker py-3 pr-4">{{ t('admin.audit.columns.when') }}</th>
            <th class="kicker py-3 pr-4">{{ t('admin.audit.columns.actor') }}</th>
            <th class="kicker py-3 pr-4">{{ t('admin.audit.columns.action') }}</th>
            <th class="kicker py-3 pr-4">{{ t('admin.audit.columns.target') }}</th>
            <th class="kicker py-3">{{ t('admin.audit.columns.detail') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-[color:var(--rule)]">
          <tr v-for="entry in entries" :key="entry.id" class="align-top">
            <td class="py-3 pr-4 num-tab text-[12px] whitespace-nowrap text-[color:var(--ink-soft)]">
              {{ formatDateTime(entry.createdAt, 'short') }}
            </td>
            <td class="py-3 pr-4 font-body-curio text-[13px] max-w-[220px] truncate" :title="entry.actorEmail">
              {{ entry.actorEmail }}
            </td>
            <td class="py-3 pr-4">
              <!-- Backend enum code (e.g. GENERATE_DIGESTS) — shown raw in both editions. -->
              <span class="px-2 py-0.5 text-[10px] font-mono-curio uppercase tracking-[0.14em] border border-[color:var(--rule)]">
                {{ entry.action }}
              </span>
            </td>
            <td class="py-3 pr-4 font-body-curio text-[13px] text-[color:var(--ink-soft)] whitespace-nowrap">
              <template v-if="entry.targetType">
                {{ entry.targetType }}<span v-if="entry.targetId" class="num-tab text-[11px]"> · {{ shortId(entry.targetId) }}</span>
              </template>
              <template v-else>—</template>
            </td>
            <td class="py-3 font-mono-curio text-[11px] text-[color:var(--mute)] break-all">
              {{ formatMetadata(entry.metadata) }}
            </td>
          </tr>
        </tbody>
      </table>

      <PagerNav
        v-if="totalPages > 1"
        :page="page"
        :total-pages="totalPages"
        :newer-label="t('admin.common.pagination.newer')"
        :older-label="t('admin.common.pagination.older')"
        @change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useFormat } from '@/composables/useFormat'
import { usePagedAdminList } from '@/composables/usePagedAdminList'
import type { AuditLogEntry } from '@/types/admin'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageMasthead from '@/components/ui/PageMasthead.vue'
import PagerNav from '@/components/ui/PagerNav.vue'

const { t } = useI18n()
const { formatDateTime } = useFormat()
const { error: showError } = useToast()

const { items: entries, page, totalPages, loading, errorOccurred, load } = usePagedAdminList<AuditLogEntry>(
  (nextPage) => api.admin.getAuditLog(nextPage),
  () => showError(t('admin.audit.toast.loadFailed'))
)

const shortId = (id: string) => (id.length > 12 ? `${id.slice(0, 8)}…` : id)

const formatMetadata = (metadata: Record<string, unknown> | null) => {
  if (!metadata || Object.keys(metadata).length === 0) return '—'
  return Object.entries(metadata)
    .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
    .join(' · ')
}

onMounted(() => load())
</script>
