<template>
  <div class="min-h-screen bg-paper text-[color:var(--ink)]">
    <!-- ── Masthead: one row of ruled cells (sticky, scroll-aware) ────── -->
    <header
      ref="navBar"
      class="nav-glass nav-ruled sticky top-0 z-30"
      :class="{ 'is-scrolled': navScrolled, 'is-hidden': navHidden }"
      :style="{ '--nav-progress': navProgress }"
    >
      <span class="nav-progress" aria-hidden="true"></span>
      <div class="flex font-mono-curio text-[12px] uppercase tracking-[0.06em] sm:flex-wrap">
        <router-link to="/" class="flex min-w-0 flex-[1_1_auto] items-center gap-3 border-r border-[color:var(--hairline)] px-3 py-3 sm:flex-[1_1_160px] sm:px-6">
          <span class="wordmark display-headline text-[24px] leading-none normal-case tracking-[-0.04em]">Curio</span>
          <span class="kicker hidden whitespace-nowrap min-[1760px]:inline">{{ t('common.app.tagline') }}</span>
        </router-link>
        <span class="hidden flex-[1_1_auto] items-center whitespace-nowrap border-r border-[color:var(--hairline)] px-6 lg:flex">
          {{ t('home.strip.vol') }}&nbsp;<span class="num-tab">{{ volumeLabel }}</span>&nbsp;—&nbsp;{{ today }}
        </span>
        <span class="hidden flex-[1_1_auto] items-center gap-2 whitespace-nowrap border-r border-[color:var(--hairline)] px-6 xl:flex">
          <span class="live-dot" aria-hidden="true"></span>{{ t('home.strip.issue') }}
        </span>
        <nav class="flex flex-[0_0_auto] items-center justify-end gap-0.5 px-1.5 sm:flex-[1_1_300px] sm:gap-2 sm:px-4">
          <a class="hidden whitespace-nowrap px-2 py-3 text-[color:var(--ink-soft)] hover:text-[color:var(--ink)] 2xl:inline" href="#what">
            <span class="num-tab">{{ liveClock }}</span> · {{ t('home.strip.readIt') }}
          </a>
          <LanguageSelect class="px-1 py-2 text-[12px] sm:px-2" />
          <router-link
            to="/login"
            class="whitespace-nowrap px-1 py-3 text-[color:var(--ink-soft)] transition-colors hover:text-[color:var(--ink)] sm:px-2"
          >
            {{ t('home.masthead.signIn') }}
          </router-link>
          <router-link to="/register" class="btn-editorial my-1.5 min-h-[40px] gap-2 pl-3 text-[13px] sm:gap-4 sm:pl-5">
            {{ t('home.masthead.subscribe') }}
            <span aria-hidden="true">→</span>
          </router-link>
        </nav>
      </div>
    </header>

    <!-- ── Ticker row ─────────────────────────────────────────────────── -->
    <div class="overflow-hidden border-b border-[color:var(--rule)]">
      <div class="flex w-full items-center gap-4 px-4 py-2.5 sm:px-6">
        <span class="flex flex-shrink-0 items-center gap-2">
          <span class="live-dot" aria-hidden="true"></span>
          <span class="kicker kicker-signal">{{ t('home.ticker.breaking') }}</span>
        </span>
        <div class="ticker-pause flex-1 overflow-hidden border-l border-[color:var(--hairline)] pl-4">
          <div class="ticker-track font-body-curio text-[13px]">
            <span v-for="(line, i) in tickerLines" :key="`a-${i}`">— {{ line }}</span>
            <span v-for="(line, i) in tickerLines" :key="`b-${i}`" aria-hidden="true">— {{ line }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ── Hero: two columns split by an ink rule ───────────────────────── -->
    <section id="top" class="grid border-b border-[color:var(--rule)] lg:grid-cols-[minmax(0,1fr)_minmax(380px,34%)]">
      <div class="flex min-w-0 flex-col justify-between gap-12 px-4 pb-10 pt-12 sm:px-6 lg:border-r lg:border-[color:var(--rule)] lg:pt-14">
        <div class="rise rise-d-1 flex items-center gap-3">
          <span class="kicker kicker-signal shrink-0 whitespace-nowrap">§ {{ t('home.hero.number') }}</span>
          <span class="h-px w-10 shrink-0 bg-[color:var(--rule)]"></span>
          <span class="kicker">{{ t('home.hero.kicker') }}</span>
        </div>

        <div class="flex flex-col gap-8">
          <i18n-t scope="global" keypath="home.hero.headline" tag="h1" class="rise-mask rise-d-2 display-headline hero-display">
            <template #curated><em class="italic-display">{{ t('home.hero.curated') }}</em></template>
            <template #coffee><em class="italic-display">{{ t('home.hero.coffee') }}</em></template>
          </i18n-t>

          <p class="rise rise-d-3 max-w-[46ch] font-body-curio text-[clamp(18px,1.6vw,22px)] font-medium leading-[1.4] tracking-[-0.015em] text-[color:var(--ink-soft)]">
            {{ t('home.hero.lede') }}
          </p>

          <div class="rise rise-d-4 flex flex-wrap items-center gap-3">
            <router-link to="/register" class="btn-editorial min-h-[56px] text-[16px]">
              {{ t('home.hero.cta') }}
              <span aria-hidden="true">→</span>
            </router-link>
            <router-link to="/login" class="btn-editorial-ghost min-h-[56px] text-[16px]">{{ t('home.hero.signIn') }}</router-link>
            <span class="kicker pl-1">{{ t('home.hero.fineprint') }}</span>
          </div>
        </div>

        <!-- Fact cells -->
        <div
          ref="statRow"
          class="rise rise-d-5 grid grid-cols-3 border-t border-[color:var(--rule)]"
        >
          <div class="pr-3 pt-4">
            <div class="deco-num text-[clamp(32px,3.4vw,48px)] leading-none">{{ statTopics }}</div>
            <p class="kicker mt-3">{{ t('home.hero.stats.topics') }}</p>
          </div>
          <div class="border-l border-[color:var(--hairline)] px-3 pt-4 sm:px-4">
            <div class="deco-num text-[clamp(32px,3.4vw,48px)] leading-none">{{ statMinutes }}<span class="text-[color:var(--signal)]">·</span>{{ t('home.hero.stats.min') }}</div>
            <p class="kicker mt-3">{{ t('home.hero.stats.minutes') }}</p>
          </div>
          <div class="border-l border-[color:var(--hairline)] pl-3 pt-4 sm:pl-4">
            <div class="deco-num text-[clamp(32px,3.4vw,48px)] leading-none">{{ statQuiz }}<span class="text-[color:var(--mute)]">/</span>5</div>
            <p class="kicker mt-3">{{ t('home.hero.stats.quiz') }}</p>
          </div>
        </div>
      </div>

      <!-- Right: tomorrow's front page, on the dotted accent block -->
      <aside class="dot-block rise rise-d-3 flex min-w-0 flex-col justify-between gap-8 overflow-hidden px-6 py-8 sm:px-8 lg:py-10">
        <div class="flex items-start justify-between gap-4 font-mono-curio text-[12px] uppercase tracking-[0.06em]">
          <span>{{ t('home.sample.kicker', { vol: nextVolumeLabel }) }}</span>
          <span class="shrink-0 whitespace-nowrap bg-[color:var(--ink)] px-2 py-1 text-[color:var(--paper)]">{{ t('home.sample.stamp') }}</span>
        </div>

        <div ref="sampleCard" class="parallax flex flex-col gap-6">
          <i18n-t scope="global" keypath="home.sample.headline" tag="h3" class="display-headline text-[clamp(30px,2.6vw,40px)] leading-[1] sample-headline">
            <template #asleep><em class="italic-display">{{ t('home.sample.asleep') }}</em></template>
          </i18n-t>
          <ol class="border-t border-[color:var(--on-accent-rule)]">
            <li v-for="(story, idx) in sampleStories" :key="idx" class="border-b border-[color:var(--on-accent-rule)] py-4">
              <div class="mb-1.5 flex items-baseline gap-2 font-mono-curio text-[11px] uppercase tracking-[0.06em] opacity-85">
                <span class="num-tab">{{ String(idx + 1).padStart(2, '0') }}</span>
                <span>{{ story.topic }}</span>
              </div>
              <p class="font-display text-[17px] font-semibold leading-[1.2]">{{ story.title }}</p>
              <p class="mt-1 font-body-curio text-[13px] leading-snug opacity-85">{{ story.dek }}</p>
            </li>
          </ol>
        </div>

        <div class="flex min-h-[64px] items-center justify-between gap-4 bg-[color:var(--paper)] px-5 text-[color:var(--ink)]">
          <span class="font-mono-curio text-[12px] uppercase tracking-[0.06em]">{{ t('home.sample.then') }}</span>
          <span class="num-tab text-[13px] font-semibold text-[color:var(--signal-deep)]">{{ t('home.sample.quizLink') }}</span>
        </div>
      </aside>
    </section>

    <!-- ── How it works: header row + three ruled chapters ──────────────── -->
    <section id="what" class="border-b border-[color:var(--rule)]">
      <div class="grid gap-8 px-4 pb-12 pt-16 sm:px-6 md:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] md:items-end">
        <div data-reveal>
          <p class="kicker kicker-signal mb-4">§ {{ t('home.method.kicker') }}</p>
          <i18n-t scope="global" keypath="home.method.headline" tag="h2" class="reveal-mask display-headline text-[clamp(40px,5.4vw,80px)] leading-[0.95]">
            <template #tail><em class="italic-display">{{ t('home.method.tail') }}</em></template>
          </i18n-t>
        </div>
        <ScrollWords
          :text="t('home.method.body')"
          class="max-w-[40ch] font-body-curio text-[18px] leading-[1.45] text-[color:var(--ink-soft)]"
        />
      </div>

      <ol class="grid border-t border-[color:var(--rule)] md:grid-cols-3">
        <li
          v-for="(step, i) in steps"
          :key="i"
          :class="[
            'flex flex-col gap-8 px-4 py-8 sm:px-6',
            i > 0 ? 'border-t border-[color:var(--hairline)] md:border-l md:border-t-0' : ''
          ]"
          data-reveal
          :style="{ '--reveal-delay': `${i * 0.1}s` }"
        >
          <div class="flex items-start justify-between gap-4">
            <span class="num-outline text-[clamp(96px,9vw,150px)]" aria-hidden="true">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="kicker">{{ t('home.method.chapter') }} {{ String(i + 1).padStart(2, '0') }}</span>
          </div>
          <div>
            <h3 class="font-display mb-3 text-[clamp(24px,2.2vw,32px)] font-bold leading-[1.05] tracking-[-0.035em]">{{ step.title }}</h3>
            <p class="max-w-[38ch] font-body-curio text-[16px] leading-[1.5] text-[color:var(--ink-soft)]">{{ step.body }}</p>
          </div>
        </li>
      </ol>
    </section>

    <!-- ── Topics on the beat: the beats as one run of large text ───────── -->
    <section class="border-b border-[color:var(--rule)]">
      <div class="grid gap-8 px-4 pb-10 pt-16 sm:px-6 md:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] md:items-end" data-reveal>
        <div>
          <p class="kicker kicker-signal mb-4">§ {{ t('home.beat.kicker') }}</p>
          <i18n-t scope="global" keypath="home.beat.headline" tag="h2" class="reveal-mask display-headline text-[clamp(40px,5.4vw,80px)] leading-[0.95]">
            <template #cover><em class="italic-display">{{ t('home.beat.cover') }}</em></template>
          </i18n-t>
        </div>
        <div>
          <ScrollWords :text="t('home.beat.body')" class="mb-6 max-w-[40ch] font-body-curio text-[18px] leading-[1.45] text-[color:var(--ink-soft)]" />
          <router-link to="/register" class="ink-link font-mono-curio text-[12px] uppercase tracking-[0.06em]">
            {{ t('home.beat.pickYours') }}
          </router-link>
        </div>
      </div>

      <div class="flex items-center justify-between border-t border-[color:var(--hairline)] px-4 py-3 sm:px-6">
        <span class="kicker">{{ t('home.hero.stats.topics') }}</span>
        <span class="kicker num-tab">{{ beatTopics.length }}</span>
      </div>
      <ul class="beat-run px-4 pb-14 pt-2 sm:px-6">
        <li
          v-for="(topic, i) in beatTopics"
          :key="topic"
          class="beat-word"
          data-reveal
          :style="{ '--reveal-delay': `${(i % 6) * 0.04}s` }"
        >{{ topicLabel(topic) }}</li>
      </ul>
    </section>

    <!-- ── Pull quote: full-bleed ink ─────────────────────────────────── -->
    <section class="bg-ink text-[color:var(--paper)]">
      <div class="flex items-center justify-between gap-4 border-b border-[color:var(--on-ink-rule)] px-4 py-4 font-mono-curio text-[12px] uppercase tracking-[0.06em] sm:px-6">
        <span style="color: var(--signal);">§ {{ t('home.quote.kicker') }}</span>
        <span class="opacity-75">{{ t('home.quote.attribution') }}</span>
      </div>
      <div class="px-4 pb-24 pt-16 sm:px-6" data-reveal>
        <i18n-t scope="global" keypath="home.quote.text" tag="blockquote" class="reveal-mask display-headline max-w-[18ch] text-[clamp(40px,6vw,96px)] leading-[0.96]">
          <template #br><br class="hidden md:inline" /></template>
          <template #minutes><em class="italic-display" style="color: var(--signal);">{{ t('home.quote.minutes') }}</em></template>
        </i18n-t>
      </div>
    </section>

    <!-- ── Subscribe ──────────────────────────────────────────────────── -->
    <section class="border-b border-[color:var(--rule)]">
      <div class="grid gap-10 px-4 py-20 sm:px-6 md:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] md:items-end">
        <div data-reveal>
          <p class="kicker kicker-signal mb-4">§ {{ t('home.subscribe.kicker') }}</p>
          <i18n-t scope="global" keypath="home.subscribe.headline" tag="h2" class="reveal-mask display-headline text-[clamp(52px,7.4vw,124px)] leading-[0.9]">
            <template #edition><em class="italic-display">{{ t('home.subscribe.edition') }}</em></template>
          </i18n-t>
        </div>
        <div data-reveal style="--reveal-delay: 0.15s">
          <ScrollWords :text="t('home.subscribe.body')" class="mb-8 max-w-[36ch] font-body-curio text-[20px] font-medium leading-[1.4] tracking-[-0.015em] text-[color:var(--ink-soft)]" />
          <div class="flex flex-wrap items-center gap-3">
            <router-link to="/register" class="btn-editorial min-h-[56px] text-[16px]">
              {{ t('home.subscribe.startFree') }}
              <span aria-hidden="true">→</span>
            </router-link>
            <router-link to="/login" class="btn-editorial-ghost min-h-[56px] text-[16px]">{{ t('home.subscribe.signIn') }}</router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- ── Colophon: ruled cells ──────────────────────────────────────── -->
    <footer class="flex flex-wrap font-mono-curio text-[12px] uppercase tracking-[0.06em] text-[color:var(--ink-soft)]">
      <div class="flex flex-[1_1_280px] flex-col justify-center gap-1 border-b border-[color:var(--hairline)] px-4 py-5 sm:px-6 lg:border-b-0 lg:border-r">
        <p class="wordmark display-headline text-[24px] leading-none normal-case tracking-[-0.04em] text-[color:var(--ink)]">Curio</p>
        <p class="kicker">{{ t('home.footer.tagline', { vol: volumeLabel }) }}</p>
      </div>
      <nav class="flex flex-[2_1_420px] flex-wrap items-center gap-x-6 gap-y-2 border-b border-[color:var(--hairline)] px-4 py-5 sm:px-6 lg:border-b-0 lg:border-r">
        <LanguageSelect />
        <router-link to="/login" class="hover:text-[color:var(--ink)]">{{ t('home.footer.signIn') }}</router-link>
        <router-link to="/register" class="hover:text-[color:var(--ink)]">{{ t('home.footer.subscribe') }}</router-link>
        <router-link to="/privacy" class="hover:text-[color:var(--ink)]">{{ t('home.footer.privacy') }}</router-link>
        <router-link to="/terms" class="hover:text-[color:var(--ink)]">{{ t('home.footer.terms') }}</router-link>
        <router-link to="/contact" class="hover:text-[color:var(--ink)]">{{ t('home.footer.contact') }}</router-link>
      </nav>
      <div class="flex flex-[1_1_260px] flex-wrap items-center justify-between gap-x-6 gap-y-2 px-4 py-5 sm:px-6">
        <a :href="`mailto:${CONTACT_EMAIL}`" class="normal-case tracking-normal hover:text-[color:var(--ink)]">{{ CONTACT_EMAIL }}</a>
        <span>{{ t('home.footer.copyright') }}</span>
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
import { CONTACT_EMAIL } from '@/data/contact'
import LanguageSelect from '@/components/ui/LanguageSelect.vue'
import { useVolume } from '@/composables/useVolume'
import { useScrollNav } from '@/composables/useScrollNav'
import ScrollWords from '@/components/ui/ScrollWords.vue'

