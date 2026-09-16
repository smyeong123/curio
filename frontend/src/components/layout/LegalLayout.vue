<template>
  <div class="min-h-screen text-[color:var(--ink)]" style="background-color: var(--paper)">
    <!-- ── Masthead ─────────────────────────────────────────────────── -->
    <header class="border-b border-[color:var(--rule)]">
      <div class="mx-auto flex w-full max-w-[820px] items-center justify-between gap-6 px-6 py-5">
        <router-link to="/" class="flex items-baseline gap-3">
          <span class="display-headline text-[28px] leading-none">Curio</span>
          <span class="kicker hidden sm:inline">{{ t('common.app.tagline') }}</span>
        </router-link>
        <div class="flex items-center gap-4">
          <LanguageToggle class="text-[12px]" />
          <router-link
            to="/"
            class="text-[12px] font-mono-curio uppercase tracking-[0.14em] text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors"
          >
            ← {{ t('legal.layout.backHome') }}
          </router-link>
        </div>
      </div>
    </header>

    <!-- ── Title + body ─────────────────────────────────────────────── -->
    <main class="mx-auto w-full max-w-[820px] px-6 py-12 md:py-16">
      <p class="kicker kicker-signal mb-4">{{ copy.kicker }}</p>
      <h1 class="display-headline text-[40px] md:text-[52px] leading-[0.95] mb-3">{{ copy.title }}</h1>
      <p v-if="copy.updated" class="font-mono-curio text-[12px] uppercase tracking-[0.14em] text-[color:var(--mute)] mb-10">
        {{ t('legal.layout.lastUpdated', { date: copy.updated }) }}
      </p>
      <div class="rule-strong mb-10"></div>

      <div class="legal-body font-body-curio text-[15.5px] leading-[1.72] text-[color:var(--ink-soft)]">
        <p><RichText :segments="copy.intro" /></p>
        <template v-for="(section, i) in copy.sections" :key="i">
          <h2>{{ section.heading }}</h2>
          <p v-for="(paragraph, j) in section.paragraphs" :key="j"><RichText :segments="paragraph" /></p>
          <ul v-if="section.bullets.length">
            <li v-for="(bullet, j) in section.bullets" :key="j"><RichText :segments="bullet" /></li>
          </ul>
        </template>
        <p v-if="copy.notice" class="mt-10 border-t border-[color:var(--rule)] pt-6 text-[13px] text-[color:var(--mute)]">
          {{ copy.notice }}
        </p>
      </div>
    </main>

    <!-- ── Footer ───────────────────────────────────────────────────── -->
    <footer class="border-t border-[color:var(--rule)]">
      <div
        class="mx-auto flex w-full max-w-[820px] flex-wrap items-center gap-x-6 gap-y-2 px-6 py-8 text-[12px] font-mono-curio uppercase tracking-[0.14em] text-[color:var(--mute)]"
      >
        <router-link to="/privacy" class="hover:text-[color:var(--ink)]">{{ t('legal.nav.privacy') }}</router-link>
        <router-link to="/terms" class="hover:text-[color:var(--ink)]">{{ t('legal.nav.terms') }}</router-link>
        <router-link to="/contact" class="hover:text-[color:var(--ink)]">{{ t('legal.nav.contact') }}</router-link>
        <a :href="`mailto:${CONTACT_EMAIL}`" class="normal-case tracking-normal hover:text-[color:var(--ink)]">{{ CONTACT_EMAIL }}</a>
        <span>{{ t('legal.layout.copyright') }}</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, h, type FunctionalComponent } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import LanguageToggle from '@/components/ui/LanguageToggle.vue'

/**
 * Chrome + prose renderer for the legal pages. All copy lives in the `legal`
 * catalog under `legal.<page>` as
 *   { kicker, title, updated?, intro, sections, notice? }
 * where each section is { heading, paragraphs?, bullets? }. Both editions
 * share this one template: body lines carry two markdown-style inline marks —
 * `**strong**` and `[text](href)` (a `/path` href renders as <router-link>,
 * anything else, e.g. mailto:, as <a>) — which are parsed into segments and
 * rendered as real elements, never via v-html. The contact address is
 * interpolated as `{email}` so no `@` sits in the catalogs (vue-i18n would
 * read it as a linked message). `notice` is the Korean edition's
 * "translation of the English original" footnote; it is empty in `en` and
 * skipped when empty.
 */
