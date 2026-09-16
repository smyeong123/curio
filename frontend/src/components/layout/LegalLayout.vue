<template>
  <div class="min-h-screen text-[color:var(--ink)]" style="background-color: var(--paper)">
    <!-- ── Masthead ─────────────────────────────────────────────────── -->
    <header class="border-b border-[color:var(--rule)]">
      <div class="mx-auto flex w-full max-w-[820px] items-center justify-between gap-6 px-6 py-5">
        <router-link to="/" class="flex items-baseline gap-3">
          <span class="display-headline text-[28px] leading-none">Curio</span>
          <span class="kicker hidden sm:inline">— A daily for AI model news</span>
        </router-link>
        <router-link
          to="/"
          class="text-[12px] font-mono-curio uppercase tracking-[0.14em] text-[color:var(--mute)] hover:text-[color:var(--ink)] transition-colors"
        >
          ← Back to home
        </router-link>
      </div>
    </header>

    <!-- ── Title + body ─────────────────────────────────────────────── -->
    <main class="mx-auto w-full max-w-[820px] px-6 py-12 md:py-16">
      <p class="kicker kicker-signal mb-4">{{ kicker }}</p>
      <h1 class="display-headline text-[40px] md:text-[52px] leading-[0.95] mb-3">{{ title }}</h1>
      <p v-if="updated" class="font-mono-curio text-[12px] uppercase tracking-[0.14em] text-[color:var(--mute)] mb-10">
        Last updated {{ updated }}
      </p>
      <div class="rule-strong mb-10"></div>

      <div class="legal-body font-body-curio text-[15.5px] leading-[1.72] text-[color:var(--ink-soft)]">
        <slot />
      </div>
    </main>

    <!-- ── Footer ───────────────────────────────────────────────────── -->
    <footer class="border-t border-[color:var(--rule)]">
      <div
        class="mx-auto flex w-full max-w-[820px] flex-wrap items-center gap-x-6 gap-y-2 px-6 py-8 text-[12px] font-mono-curio uppercase tracking-[0.14em] text-[color:var(--mute)]"
      >
        <router-link to="/privacy" class="hover:text-[color:var(--ink)]">Privacy</router-link>
        <router-link to="/terms" class="hover:text-[color:var(--ink)]">Terms</router-link>
        <router-link to="/contact" class="hover:text-[color:var(--ink)]">Contact</router-link>
        <a href="mailto:sangmyeonglee123@gmail.com" class="normal-case tracking-normal hover:text-[color:var(--ink)]">sangmyeonglee123@gmail.com</a>
        <span>© 2026 Curio</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string; kicker: string; updated?: string }>()
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