const { t, tm, rt } = useI18n()
const { formatDate } = useFormat()
const { topicLabel } = useTopicLabels()

const today = computed(() => formatDate(new Date(), 'full').toUpperCase())

const { volumeLabel, nextVolumeLabel } = useVolume()

const navBar = ref<HTMLElement | null>(null)
const { scrolled: navScrolled, hidden: navHidden, progress: navProgress } = useScrollNav(navBar)

// The sample card drifts up a touch faster than the page while the hero
// scrolls away (desktop two-column layout only).
const sampleCard = ref<HTMLElement | null>(null)
let parallaxFrame = 0
const updateParallax = () => {
  parallaxFrame = 0
  const el = sampleCard.value
  if (!el) return
  const wide = window.matchMedia('(min-width: 1024px)').matches
  el.style.setProperty('--parallax', wide ? String(Math.max(-80, -window.scrollY * 0.12)) : '0')
}
const onParallaxScroll = () => {
  if (!parallaxFrame) parallaxFrame = requestAnimationFrame(updateParallax)
}

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

  if (!reducedMotion) {
    window.addEventListener('scroll', onParallaxScroll, { passive: true })
    window.addEventListener('resize', onParallaxScroll, { passive: true })
  }

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
  window.removeEventListener('scroll', onParallaxScroll)
  window.removeEventListener('resize', onParallaxScroll)
  if (parallaxFrame) cancelAnimationFrame(parallaxFrame)
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
