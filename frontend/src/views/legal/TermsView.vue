<template>
  <LegalLayout kicker="Terms" title="Terms of Service" updated="July 2026">
    <p>
      These terms govern your use of Curio. By creating an account or reading a digest, you agree to
      them. If you don't agree, please don't use the service.
    </p>

    <h2>The service</h2>
    <p>
      Curio is a daily email digest of AI-model news, tailored to topics you choose, with an optional
      recall quiz. Digests are delivered each morning at 08:00 in your local timezone (detected from
      your device, or pinned in Settings). We may add, change, or remove features as the product
      evolves.
    </p>

    <h2>Your account</h2>
    <ul>
      <li>Provide accurate information and keep your login credentials secure.</li>
      <li>You're responsible for activity under your account.</li>
      <li>You must be old enough to form a binding contract in your jurisdiction.</li>
    </ul>

    <h2>Acceptable use</h2>
    <ul>
      <li>Don't resell, scrape, or redistribute digest content at scale without permission.</li>
      <li>Don't attempt to disrupt, reverse-engineer, or gain unauthorized access to the service.</li>
      <li>Don't use Curio for anything unlawful.</li>
    </ul>

    <h2>AI-generated content</h2>
    <p>
      Digests and quizzes are produced with AI summarization of third-party sources. While we aim for
      accuracy, summaries <strong>may contain errors or omissions</strong> and are provided for general
      information only — not professional, financial, or legal advice. Always verify against the linked
      original sources before relying on anything.
    </p>

    <h2>Pricing</h2>
    <p>
      Curio is in a <strong>free testing period through 31 July 2026</strong> — the full daily
      digest, complete archive, and all topics at no cost. From
      <strong>August–September 2026</strong> we plan to enable payments and introduce paid plans
      with additional features for paid subscribers. We'll give clear notice here and by email
      before any paid plan launches, and you will never be charged unless you explicitly subscribe
      to one — the existing free tier isn't going away without notice.
    </p>

    <h2>Intellectual property</h2>
    <p>
      The Curio name, design, and original editorial framing are ours. Source articles remain the
      property of their respective publishers; we link back to every original.
    </p>

    <h2>Termination</h2>
    <p>
      You may delete your account at any time. We may suspend or terminate access if these terms are
      violated or if required to protect the service or other users.
    </p>

    <h2>Disclaimer &amp; liability</h2>
    <p>
      The service is provided "as is" without warranties of any kind. To the extent permitted by law,
      Curio is not liable for indirect or consequential damages arising from your use of the service.
    </p>

    <h2>Changes &amp; contact</h2>
    <p>
      We'll update the date above when these terms change. Questions about them? Email
      Sangmyeong Lee at
      <a href="mailto:sangmyeonglee123@gmail.com">sangmyeonglee123@gmail.com</a> or visit our
      <router-link to="/contact">contact page</router-link>.
    </p>
  </LegalLayout>
</template>

<script setup lang="ts">
import LegalLayout from '@/components/layout/LegalLayout.vue'
</script>
