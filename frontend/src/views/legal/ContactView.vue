<template>
  <LegalLayout kicker="Contact" title="Get in touch">
    <p>
      Curio is run by <strong>Sangmyeong Lee</strong>. Email is the fastest way to reach me — I read
      every message and typically reply within two business days.
    </p>

    <h2>Email</h2>
    <ul>
      <li>
        <strong>All enquiries</strong> — general feedback, help &amp; account issues, and privacy or
        data requests: <a href="mailto:sangmyeonglee123@gmail.com">sangmyeonglee123@gmail.com</a>
      </li>
    </ul>

    <h2>Pricing &amp; upcoming paid plans</h2>
    <p>
      Curio is <strong>free while we test, through the end of July 2026</strong> — every feature is
      open to everyone. From <strong>August–September 2026</strong> we'll enable payments and
      introduce paid plans with additional features. See the
      <router-link to="/terms">terms</router-link> for details.
    </p>

    <h2>Before you write</h2>
    <ul>
      <li><strong>Want to stop emails?</strong> Use the unsubscribe link at the bottom of any digest, or turn delivery off in your settings — no need to email us.</li>
      <li><strong>Found an error in a digest?</strong> Tell us which issue and topic — it helps us improve the summaries.</li>
    </ul>

    <h2>New here?</h2>
    <p>
      Curio sends one tailored AI-model news digest every morning at 08:00 your local time, with a quick quiz to
      help it stick. <router-link to="/register">Subscribe free</router-link> or
      <router-link to="/login">sign in</router-link> to your account.
    </p>
  </LegalLayout>
</template>

<script setup lang="ts">
import LegalLayout from '@/components/layout/LegalLayout.vue'
</script>
