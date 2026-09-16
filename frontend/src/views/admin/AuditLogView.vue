<template>
  <div class="mx-auto max-w-[1100px] px-5 py-10 sm:px-8 lg:px-12">
    <header class="mb-10">
      <p class="kicker kicker-signal mb-3">{{ t('admin.audit.kicker') }}</p>
      <i18n-t scope="global" keypath="admin.audit.headline" tag="h1" class="display-headline text-[clamp(48px,7vw,96px)] leading-[0.95] mb-6">
        <template #trail><em class="italic-display">{{ t('admin.audit.trail') }}</em></template>
      </i18n-t>
      <div class="rule-double w-full"></div>
      <p class="font-body-curio text-[15px] text-[color:var(--ink-soft)] mt-4 leading-relaxed max-w-[64ch]">
        {{ t('admin.audit.intro') }}
      </p>
    </header>

    <section v-if="errorOccurred" class="border border-[color:var(--rule)] bg-paper-deep p-12 text-center">
      <p class="kicker kicker-signal mb-3">{{ t('admin.common.loadFailed') }}</p>
      <button class="btn-editorial-ghost" @click="load(page)">{{ t('admin.common.retry') }}</button>
    </section>

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
              {{ formatWhen(entry.createdAt) }}
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

      <nav v-if="totalPages > 1" class="flex items-center justify-between">
        <button class="btn-editorial-ghost" :disabled="page === 0" @click="load(page - 1)">
          <span aria-hidden="true">←</span>
          {{ t('admin.common.pagination.newer') }}
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/services/api'
import { useToast } from '@/composables/useToast'
import { useLocale } from '@/composables/useLocale'

interface AuditLogEntry {
  id: number
  actorId: string | null
  actorEmail: string
  action: string
  targetType: string | null
  targetId: string | null
  requestId: string | null
  metadata: Record<string, unknown> | null
  createdAt: string
}

const { t } = useI18n()
const { intlLocale } = useLocale()
const { error: showError } = useToast()

const loading = ref(true)
const errorOccurred = ref(false)
const entries = ref<AuditLogEntry[]>([])
const page = ref(0)
const totalPages = ref(0)

const formatWhen = (dateStr: string) =>
  new Date(dateStr).toLocaleString(intlLocale.value, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })

const shortId = (id: string) => (id.length > 12 ? `${id.slice(0, 8)}…` : id)

const formatMetadata = (metadata: Record<string, unknown> | null) => {
  if (!metadata || Object.keys(metadata).length === 0) return '—'
  return Object.entries(metadata)
    .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
    .join(' · ')
}

const load = async (target = 0) => {
  loading.value = true
  errorOccurred.value = false
  try {
    const response = await api.admin.getAuditLog(target)
    entries.value = response.data.content
    totalPages.value = response.data.totalPages
    page.value = target
  } catch {
    errorOccurred.value = true
    showError(t('admin.audit.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(() => load())
</script>
