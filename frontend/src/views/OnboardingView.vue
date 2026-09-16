<template>
  <div class="relative min-h-screen bg-paper text-[color:var(--ink)]">
    <!-- ── Top strip ────────────────────────────────────────────── -->
    <div class="bg-ink text-[color:var(--paper)] py-2">
      <div class="mx-auto flex w-full max-w-[1100px] items-center justify-between px-6 text-[11px] uppercase tracking-[0.18em] font-mono-curio">
        <span>Curio · New subscriber setup</span>
        <button class="opacity-80 hover:opacity-100" @click="handleLogout">Log out ↗</button>
      </div>
    </div>

    <div class="mx-auto w-full max-w-[1100px] px-6 py-12 lg:py-16">
      <!-- ── Header ──────────────────────────────────────────────── -->
      <header class="mb-12">
        <p class="kicker kicker-signal mb-4">Section I — Pick your beats</p>
        <h1 class="display-headline text-[clamp(40px,6vw,84px)] leading-[0.96] mb-6">
          What's
          <em class="italic-display">your beat</em>
          this morning?
        </h1>
        <p class="font-body-curio text-[16px] text-[color:var(--ink-soft)] leading-relaxed max-w-[60ch]">
          Curio writes a different paper for every reader. Pick at least three beats and
          we'll file your first issue at 08:00 tomorrow. You can change them whenever you
          like.
        </p>
      </header>

      <!-- ── Selection counter ──────────────────────────────────── -->
      <div class="border-t-2 border-b-2 border-[color:var(--rule)] py-5 mb-10 flex items-center justify-between flex-wrap gap-4">
        <div class="flex items-baseline gap-4">
          <span class="deco-num text-[64px] leading-none">{{ selectedTopics.length }}</span>
          <div>
            <p class="kicker">Selected</p>
            <p class="font-display text-[15px] text-[color:var(--ink-soft)]">
              <span class="num-tab">3</span> minimum · pick as many as you like
              <span v-if="selectedTopics.length < 3" class="text-[color:var(--signal-deep)]">
                · {{ 3 - selectedTopics.length }} more to go
              </span>
              <span v-else class="text-[color:var(--leaf)]">· you're all set</span>
            </p>
          </div>
        </div>
      </div>

      <!-- ── Quick picks ────────────────────────────────────────── -->
      <section class="mb-12">
        <p class="kicker mb-4">— Or grab a preset bundle —</p>
        <div class="flex flex-wrap gap-3">
          <button
            v-for="preset in quickPicks"
            :key="preset.label"
            class="group flex items-baseline gap-3 px-4 py-2.5 border border-[color:var(--rule)] bg-paper hover:bg-ink hover:text-[color:var(--paper)] transition-colors"
            @click="applyPreset(preset.topics)"
          >
            <span class="font-display text-[16px] leading-none flex items-center gap-2">
              <AppIcon :name="preset.icon" class="h-4 w-4 flex-shrink-0 text-[color:var(--signal-deep)] group-hover:text-[color:var(--paper)]" />
              {{ preset.label }}
            </span>
            <span class="num-tab text-[10px] text-[color:var(--mute)] group-hover:text-[color:var(--paper)]/70">+{{ preset.topics.length }}</span>
          </button>
        </div>
      </section>

      <!-- ── Topic hierarchy ───────────────────────────────────── -->
      <section class="space-y-0 mb-12">
        <article
          v-for="(domain, dIdx) in topicHierarchy"
          :key="domain.id"
          class="border-t-2 border-[color:var(--rule)] last:border-b-2"
        >
          <!-- L1 -->
          <button
            class="w-full flex items-center justify-between px-2 py-7 hover:bg-paper-deep transition-colors text-left"
            :aria-expanded="expandedDomains.has(domain.id)"
            :aria-controls="`domain-panel-${domain.id}`"
            @click="toggleDomain(domain.id)"
          >
            <div class="flex items-baseline gap-5 min-w-0">
              <span class="deco-num text-[44px] leading-none text-[color:var(--mute)] flex-shrink-0">{{ String(dIdx + 1).padStart(2, '0') }}</span>
              <div class="min-w-0">
                <div class="flex items-baseline gap-3 mb-1">
                  <span class="kicker">Chapter</span>
                  <span v-if="getDomainSelectedCount(domain) > 0" class="kicker kicker-signal">
                    {{ getDomainSelectedCount(domain) }} on the beat
                  </span>
                </div>
                <h3 class="display-headline text-[clamp(24px,3.5vw,38px)] leading-tight flex items-center gap-3">
                  <AppIcon :name="domain.icon" class="h-[0.75em] w-[0.75em] flex-shrink-0 text-[color:var(--signal-deep)]" />
                  <span>{{ domain.name }}</span>
                </h3>
                <p class="font-body-curio text-[13px] text-[color:var(--mute)] mt-1">{{ domain.description }}</p>
              </div>
            </div>
            <span
              :class="[
                'font-mono-curio text-[20px] flex-shrink-0 ml-4 transition-transform',
                expandedDomains.has(domain.id) ? 'rotate-45 text-[color:var(--signal-deep)]' : 'text-[color:var(--mute)]'
              ]"
              aria-hidden="true"
            >+</span>
          </button>

          <!-- L2/L3 -->
          <div v-if="expandedDomains.has(domain.id)" :id="`domain-panel-${domain.id}`" class="border-t border-[color:var(--rule)] bg-paper-deep">
            <div
              v-for="(subcategory, sIdx) in domain.subcategories"
              :key="subcategory.id"
              class="border-b border-[color:var(--rule)] last:border-b-0"
            >
              <!-- L2 -->
              <button
                class="w-full flex items-center justify-between px-6 py-4 hover:bg-paper transition-colors text-left"
                :aria-expanded="expandedSubcategories.has(subcategory.id)"
                :aria-controls="`subcategory-panel-${subcategory.id}`"
                @click="toggleSubcategory(subcategory.id)"
              >
                <div class="flex items-baseline gap-4">
                  <span class="kicker num-tab">{{ String.fromCharCode(65 + sIdx) }}</span>
                  <span class="font-display text-[18px] font-medium">{{ subcategory.name }}</span>
                </div>
                <div class="flex items-center gap-3">
                  <span
                    v-if="getSubcategorySelectedCount(subcategory) > 0"
                    class="kicker"
                    style="color: var(--leaf);"
                  >
                    {{ getSubcategorySelectedCount(subcategory) }}/{{ subcategory.topics.length }}
                  </span>
                  <span
                    :class="[
                      'font-mono-curio text-[16px]',
                      expandedSubcategories.has(subcategory.id) ? 'text-[color:var(--ink)] rotate-180' : 'text-[color:var(--mute)]'
                    ]"
                    aria-hidden="true"
                  >∨</span>
                </div>
              </button>

              <!-- L3 -->
              <div v-if="expandedSubcategories.has(subcategory.id)" :id="`subcategory-panel-${subcategory.id}`" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2 px-6 pb-5 bg-paper-deep">
                <button
                  v-for="topic in subcategory.topics"
                  :key="topic"
                  :class="[
                    'group text-left px-4 py-3 border transition-all flex items-center justify-between gap-2',
                    selectedTopics.includes(topic)
                      ? 'border-[color:var(--ink)] bg-ink text-[color:var(--paper)]'
                      : 'border-[color:var(--rule)] bg-paper text-[color:var(--ink)] hover:border-[color:var(--signal)] hover:translate-x-[-2px]'
                  ]"
                  @click="toggleTopic(topic)"
                >
                  <span class="font-display text-[14px] leading-tight">{{ topic }}</span>
                  <span
                    :class="[
                      'font-mono-curio text-[12px] flex-shrink-0',
                      selectedTopics.includes(topic) ? 'opacity-100' : 'opacity-40 group-hover:opacity-100 group-hover:text-[color:var(--signal-deep)]'
                    ]"
                    aria-hidden="true"
                  >{{ selectedTopics.includes(topic) ? '✓' : '+' }}</span>
                </button>
              </div>
            </div>
          </div>
        </article>
      </section>

      <!-- ── Selection summary ───────────────────────────────── -->
      <section v-if="selectedTopics.length > 0" class="mb-10 border border-[color:var(--rule)] bg-paper-deep p-6">
        <p class="kicker mb-3">Your beats</p>
        <div class="flex flex-wrap gap-2">
          <span
            v-for="topic in selectedTopics"
            :key="topic"
            class="inline-flex items-center gap-2 px-3 py-1.5 bg-ink text-[color:var(--paper)] font-mono-curio text-[11px] uppercase tracking-[0.12em]"
          >
            {{ topic }}
            <button
              class="opacity-70 hover:opacity-100 hover:text-[color:var(--signal)]"
              @click="toggleTopic(topic)"
              :aria-label="`Remove ${topic}`"
            >×</button>
          </span>
        </div>
      </section>

      <!-- ── CTA ─────────────────────────────────────────────── -->
      <div class="border-t-2 border-[color:var(--rule)] pt-8 flex flex-col items-center gap-4">
        <button
          class="btn-editorial text-[14px] px-10 py-4"
          :disabled="selectedTopics.length < 3 || saving"
          @click="handleSave"
        >
          <span v-if="saving">Filing…</span>
          <span v-else>Start reading</span>
          <span aria-hidden="true">→</span>
        </button>
        <p v-if="selectedTopics.length < 3" class="kicker">
          {{ 3 - selectedTopics.length }} more beat{{ 3 - selectedTopics.length !== 1 ? 's' : '' }} to publish
        </p>
        <router-link to="/dashboard/archive" class="kicker hover:text-[color:var(--ink)]">
          Skip for now —
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { useToast } from '@/composables/useToast'
import { TOPIC_HIERARCHY } from '@/data/topics'
import type { TopicL1, TopicL2, TopicIcon } from '@/data/topics'
import { detectBrowserTimezone } from '@/utils/timezone'
import AppIcon from '@/components/ui/AppIcon.vue'
import { getApiErrorMessage } from '@/utils/apiError'

