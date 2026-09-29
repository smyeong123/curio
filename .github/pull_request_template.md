<!--
PR title = the squash-merge commit message. Use Conventional Commits:
  feat(i18n): add language dropdown
-->

## What

<!-- What does this PR change? -->

## Why

<!-- The problem it solves. Link the issue: Closes #123 -->

## How to verify

<!-- Steps a reviewer can follow to see it working. -->

1.

## Screenshots

<!-- For UI changes: before / after, both EN and KO editions if copy changed. Delete if not applicable. -->

## Checklist

- [ ] `mvn -B verify` passes (backend)
- [ ] `npm run lint`, `npm run test:unit -- --run`, `npm run build` pass (frontend)
- [ ] Tests added or updated for the new behavior
- [ ] New UI strings added to both `locales/en` and `locales/ko`
- [ ] Docs updated (`docs/`, `.env.*.example`) if behavior, endpoints, env vars, or migrations changed
- [ ] No secrets, tokens, or personal data in the diff