const props = defineProps<{ page: 'privacy' | 'terms' | 'contact' }>()

const CONTACT_EMAIL = 'sangmyeonglee123@gmail.com'

type RawSection = { heading: string; paragraphs?: string[]; bullets?: string[] }
type RawCopy = {
  kicker: string
  title: string
  updated?: string
  intro: string
  sections: RawSection[]
  notice?: string
}

type Segment =
  | { kind: 'text'; text: string }
  | { kind: 'strong'; text: string }
  | { kind: 'link'; text: string; href: string }

const INLINE_MARK = /\*\*(.+?)\*\*|\[([^\]]+)\]\(([^)\s]+)\)/g

function parseInline(line: string): Segment[] {
  const segments: Segment[] = []
  let cursor = 0
  for (const match of line.matchAll(INLINE_MARK)) {
    const start = match.index ?? 0
    if (start > cursor) segments.push({ kind: 'text', text: line.slice(cursor, start) })
    if (match[1] !== undefined) segments.push({ kind: 'strong', text: match[1] })
    else segments.push({ kind: 'link', text: match[2] ?? '', href: match[3] ?? '' })
    cursor = start + match[0].length
  }
  if (cursor < line.length) segments.push({ kind: 'text', text: line.slice(cursor) })
  return segments
}

const { t, tm, rt } = useI18n()

// `tm` hands back the raw catalog subtree; `rt` renders each leaf (and fills
// `{email}`), then the inline marks are split into segments for RichText.
const line = (raw: string) => parseInline(rt(raw, { email: CONTACT_EMAIL }))

const copy = computed(() => {
  const raw = tm(`legal.${props.page}`) as RawCopy
  return {
    kicker: rt(raw.kicker),
    title: rt(raw.title),
    updated: raw.updated ? rt(raw.updated) : '',
    intro: line(raw.intro),
    sections: raw.sections.map((section) => ({
      heading: rt(section.heading),
      paragraphs: (section.paragraphs ?? []).map(line),
      bullets: (section.bullets ?? []).map(line),
    })),
    notice: raw.notice ? rt(raw.notice) : '',
  }
})

const RichText: FunctionalComponent<{ segments: Segment[] }> = ({ segments }) =>
  segments.map((segment) => {
    if (segment.kind === 'strong') return h('strong', segment.text)
    if (segment.kind === 'link') {
      return segment.href.startsWith('/')
        ? h(RouterLink, { to: segment.href }, () => segment.text)
        : h('a', { href: segment.href }, segment.text)
    }
    return segment.text
  })
</script>

<style scoped>
/* Style the slotted markdown-ish content consistently with the editorial theme. */
.legal-body :deep(h2) {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 1.5rem;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--ink);
  margin: 2.5rem 0 0.75rem;
}
.legal-body :deep(h2:first-child) {
  margin-top: 0;
}
.legal-body :deep(h3) {
  font-family: var(--font-mono);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-size: 0.8125rem;
  color: var(--ink);
  margin: 1.75rem 0 0.5rem;
}
.legal-body :deep(p) {
  margin-bottom: 1rem;
}
.legal-body :deep(ul) {
  margin: 0 0 1.25rem;
  padding-left: 1.1rem;
  list-style: none;
}
.legal-body :deep(li) {
  position: relative;
  margin-bottom: 0.55rem;
  /* Must clear the em-dash marker below (~0.95rem wide at this size) plus a
     visible gap — 0.9rem let the dash touch the first word. */
  padding-left: 1.6rem;
}
.legal-body :deep(li)::before {
  content: '—';
  position: absolute;
  left: 0;
  color: var(--signal-deep);
}
.legal-body :deep(a) {
  color: var(--signal-deep);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.legal-body :deep(a:hover) {
  color: var(--signal);
}
.legal-body :deep(strong) {
  color: var(--ink);
  font-weight: 600;
}
</style>