const topicHierarchy = TOPIC_HIERARCHY

interface QuickPick { label: string; icon: TopicIcon | 'globe'; topics: string[] }

const quickPicks: QuickPick[] = [
  {
    label: 'Frontier Watcher',
    icon: 'brain',
    topics: ['Claude (Anthropic)', 'GPT & ChatGPT (OpenAI)', 'Gemini (Google DeepMind)', 'Grok (xAI)', 'New & Emerging Models']
  },
  {
    label: 'Open Source',
    icon: 'globe',
    topics: ['Llama (Meta AI)', 'DeepSeek', 'Qwen (Alibaba)', 'Mistral', 'Pricing & Availability']
  },
  {
    label: 'Agent Builder',
    icon: 'bot',
    topics: ['Claude Code & CLI Agents', 'Cursor, Aider & IDE Agents', 'Devin & Autonomous Coders', 'Browser & Computer-Use Agents', 'Agent Frameworks & SDKs']
  },
  {
    label: 'Capability Tracker',
    icon: 'rocket',
    topics: ['Reasoning & Context', 'Multimodal (Vision, Audio, Video)', 'Benchmarks & Evaluations', 'Pricing & Availability', 'New & Emerging Models']
  }
]

const selectedTopics = ref<string[]>([])

const applyPreset = (topics: string[]) => {
  topics.forEach(topic => {
    if (!selectedTopics.value.includes(topic)) selectedTopics.value.push(topic)
  })
}

