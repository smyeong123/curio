<template>
  <LegalLayout kicker="Privacy" title="Privacy Policy" updated="May 2026">
    <p>
      This policy explains what Curio collects, why, and the choices you have. We keep it short
      and specific — we only collect what the service genuinely needs to deliver your daily digest.
    </p>

    <h2>What we collect</h2>
    <ul>
      <li><strong>Account details</strong> — your email address, and either a password (stored only as a salted hash, never in plain text) or your Google account identifier if you sign in with Google.</li>
      <li><strong>Your interests</strong> — the AI-model topics you select during onboarding, used to tailor each digest.</li>
      <li><strong>Quiz activity</strong> — your answers and scores, so we can show your history and track retention.</li>
      <li><strong>Email engagement</strong> — whether a digest email was delivered, opened, or clicked, reported back to us by our email provider.</li>
    </ul>

    <h2>How we use it</h2>
    <ul>
      <li>To generate and send your personalized news digest each morning at 08:00 in your local timezone (we detect it from your device, or you can pin one in Settings).</li>
      <li>To build the recall quiz attached to each digest and remember your results.</li>
      <li>To operate your account, secure it, and respond when you contact us.</li>
    </ul>

    <h2>Service providers</h2>
    <p>We share the minimum necessary data with trusted providers who process it on our behalf:</p>
    <ul>
      <li><strong>Anthropic (Claude)</strong> — summarizes source articles into your digest. We send article text, not your identity.</li>
      <li><strong>News API</strong> — supplies the source articles we summarize.</li>
      <li><strong>Resend</strong> — delivers your digest emails and reports delivery, open, and click events.</li>
    </ul>

    <h2>Cookies &amp; sessions</h2>
    <p>
      We use authentication tokens to keep you signed in — a short-lived access token (15 minutes)
      and a refresh token stored on our side only as a hash. The refresh token is a sliding session:
      it stays valid while you use Curio and expires after about 3 hours of inactivity. We do not use
      third-party advertising or tracking cookies.
    </p>

    <h2>How long we keep it</h2>
    <p>
      Generated digests and quizzes are automatically deleted after <strong>30 days</strong> on a
      rolling basis. Your account information is retained until you delete your account, after which
      it is removed.
    </p>

    <h2>Your choices</h2>
    <ul>
      <li><strong>Unsubscribe anytime</strong> — every email includes a one-click unsubscribe link; you can also turn delivery off in your settings.</li>
      <li><strong>Delete your account</strong> — this removes your profile, preferences, and history.</li>
      <li><strong>Reach us</strong> — for any privacy request, email Sangmyeong Lee at <a href="mailto:sangmyeonglee123@gmail.com">sangmyeonglee123@gmail.com</a>.</li>
    </ul>

    <h2>Changes</h2>
    <p>
      If we update this policy, we'll revise the date above and, for material changes, notify you by
      email. Questions? <router-link to="/contact">Contact us</router-link>.
    </p>
  </LegalLayout>
</template>

<script setup lang="ts">
import LegalLayout from '@/components/layout/LegalLayout.vue'
</script>
