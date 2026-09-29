# Security Policy

## Supported versions

Curio is deployed continuously from `main`. Only the latest `main` receives
security fixes.

## Reporting a vulnerability

**Please do not report security vulnerabilities through public GitHub issues,
discussions, or pull requests.**

Report privately through GitHub:
**Security → Advisories → [Report a vulnerability](https://github.com/smyeong123/curio/security/advisories/new)**.

Please include:

- The type of issue (e.g. auth bypass, XSS, SSRF, secret exposure)
- The affected endpoint, file, or component
- Steps to reproduce, or a proof of concept
- The impact, as you understand it

## What to expect

- Acknowledgement within **3 business days**
- An initial assessment within **7 days**
- Credit in the release notes once a fix ships, if you want it

## Scope

In scope: this repository's backend (`backend/`), frontend (`frontend/`), and the
deployment configuration in it.

Out of scope: third-party services Curio integrates with (Anthropic, Google, OpenAI,
Resend, NewsAPI) — report those to the vendor — and denial-of-service or volumetric
attacks against hosted instances.