const authStore = useAuthStore()
const userStore = useUserStore()
const router = useRouter()
const { success, error } = useToast()
const expandedDomains = ref<Set<string>>(new Set())
const expandedSubcategories = ref<Set<string>>(new Set())
const saving = ref(false)

const toggleDomain = (id: string) => {
  if (expandedDomains.value.has(id)) expandedDomains.value.delete(id)
  else expandedDomains.value.add(id)
}

const toggleSubcategory = (id: string) => {
  if (expandedSubcategories.value.has(id)) expandedSubcategories.value.delete(id)
  else expandedSubcategories.value.add(id)
}

const toggleTopic = (topic: string) => {
  const index = selectedTopics.value.indexOf(topic)
  if (index === -1) {
    selectedTopics.value.push(topic)
  } else {
    selectedTopics.value.splice(index, 1)
  }
}

const getDomainSelectedCount = (domain: TopicL1) =>
  domain.subcategories.reduce((acc, sub) => acc + sub.topics.filter(t => selectedTopics.value.includes(t)).length, 0)

const getSubcategorySelectedCount = (sub: TopicL2) =>
  sub.topics.filter(t => selectedTopics.value.includes(t)).length

const handleLogout = async () => {
  await authStore.logout()
  router.push({ name: 'home' })
}

const handleSave = async () => {
  if (selectedTopics.value.length < 3) return
  saving.value = true
  try {
    // Capture the user's timezone up front so their first digest lands at 08:00
    // local (the send-gate defaults an unset timezone to 08:00 UTC otherwise).
    await userStore.updatePreferences(selectedTopics.value, {
      timezone: detectBrowserTimezone(),
      deliveryHour: 8,
    })
    success('Beats saved! Your first issue is on its way.')
    router.push({ name: 'archive' })
  } catch (err: unknown) {
    error(getApiErrorMessage(err, 'Failed to save preferences. Please try again.'))
  } finally {
    saving.value = false
  }
}
</script>
