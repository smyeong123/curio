<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)]">
    <!-- ── Top metadata strip ───────────────────────────────────────── -->
    <div class="bg-ink text-[color:var(--paper)] py-2">
      <div class="mx-auto flex w-full max-w-[1280px] items-center justify-between gap-6 px-6 text-[11px] uppercase tracking-[0.18em] font-mono-curio">
        <div class="flex items-center gap-5">
          <span class="hidden sm:inline">{{ t('home.strip.vol') }} <span class="num-tab">{{ volumeLabel }}</span></span>
          <span class="opacity-60 hidden md:inline">·</span>
          <span class="hidden md:inline">{{ today }}</span>
          <span class="opacity-60 hidden lg:inline">·</span>
          <span class="hidden lg:inline">{{ t('home.strip.issue') }}</span>
        </div>
        <div class="flex items-center gap-4">
          <span class="num-tab opacity-70">{{ liveClock }}</span>
          <span class="opacity-60">·</span>
          <a class="opacity-80 hover:opacity-100 hover:text-[color:var(--signal)]" href="#what">{{ t('home.strip.readIt') }}</a>
        </div>
      </div>
    </div>

    <!-- ── Marquee: "Today on Curio" ───────────────────────────────── -->
    <div class="border-b border-[color:var(--rule)] bg-paper-deep overflow-hidden">
      <div class="mx-auto flex w-full max-w-[1280px] items-center gap-4 px-6 py-2">
        <span class="flex items-center gap-2 flex-shrink-0">
          <span class="live-dot" aria-hidden="true"></span>
          <span class="kicker kicker-signal">{{ t('home.ticker.breaking') }}</span>
        </span>
        <div class="overflow-hidden flex-1 ticker-pause">
          <div class="ticker-track text-[13px] font-body-curio">
            <span v-for="(line, i) in tickerLines" :key="`a-${i}`">— {{ line }}</span>
            <span v-for="(line, i) in tickerLines" :key="`b-${i}`" aria-hidden="true">— {{ line }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ── Masthead ─────────────────────────────────────────────────── -->
    <header
      class="sticky top-0 z-30 backdrop-blur border-b border-[color:var(--rule)]"
      style="background-color: color-mix(in srgb, var(--paper) 92%, transparent)"
    >
      <div class="mx-auto flex w-full max-w-[1280px] items-center justify-between gap-3 sm:gap-6 px-4 sm:px-6 py-5">
        <router-link to="/" class="group flex items-baseline gap-3">
          <span class="display-headline text-[34px] leading-none">Curio</span>
          <span class="kicker hidden sm:inline">{{ t('common.app.tagline') }}</span>
        </router-link>
        <nav class="flex items-center gap-1 sm:gap-2">
          <LanguageSelect class="px-1 sm:px-3 py-2 text-[13px]" />
          <router-link
            to="/login"
            class="whitespace-nowrap px-1 sm:px-3 py-2 text-[13px] font-mono-curio uppercase tracking-[0.14em] text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors"
          >
            {{ t('home.masthead.signIn') }}
          </router-link>
          <router-link to="/register" class="btn-editorial">
            {{ t('home.masthead.subscribe') }}
            <span aria-hidden="true">→</span>
          </router-link>
        </nav>
      </div>
    </header>

    <!-- ── Lede ─────────────────────────────────────────────────────── -->
    <section class="mx-auto w-full max-w-[1280px] px-6 pt-14 pb-20">
      <div class="grid grid-cols-12 gap-8 items-end">
        <!-- Left: kicker + huge serif headline -->
        <div class="col-span-12 lg:col-span-8">
          <div class="rise rise-d-1 flex items-center gap-3 mb-7">
            <span class="kicker kicker-signal">{{ t('home.hero.number') }}</span>
            <span class="h-px w-10 bg-[color:var(--rule)]"></span>
            <span class="kicker">{{ t('home.hero.kicker') }}</span>
          </div>

          <i18n-t scope="global" keypath="home.hero.headline" tag="h1" class="rise rise-d-2 display-headline text-[clamp(56px,9vw,148px)] mb-8">
            <template #curated>
              <em class="italic-display hl-sweep hl-sweep-1"><span class="hl-sweep-band" aria-hidden="true"></span><span class="hl-sweep-text">{{ t('home.hero.curated') }}</span></em>
            </template>
            <template #coffee>
              <em class="italic-display hl-sweep hl-sweep-2"><span class="hl-sweep-band" aria-hidden="true"></span><span class="hl-sweep-text">{{ t('home.hero.coffee') }}</span></em>
            </template>
          </i18n-t>

          <p class="rise rise-d-3 max-w-[58ch] font-body-curio text-[18px] leading-[1.55] text-[color:var(--ink-soft)] mb-10">
            {{ t('home.hero.lede') }}
          </p>

          <div class="rise rise-d-4 flex flex-wrap items-center gap-4 mb-12">
            <router-link to="/register" class="btn-editorial">
              {{ t('home.hero.cta') }}
              <span aria-hidden="true">→</span>
            </router-link>
            <router-link to="/login" class="btn-editorial-ghost">{{ t('home.hero.signIn') }}</router-link>
            <span class="kicker pl-2">{{ t('home.hero.fineprint') }}</span>
          </div>

          <!-- Stat row -->
          <div
            ref="statRow"
            class="rise rise-d-5 grid grid-cols-3 border-t border-b border-[color:var(--rule)] divide-x divide-[color:var(--rule)]"
          >
            <div class="px-5 py-5">
              <div class="deco-num text-[44px] leading-none">{{ statTopics }}</div>
              <p class="kicker mt-3">{{ t('home.hero.stats.topics') }}</p>
            </div>
            <div class="px-5 py-5">
              <div class="deco-num text-[44px] leading-none">{{ statMinutes }}<span class="text-[color:var(--signal-deep)]">·</span>{{ t('home.hero.stats.min') }}</div>
              <p class="kicker mt-3">{{ t('home.hero.stats.minutes') }}</p>
            </div>
            <div class="px-5 py-5">
              <div class="deco-num text-[44px] leading-none">{{ statQuiz }}<span class="text-[color:var(--mute)]">/</span>5</div>
              <p class="kicker mt-3">{{ t('home.hero.stats.quiz') }}</p>
            </div>
          </div>
        </div>

        <!-- Right: "tomorrow's front page" preview card -->
        <aside class="col-span-12 lg:col-span-4 rise rise-d-3">
          <div class="card-paper card-lift p-6 relative">
            <!-- Edition stamp -->
            <div class="stamp-tilt absolute -top-3 -right-3 bg-signal text-[color:var(--paper)] px-3 py-1.5 font-mono-curio text-[10px] font-bold uppercase tracking-[0.18em]">
              {{ t('home.sample.stamp') }}
            </div>

            <p class="kicker mb-4">{{ t('home.sample.kicker', { vol: nextVolumeLabel }) }}</p>
            <i18n-t scope="global" keypath="home.sample.headline" tag="h3" class="display-headline text-[26px] leading-[1.05] mb-2">
              <template #asleep><em class="italic-display">{{ t('home.sample.asleep') }}</em></template>
            </i18n-t>
            <div class="rule mb-4 mt-3"></div>

            <div class="space-y-5">
              <article v-for="(story, idx) in sampleStories" :key="idx">
                <div class="flex items-baseline gap-2 mb-1">
                  <span class="num-tab text-[10px] text-[color:var(--mute)]">{{ String(idx + 1).padStart(2, '0') }}</span>
                  <span class="kicker">{{ story.topic }}</span>
                </div>
                <p class="font-display text-[16px] leading-[1.25] font-medium text-[color:var(--ink)]">
                  {{ story.title }}
                </p>
                <p class="font-body-curio text-[12.5px] text-[color:var(--mute)] leading-snug mt-1">{{ story.dek }}</p>
                <div v-if="idx < sampleStories.length - 1" class="rule mt-4"></div>
              </article>
            </div>

            <div class="mt-6 pt-4 border-t-2 border-[color:var(--rule)] flex items-baseline justify-between">
              <span class="kicker">{{ t('home.sample.then') }}</span>
              <span class="num-tab text-[12px] font-bold text-[color:var(--signal-deep)]">{{ t('home.sample.quizLink') }}</span>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <!-- ── How it works (newsroom three-up) ─────────────────────────── -->
    <section id="what" class="border-t border-[color:var(--rule)] bg-paper-deep">
      <div class="mx-auto w-full max-w-[1280px] px-6 py-20">
        <div class="grid grid-cols-12 gap-6 mb-12 items-end">
          <div class="col-span-12 md:col-span-7" data-reveal>
            <p class="kicker kicker-signal mb-3">{{ t('home.method.kicker') }}</p>
            <i18n-t scope="global" keypath="home.method.headline" tag="h2" class="display-headline text-[clamp(40px,5.5vw,72px)] leading-[0.98]">
              <template #tail><em class="italic-display">{{ t('home.method.tail') }}</em></template>
            </i18n-t>
            <div class="rule-strong rule-draw mt-6 max-w-[240px]"></div>
          </div>
          <p
            class="col-span-12 md:col-span-5 font-body-curio text-[15px] text-[color:var(--ink-soft)] leading-relaxed"
            data-reveal
            style="--reveal-delay: 0.12s"
          >
            {{ t('home.method.body') }}
          </p>
        </div>

        <ol class="grid grid-cols-1 md:grid-cols-3 gap-0 border-t-2 border-[color:var(--rule)]">
          <li
            v-for="(step, i) in steps"
            :key="i"
            :class="[
              'p-7 border-b-2 border-[color:var(--rule)]',
              i < 2 ? 'md:border-r border-[color:var(--rule)]' : ''
            ]"
            data-reveal
            :style="{ '--reveal-delay': `${i * 0.1}s` }"
          >
            <div class="flex items-baseline justify-between mb-6">
              <span class="deco-num text-[64px] leading-none text-[color:var(--signal-deep)]">{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="kicker">{{ t('home.method.chapter') }}</span>
            </div>
            <h3 class="font-display text-[24px] leading-tight font-semibold mb-2">{{ step.title }}</h3>
            <p class="font-body-curio text-[14.5px] text-[color:var(--ink-soft)] leading-relaxed">{{ step.body }}</p>
          </li>
        </ol>
      </div>
    </section>

    <!-- ── Topics on the beat ─────────────────────────────────────── -->
    <section class="border-t border-[color:var(--rule)]">
      <div class="mx-auto w-full max-w-[1280px] px-6 py-20">
        <div class="grid grid-cols-12 gap-8 items-start">
          <div class="col-span-12 md:col-span-4" data-reveal>
            <p class="kicker kicker-signal mb-3">{{ t('home.beat.kicker') }}</p>
            <i18n-t scope="global" keypath="home.beat.headline" tag="h2" class="display-headline text-[clamp(36px,5vw,64px)] leading-[1]">
              <template #cover><em class="italic-display">{{ t('home.beat.cover') }}</em></template>
            </i18n-t>
            <div class="rule-strong rule-draw mt-4 mb-5 max-w-[160px]"></div>
            <p class="font-body-curio text-[14.5px] text-[color:var(--ink-soft)] leading-relaxed mb-6">
              {{ t('home.beat.body') }}
            </p>
            <router-link to="/register" class="ink-link font-mono-curio text-[12.5px] uppercase tracking-[0.14em]">
              {{ t('home.beat.pickYours') }}
            </router-link>
          </div>

          <ul class="col-span-12 md:col-span-8 grid grid-cols-2 sm:grid-cols-3 gap-x-8 gap-y-4">
            <li
              v-for="(topic, i) in beatTopics"
              :key="topic"
              class="beat-row flex items-baseline gap-3 border-b border-[color:var(--rule)]/30 pb-3"
              data-reveal
              :style="{ '--reveal-delay': `${(i % 6) * 0.05}s` }"
            >
              <span class="num-tab text-[11px] text-[color:var(--mute)]">{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="font-display text-[17px] leading-tight text-[color:var(--ink)]">{{ topicLabel(topic) }}</span>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- ── Pull quote ─────────────────────────────────────────────── -->
    <section class="border-t-2 border-b-2 border-[color:var(--rule)] bg-ink text-[color:var(--paper)]">
      <div class="mx-auto w-full max-w-[1100px] px-6 py-24 text-center" data-reveal>
        <p class="kicker mb-6" style="color: var(--signal);">{{ t('home.quote.kicker') }}</p>
        <i18n-t scope="global" keypath="home.quote.text" tag="blockquote" class="display-headline text-[clamp(32px,4.5vw,56px)] leading-[1.05]">
          <template #br><br class="hidden md:inline" /></template>
          <template #minutes><em class="italic-display" style="color: var(--signal);">{{ t('home.quote.minutes') }}</em></template>
        </i18n-t>
        <p class="kicker mt-7" style="color: var(--paper);">{{ t('home.quote.attribution') }}</p>
      </div>
    </section>

    <!-- ── Subscribe CTA ───────────────────────────────────────────── -->
    <section class="border-b-2 border-[color:var(--rule)]">
      <div class="mx-auto w-full max-w-[1280px] px-6 py-24">
        <div class="grid grid-cols-12 gap-8 items-end">
          <div class="col-span-12 md:col-span-7" data-reveal>
            <p class="kicker kicker-signal mb-4">{{ t('home.subscribe.kicker') }}</p>
            <i18n-t scope="global" keypath="home.subscribe.headline" tag="h2" class="display-headline text-[clamp(48px,7vw,108px)] leading-[0.95]">
              <template #edition><em class="italic-display">{{ t('home.subscribe.edition') }}</em></template>
            </i18n-t>
          </div>
          <div class="col-span-12 md:col-span-5" data-reveal style="--reveal-delay: 0.15s">
            <p class="font-body-curio text-[16px] text-[color:var(--ink-soft)] mb-6 leading-relaxed">
              {{ t('home.subscribe.body') }}
            </p>
            <div class="flex flex-wrap items-center gap-3">
              <router-link to="/register" class="btn-editorial">
                {{ t('home.subscribe.startFree') }}
                <span aria-hidden="true">→</span>
              </router-link>
              <router-link to="/login" class="btn-editorial-ghost">{{ t('home.subscribe.signIn') }}</router-link>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── Colophon ────────────────────────────────────────────────── -->
    <footer style="background-color: var(--paper)">
      <div class="mx-auto flex w-full max-w-[1280px] flex-col gap-6 px-6 py-10 md:flex-row md:items-end md:justify-between">
        <div>
          <p class="display-headline text-[34px] leading-none mb-1">Curio</p>
          <p class="kicker">{{ t('home.footer.tagline', { vol: volumeLabel }) }}</p>
        </div>
        <div class="flex flex-wrap items-center gap-x-6 gap-y-2 text-[12px] font-mono-curio uppercase tracking-[0.14em] text-[color:var(--mute)]">
          <LanguageSelect />
          <router-link to="/login" class="hover:text-[color:var(--ink)]">{{ t('home.footer.signIn') }}</router-link>
          <router-link to="/register" class="hover:text-[color:var(--ink)]">{{ t('home.footer.subscribe') }}</router-link>
          <router-link to="/privacy" class="hover:text-[color:var(--ink)]">{{ t('home.footer.privacy') }}</router-link>
          <router-link to="/terms" class="hover:text-[color:var(--ink)]">{{ t('home.footer.terms') }}</router-link>
          <router-link to="/contact" class="hover:text-[color:var(--ink)]">{{ t('home.footer.contact') }}</router-link>
          <a href="mailto:sangmyeonglee123@gmail.com" class="normal-case tracking-normal hover:text-[color:var(--ink)]">sangmyeonglee123@gmail.com</a>
          <span>{{ t('home.footer.copyright') }}</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFormat } from '@/composables/useFormat'
import { useTopicLabels } from '@/composables/useTopicLabels'
import { ALL_TOPICS } from '@/data/topics'
import LanguageSelect from '@/components/ui/LanguageSelect.vue'

const { t, tm, rt } = useI18n()
const { formatDate } = useFormat()
const { topicLabel } = useTopicLabels()

const today = computed(() => formatDate(new Date(), 'full').toUpperCase())

// Volume = the daily edition number, counting from the 1 Aug 2026 production
// launch (Vol. 001 on launch day, +1 each day after). Clamped to ≥1 so the
// pre-launch masthead never shows 000 or a negative number. The sample card
// previews tomorrow's edition, so it uses vol + 1.
const VOLUME_EPOCH = Date.UTC(2026, 7, 1) // 1 Aug 2026, 00:00 UTC (month is 0-indexed)
const volume = computed(() =>
  Math.max(1, Math.floor((Date.now() - VOLUME_EPOCH) / 86_400_000) + 1)
)
const volumeLabel = computed(() => String(volume.value).padStart(3, '0'))
const nextVolumeLabel = computed(() => String(volume.value + 1).padStart(3, '0'))

const liveClock = ref(formatClock())
function formatClock() {
  // The strip labels the time "UTC", so it is formatted in UTC, not local time.
  return new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: 'UTC' }) + ' UTC'
}
let clockTimer: ReturnType<typeof setInterval> | null = null

// ── Scroll reveals + stat count-up ─────────────────────────────────────
// Elements marked [data-reveal] fade/rise in when they enter the viewport;
// the stat numerals roll up from 0 the first time the stat row is seen.
// Both collapse to their final state under prefers-reduced-motion.
const statRow = ref<HTMLElement | null>(null)
const statTopics = ref(0)
const statMinutes = ref(0)
const statQuiz = ref(0)
const STAT_TARGETS = { topics: ALL_TOPICS.length, minutes: 5, quiz: 5 } as const

let revealObserver: IntersectionObserver | null = null
let statObserver: IntersectionObserver | null = null
let countUpRaf: number | null = null

function setStatsFinal() {
  statTopics.value = STAT_TARGETS.topics
  statMinutes.value = STAT_TARGETS.minutes
  statQuiz.value = STAT_TARGETS.quiz
}

function runCountUp() {
  const duration = 900
  const start = performance.now()
  const tick = (now: number) => {
    const t = Math.min(1, (now - start) / duration)
    const eased = 1 - Math.pow(1 - t, 3)
    statTopics.value = Math.round(STAT_TARGETS.topics * eased)
    statMinutes.value = Math.round(STAT_TARGETS.minutes * eased)
    statQuiz.value = Math.round(STAT_TARGETS.quiz * eased)
    if (t < 1) countUpRaf = requestAnimationFrame(tick)
  }
  countUpRaf = requestAnimationFrame(tick)
}

onMounted(() => {
  clockTimer = setInterval(() => (liveClock.value = formatClock()), 30_000)

  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const revealTargets = Array.from(document.querySelectorAll<HTMLElement>('[data-reveal]'))

  if (reducedMotion || typeof IntersectionObserver === 'undefined') {
    revealTargets.forEach((el) => el.classList.add('is-inview'))
    setStatsFinal()
    return
  }

  // Toggle (not one-shot): sections hide when they leave the viewport and
  // replay their reveal on re-entry, in both scroll directions. Reveal on
  // the first visible pixel and hide only at zero visible pixels — anything
  // stricter leaves partially-visible content sitting blank at the viewport
  // edge when the user stops scrolling.
  revealObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        entry.target.classList.toggle('is-inview', entry.isIntersecting)
      }
    },
    { threshold: 0 }
  )
  revealTargets.forEach((el) => revealObserver!.observe(el))

  if (statRow.value) {
    statObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          statObserver?.disconnect()
          statObserver = null
          runCountUp()
        }
      },
      { threshold: 0.4 }
    )
    statObserver.observe(statRow.value)
  } else {
    setStatsFinal()
  }
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  revealObserver?.disconnect()
  statObserver?.disconnect()
  if (countUpRaf !== null) cancelAnimationFrame(countUpRaf)
})

// Editorial copy lists live in the catalogs (home.json) so both editions can
// carry their own lines; `tm` hands back the raw array, `rt` renders each entry.
const tickerLines = computed(() => (tm('home.ticker.lines') as string[]).map((line) => rt(line)))

type SampleStory = { topic: string; title: string; dek: string }
const sampleStories = computed(() =>
  (tm('home.sample.stories') as SampleStory[]).map((s) => ({ topic: rt(s.topic), title: rt(s.title), dek: rt(s.dek) }))
)

type Step = { title: string; body: string }
const steps = computed(() =>
  (tm('home.method.steps') as Step[]).map((s) => ({ title: rt(s.title), body: rt(s.body) }))
)

// Every beat a reader can subscribe to, in catalogue order; rendered through topicLabel().
const beatTopics = ALL_TOPICS
</script>
