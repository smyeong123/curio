# Curio -- Complete Codebase Reference

**Last updated:** 2026-07-18 (based on full audit of all source files)

## Table of Contents

1. [Project Overview](#project-overview)
2. [Full Directory Tree](#full-directory-tree)
3. [Backend](#backend)
4. [Frontend](#frontend)
5. [Integration](#integration)
6. [Infrastructure & DevOps](#infrastructure--devops)
7. [Testing](#testing)
8. [Hexagonal Architecture (Implemented)](#hexagonal-architecture-implemented)

> Open gaps, known issues, and coverage estimates that used to live in this file have moved to the private working notes (`docs-internal/CODEBASE_NOTES.md`) to keep this reference a description of what exists rather than a running to-do list.

---

## Project Overview

Curio is an AI-powered personalized news service. Each day it fetches news articles, summarizes them using an AI model (Claude by default, with Gemini and OpenAI alternatives), and emails a digest to each user. A quiz is auto-generated per digest to improve content retention. The system is split into a **Vue 3 SPA frontend** and a **Java Spring Boot REST API backend**, communicating over HTTP with JWT authentication.

### Technology Stack

**Frontend**

| Technology | Version | Purpose |
|---|---|---|
| Vue.js | 3.5 | UI framework (Composition API, `<script setup>`) |
| TypeScript | 5.9 | Type safety |
| Vite | 7 | Build tool + dev proxy for `/api` |
| Pinia | 3 | State management |
| Vue Router | 4 | Routing + auth guards |
| vue-i18n | 11 | Bilingual UI (English / Korean), JSON catalogs per feature namespace |
| Tailwind CSS | 4 | Utility CSS, `@theme` token system |
| Axios | 1.13 | HTTP client (JWT interceptors, silent refresh, per-call timeouts) |
| Fraunces / Inter Tight / JetBrains Mono (+ Noto Serif/Sans KR) | — | Editorial type system (Google Fonts) |
| Vitest | 4 | Unit tests |
| Cypress | 15 | E2E tests |

**Backend**

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Runtime |
| Spring Boot | 3.5 | Framework (Web, Data JPA, Security, Validation, Actuator) |
| jjwt | 0.12.6 | JWT access tokens |
| PostgreSQL | 16 | Primary DB, Flyway migrations (V1–V29) |
| Redis | 7 | AI summary cache (12 h TTL) + job-status registry |
| Resilience4j | 2.2 | Circuit breaker + bulkhead around AI calls |
| ShedLock | 5.10 | Distributed scheduler locking |
| Bucket4j | 8.10 | Per-IP rate limiting |
| Springdoc OpenAPI | 2.8.6 | `/api-docs` + Swagger UI (dev only) |
| Sentry | 7.14 | Error tracking (opt-in via DSN) |
| Micrometer + Prometheus | — | Metrics (`/actuator/prometheus`, ADMIN-gated) |
| Thymeleaf | — | Digest + auth email templates |
| Maven | 3.9+ | Build tool |

**External services**

| Service | Purpose | Required? |
|---|---|---|
| Anthropic Claude API | Summary + quiz generation (default provider, `AI_PROVIDER=claude`) | Yes |
| Google Gemini API / OpenAI API | Alternate providers | Optional |
| NewsAPI (`/v2/everything`) | News source aggregation | Yes (without it digests fall back to lab blogs only) |
| Resend | Transactional email + webhook tracking | Yes |
| Google Identity Services | Sign-in with Google (ID-token flow) | Optional |
| Sentry | Error tracking | Optional |

Containers: Docker multi-stage builds; Docker Compose for local infra and the production stack.

---

## Full Directory Tree

```
curio/
├── .claude/                         # Claude Code AI assistant config
│   ├── agents/
│   │   └── docs-updater.md          # Custom subagent definition
│   └── settings.local.json          # Per-project tool permissions
│
├── backend/                         # Spring Boot REST API (Java 21)
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   ├── .env / .env.example
│   └── src/
│       ├── main/
│       │   ├── java/com/curio/                    # Hexagonal feature packages (2026-03-04)
│       │   │   ├── CurioApplication.java
│       │   │   ├── auth/
│       │   │   │   ├── controller/   AuthController
│       │   │   │   ├── service/      AuthService (register/login/refresh/logout/password reset, session issuance),
│       │   │   │   │                 EmailVerificationChallengeService (2FA code lifecycle), GoogleIdTokenVerifier → GoogleIdentity,
│       │   │   │   │                 TokenHasher (SHA-256 at-rest form of every auth secret), UserDetailsServiceImpl, UnsubscribeTokenService
│       │   │   │   ├── dto/          LoginRequest, RegisterRequest, GoogleLoginRequest,
│       │   │   │   │                 ForgotPasswordRequest, ResetPasswordRequest, AuthResponse,
│       │   │   │   │                 LoginResponse, VerifyCodeRequest, VerifyCodeResponse,
│       │   │   │   │                 ResendCodeRequest, ResendCodeResponse   (email-2FA, V24)
│       │   │   │   ├── entity/       RefreshToken, PasswordResetToken, EmailVerificationCode (V24)
│       │   │   │   ├── repository/   RefreshTokenRepository, PasswordResetTokenRepository, EmailVerificationCodeRepository
│       │   │   │   ├── port/in/      AuthUseCase
│       │   │   │   ├── port/out/     RefreshTokenPort, PasswordResetTokenPort, EmailVerificationCodePort
│       │   │   │   └── adapter/persistence/  RefreshTokenJpaAdapter, PasswordResetTokenJpaAdapter, EmailVerificationCodeJpaAdapter
│       │   │   ├── user/
│       │   │   │   ├── controller/   UserController, UserApiKeyController (BYOK)
│       │   │   │   ├── service/      UserService, UserApiKeyService (BYOK)
│       │   │   │   ├── dto/          PreferencesRequest, PreferencesResponse, UserResponse, UpdateProfileRequest
│       │   │   │   ├── entity/       User, UserPreferences, UserApiKey (V17)
│       │   │   │   ├── repository/   UserRepository, UserPreferencesRepository, UserApiKeyRepository
│       │   │   │   ├── port/in/      UserUseCase, UserApiKeyUseCase, ApiKeyValidator
│       │   │   │   ├── port/out/     UserPort, UserPreferencesPort, UserApiKeyPort
│       │   │   │   └── adapter/persistence/  UserJpaAdapter, UserPreferencesJpaAdapter, UserApiKeyJpaAdapter
│       │   │   ├── news/
│       │   │   │   ├── controller/   NewsController
│       │   │   │   ├── service/      NewsService, AbstractAiProvider (shared AI orchestration base:
│       │   │   │   │                 cache, single-flight, retry, breaker, parse), AiPrompts (the summary + quiz
│       │   │   │   │                 prompt text, pure functions), ClaudeService, GeminiService, OpenAiService, NewsApiClient,
│       │   │   │   │                 LabBlogFetcher (45-min feed memo), LabBlogRegistry, LlmKeyValidator
│       │   │   │   ├── dto/          DigestResponse, NewsSummary, QuizGenerationResult, QuizQuestionItem
│       │   │   │   ├── entity/       Digest
│       │   │   │   ├── repository/   DigestRepository
│       │   │   │   ├── port/in/      NewsUseCase, DigestProgressListener, DigestGeneration (status + digest)
│       │   │   │   ├── port/out/     DigestPort, AiService (outbound AI port: summaries + quiz per edition)
│       │   │   │   └── adapter/persistence/  DigestJpaAdapter
│       │   │   ├── quiz/
│       │   │   │   ├── controller/   QuizController
│       │   │   │   ├── service/      QuizService, QuizAttemptRecorder (REQUIRES_NEW attempt persistence)
│       │   │   │   ├── dto/          QuizSubmitRequest, QuizResponse (+PreviousAttempt), QuizQuestionSet (answer-key-free questions),
│       │   │   │   │                 QuizSubmitResponse (+QuestionResult), QuizHistoryEntry
│       │   │   │   ├── entity/       Quiz, QuizAttempt
│       │   │   │   ├── repository/   QuizRepository, QuizAttemptRepository
│       │   │   │   ├── port/in/      QuizUseCase
│       │   │   │   ├── port/out/     QuizPort, QuizAttemptPort
│       │   │   │   └── adapter/persistence/  QuizJpaAdapter, QuizAttemptJpaAdapter
│       │   │   ├── studio/
│       │   │   │   ├── controller/   StudioController
│       │   │   │   ├── service/      StudioService, StudioTaskStatusService
│       │   │   │   ├── dto/          StudioStatusResponse, TaskStatus (NON_NULL snapshot of one task), StudioOverview
│       │   │   │   └── port/in/      StudioUseCase
│       │   │   ├── admin/
│       │   │   │   ├── controller/   AdminController
│       │   │   │   ├── service/      AdminUserService, AdminDigestService, AdminStatsService, AdminOperationsService, AdminManualJobService, AuditLogService
│       │   │   │   ├── dto/          StatsResponse, AdminDigestResponse, GenerateDigestsRequest, TopicStatusResponse, JobStatusResponse, AuditLogResponse,
│       │   │   │   │                 AdminUserSummary, AdminUserDetail (+Metrics/RecentDigest/RecentQuizAttempt), JobTriggerResponse
│       │   │   │   ├── entity/       AuditLog (V18)
│       │   │   │   ├── repository/   AuditLogRepository
│       │   │   │   ├── port/in/      AdminUserUseCase, AdminDigestUseCase, AdminStatsUseCase, AdminOperationsUseCase, AdminManualJobUseCase
│       │   │   │   └── adapter/persistence/  AuditLogJpaAdapter
│       │   │   └── shared/
│       │   │       ├── config/       SecurityConfig, CorsConfig, RedisConfig, OpenApiConfig, AsyncConfig (batch pools core==max), ClockConfig, RequestLoggingConfig, ShedLockConfig, TopicConstants, WebhookSecretsValidator, JwtSecretsValidator, SentryTaggingConfig, WebConfig
│       │   │       ├── security/     JwtTokenProvider, JwtAuthenticationFilter, WebhookSignatureVerifier, CookieUtils, UserDetailsAdapter, UserPrincipalResolver,
│       │   │       │                 RateLimitingFilter, InMemoryRateLimiter, WebhookBodyLimitFilter, CorrelationIdFilter, ApiKeyCipher, UnsubscribeTokenService
│       │   │       ├── concurrent/   SingleFlight
│       │   │       ├── util/         EmailNormalizer
│       │   │       ├── batch/        SubscriberBatch (chunked per-subscriber runner: bounded in-flight, per-user timeout, Tally of outcomes)
│       │   │       ├── digest/       DigestPipeline (generate digest + quiz for one user), DigestBatch (generation for every subscriber),
│       │   │       │                 DigestEmailBatch (hourly "due" send / admin "send all unsent")
│       │   │       ├── jobs/         JobStatusRegistry (last run per job, Redis), JobFailureNotifier (Sentry), JobRunRecorder (one result shape + failure recording)
│       │   │       ├── scheduler/    DigestGenerationJob, EmailSendJob (thin @Scheduled delegators to shared/digest), CleanupJob, ExpiredAuthRowReaper
│       │   │       ├── exception/    GlobalExceptionHandler, ResourceNotFoundException, UnauthorizedException, RootCauses
│       │   │       ├── email/        EmailService (render + orchestrate), EmailTransport (Resend wire call, retry, SMTP fallback)
│       │   │       ├── i18n/         Language (EN/KO edition: codes, locales, digest-content stamp)
│       │   │       ├── webhook/      WebhookController, EmailEventProcessor (open/click stamps, bounce/complaint suppression)
│       │   │       └── port/in/      EmailUseCase (send), EmailEventUseCase (webhook events)
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       ├── application-test.yml
│       │       ├── db/migration/    # V1-V29 Flyway SQL scripts
│       │       ├── messages.properties      # email chrome, English (digest + auth mails: subject, labels, date patterns)
│       │       ├── messages_ko.properties   # same keys, Korean edition
│       │       └── templates/
│       │           ├── digest-email.html   # Thymeleaf email template (all copy via #{...} keys, lang from the digest)
│       │           ├── auth-email.html     # 2FA code + password-reset mails (kind=code|reset), edition from the user's preference
│       │           └── unsubscribe-{confirm,done,invalid}.html   # the two-step unsubscribe pages served by UserController
│       └── test/java/com/curio/
│           ├── CurioApplicationTests.java
│           ├── HexagonalArchitectureTest.java  # ArchUnit rules (5 rules, incl. no cycles among shared.* packages)
│           ├── admin/controller/    AdminControllerSecurityIntegrationTest
│           ├── admin/service/       AdminOperationsServiceTest, AdminStatsServiceTest, AdminUserServiceTest, AuditLogServiceTest
│           ├── auth/controller/     AuthControllerIntegrationTest
│           ├── auth/service/        AuthServiceTest, EmailVerificationChallengeServiceTest, GoogleIdTokenVerifierTest
│           ├── user/controller/     UserControllerPasswordIntegrationTest, UserControllerUnsubscribeIntegrationTest
│           ├── user/service/        UserServicePasswordTest, UserApiKeyServiceTest, UserServicePreferencesTest
│           ├── quiz/service/        QuizServiceTest, QuizAttemptRecorderTest
│           ├── studio/service/      StudioServiceTest
│           ├── news/service/        ClaudeServiceTest, GeminiServiceTest, OpenAiServiceTest, NewsApiClientTest, NewsServiceTest, AiPromptsTest
│           ├── shared/webhook/      WebhookControllerSignatureIntegrationTest, EmailEventProcessorTest
│           ├── shared/batch/        SubscriberBatchTest (+ InlinePool test helper)
│           ├── shared/digest/       DigestPipelineTest, DigestBatchTest, DigestEmailBatchTest
│           ├── shared/jobs/         JobRunRecorderTest
│           ├── shared/concurrent/   SingleFlightTest
│           ├── shared/config/       JwtSecretsValidatorTest
│           ├── shared/i18n/         LanguageTest
│           ├── shared/time/         DigestDayTest
│           ├── shared/email/        EmailServiceRenderTest, EmailTransportTest
│           └── shared/security/     ApiKeyCipherTest, UnsubscribeTokenServiceTest
│
├── frontend/                        # Vue 3 SPA (TypeScript + Vite)
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── nginx.conf
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.js           # v3-style config, still used for darkMode:'class' + content globs
│   ├── cypress.config.ts
│   ├── tsconfig*.json
│   └── src/
│       ├── main.ts
│       ├── App.vue                  # Root: router-view + ToastContainer
│       ├── assets/
│       │   ├── styles/main.css      # Tailwind v4 import + @theme + base styles
│       │   └── vue.svg              # Unused Vite asset
│       ├── router/index.ts          # Routes + auth/admin guards
│       ├── i18n/
│       │   ├── index.ts             # vue-i18n instance, locale detection, catalog auto-discovery
│       │   └── locales/{en,ko}/     # one JSON per namespace (common, home, layout, topics, auth, archive, quiz, settings, studio, onboarding, admin, legal)
│       ├── stores/                  # auth, user, news, quiz (Pinia)
│       ├── services/
│       │   ├── api.ts               # Single Axios client + all endpoint methods
│       │   └── sentry.ts            # Optional Sentry init (VITE_SENTRY_DSN)
│       ├── utils/                   # apiError.ts, safeUrl.ts, timezone.ts
│       ├── composables/
│       │   ├── useDisclosureSet.ts  # Open/closed set for accordions and filters (toggle/isOpen), shared by three pickers
│       │   ├── useEnsureTimezone.ts # Device-timezone auto-follow (wired in DashboardLayout)
│       │   ├── useFocusOnEnter.ts
│       │   ├── useFocusTrap.ts
│       │   ├── useFormat.ts         # Edition-aware date/time/number/duration formatting (styles named by role: full/short/stamp/compact)
│       │   ├── useLocale.ts         # Edition (en/ko) state: persist on explicit choice, <html lang>, document.title
│       │   ├── usePagedAdminList.ts # load(page) + page envelope shared by the admin roster/ledger/editions tables
│       │   ├── usePoller.ts         # Non-overlapping poll loop with attempt/failure caps (Studio + admin job status)
│       │   ├── useTheme.ts          # Light/dark theme toggle
│       │   ├── useTopicLabels.ts    # Localized labels for the topic taxonomy (names stay canonical)
│       │   ├── useTopicSelection.ts # Beat-picker state shared by onboarding and Settings
│       │   └── useToast.ts
│       ├── types/                   # user.ts, news.ts, quiz.ts, studio.ts, admin.ts, page.ts (SpringPage<T>), google-identity.d.ts
│       ├── components/
│       │   ├── ui/                  # BaseButton, BaseInput, BaseModal, BaseSpinner, ToastContainer, AppIcon, LanguageSelect,
│       │   │                        # PageMasthead, ErrorState, PagerNav, SegmentedControl
│       │   ├── auth/                # LoginForm, RegisterForm, AuthMasthead, AuthColophon, MailSentPanel
│       │   ├── layout/              # DashboardLayout, DashboardMenu, DashboardMenuFoot, LegalLayout
│       │   ├── archive/             # ArchiveSearch, BeatFilter, DigestIssue
│       │   ├── quiz/                # QuizQuestion, QuizResults
│       │   ├── studio/              # TaskProgress
│       │   ├── admin/               # JobCard
│       │   ├── settings/            # ApiKeyManager (BYOK key UI)
│       │   └── ErrorBoundary.vue    # Global error boundary
│       └── views/
│           ├── HomeView.vue
│           ├── OnboardingView.vue
│           ├── NotFoundView.vue     # 404 catchall route
│           ├── auth/                # LoginView, RegisterView, VerifyCodeView, ResetPasswordView
│           ├── legal/               # PrivacyView, TermsView, ContactView
│           ├── dashboard/           # ArchiveView, QuizView, QuizHistoryView, SettingsView, StudioView
│           └── admin/               # AdminDashboardView, AdminDigestsView, StatsView, UsersView, UserDetailView, AuditLogView
│
├── docs/                            # Public project documentation
│   ├── CODEBASE.md                  # This file
│   ├── ARCHITECTURE.md              # System architecture overview
│   ├── SETUP.md                     # Local development setup
│   ├── ENV_VARIABLES.md             # Environment variable reference
│   ├── SETUP_API_KEYS.md            # API key signup guide
│   ├── DEPLOY.md                    # Production deploy checklist (moved from repo root)
│   └── architecture/               # Hexagonal architecture docs (ADR, package design, test strategy)
│
├── docs-internal/                   # Private maintainer docs (gitignored, not in the published repo)
│
├── README.md
├── docker-compose.infra.yml              # Local dev: PostgreSQL + Redis only
├── docker-compose.yml                    # Prod/staging: full containerized stack
├── docker-compose.tls.yml                # Optional Caddy TLS overlay
└── .env.prod.example                     # Prod env var template
```

---

## Backend

### Architecture

The backend uses **hexagonal (ports & adapters) architecture** (implemented 2026-03-04). Files are organized into feature packages (`auth`, `user`, `news`, `quiz`, `studio`, `admin`, `shared`), each containing `controller`, `service`, `port/in`, `port/out`, `adapter/persistence`, `entity`, `dto`, and `repository` subpackages. See the [Hexagonal Architecture section](#hexagonal-architecture-implemented) for details.

### REST API Endpoints

Base URL: `/api/v1`

#### AuthController (`/api/v1/auth`)

| Method | Path | Auth | Request Body | Response |
|--------|------|------|-------------|----------|
| POST | `/register` | Public | `RegisterRequest { email, password, fullName }` | `AuthResponse` |
| POST | `/login` | Public | `LoginRequest { email, password }` | `{ challengeId, ... }` (emails 2FA code) |
| POST | `/verify-code` | Public | `{ challengeId, code }` | `AuthResponse` (issues session) |
| POST | `/resend-code` | Public | `{ challengeId }` | `{ challengeId, ... }` (issues a fresh code) |
| POST | `/google` | Public | `GoogleLoginRequest { token }` | `AuthResponse` |
| POST | `/refresh` | Public | `{ refreshToken }` | `AuthResponse` |
| POST | `/logout` | Public | `{ refreshToken }` (optional) | 200 empty |
| POST | `/forgot-password` | Public | `ForgotPasswordRequest { email }` | `{ message }` |
| POST | `/reset-password` | Public | `ResetPasswordRequest { token, newPassword }` | `{ message }` |

**Google OAuth implementation:** The frontend sends a Google ID token via `POST /auth/google`. The backend verifies it by calling Google's tokeninfo REST API directly (`AuthService`, checking audience + issuer). There is NO Spring OAuth2 callback flow and no `OAuth2SuccessHandler` class — the `spring-boot-starter-oauth2-client` dependency was dropped, so the flow needs only `GOOGLE_CLIENT_ID` (no client secret).

#### UserController (`/api/v1/user`)

| Method | Path | Auth | Request Body | Response |
|--------|------|------|-------------|----------|
| GET | `/me` | JWT | - | `UserResponse` |
| PUT | `/me` | JWT | `{ fullName?, deliveryEnabled? }` | `UserResponse` |
| GET | `/preferences` | JWT | - | `{ topics: string[], timezone, deliveryHour, timezoneAuto, language }` |
| PUT | `/preferences` | JWT | `PreferencesRequest { topics }` | `{ topics }` |
| DELETE | `/me` | JWT | - | 200 empty |
| GET | `/unsubscribe?token=` | Public | - | HTML string |

#### NewsController (`/api/v1/news`)

| Method | Path | Auth | Request Body | Response |
|--------|------|------|-------------|----------|
| GET | `/digests?page=0&size=10` | JWT | - | `Page<DigestResponse>` |
| GET | `/digests/{id}` | JWT | - | `DigestResponse` |
| GET | `/search?q=&page=0&size=10` | JWT | - | `Page<DigestResponse>` (full-text digest search, V21) |

#### QuizController (`/api/v1/quiz`)

| Method | Path | Auth | Request Body | Response |
|--------|------|------|-------------|----------|
| GET | `/digest/{digestId}` | JWT | - | `QuizResponse` (questions + options only — correct answers/explanations withheld until submit) |
| POST | `/{quizId}/submit` | JWT | `QuizSubmitRequest { answers }` | `{ score, totalQuestions, results[], bestScore, improved }` |
| GET | `/history?page=0` | JWT | - | `Page<QuizAttempt>` (page size hardcoded to 10) |

**Note:** `GET /quiz/digest/{digestId}` no longer returns correct answers or explanations before submission — scoring is server-side. Submit returns this attempt's score plus `bestScore` and `improved` ("better score wins", backed by the V25 unique constraint); a concurrent first submit recovers instead of 500ing via `QuizAttemptRecorder` (REQUIRES_NEW). Exactly 5 questions per quiz; empty AI results are rejected (not persisted).

#### AdminController (`/api/v1/admin`)

| Method | Path | Auth | Request Body | Response |
|--------|------|------|-------------|----------|
| GET | `/users?page=&size=&search=` | ADMIN | - | `Page<Map>` |
| GET | `/users/{id}` | ADMIN | - | `{ user, topics, metrics, recentDigests, recentQuizAttempts }` |
| GET | `/stats` | ADMIN | - | `StatsResponse { totalUsers, emailsSentToday, quizCompletionsToday }` |
| GET | `/stats/topics` | ADMIN | - | `Map<String, Long>` |
| POST | `/generate-digests` | ADMIN | `GenerateDigestsRequest { topics? }` (optional) | 202 `{ status: "started" \| "already_running" }` (async — runs on a background executor; poll `/jobs/status` for counts) |
| POST | `/send-emails` | ADMIN | - | 202 `{ status: "started" \| "already_running" }` (async — runs on a background executor; poll `/jobs/status` for counts) |
| POST | `/cleanup` | ADMIN | - | `{ deleted, orphaned }` |
| GET | `/digests?page=&size=&topic=&userEmail=` | ADMIN | - | `Page<AdminDigestResponse>` |
| GET | `/topics/status` | ADMIN | - | `List<TopicStatusResponse>` |
| GET | `/jobs/status` | ADMIN | - | `List<JobStatusResponse>` |
| GET | `/audit-log?page=&size=` | ADMIN | - | `Page<AuditLogResponse>` (most-recent-first) |

Admin protection is defense-in-depth: a URL pattern in `SecurityConfig` (`.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`) **plus** `@PreAuthorize` annotations (READ/WRITE/SUPER expressions) on the individual `AdminController` methods.

#### UserApiKeyController (`/api/v1/user/api-keys`)

| Method | Path | Auth | Request Body | Response |
|--------|------|------|-------------|----------|
| GET | `` | JWT | - | `List<ApiKeySummary>` (metadata only) |
| POST | `` | JWT | `SaveApiKeyRequest { provider, apiKey, currentPassword }` | `ApiKeySummary` |
| DELETE | `/{provider}` | JWT | `DeleteApiKeyRequest { currentPassword }` | 204 No Content |
| POST | `/validate` | JWT | `ValidateRequest { provider, apiKey }` | `{ valid: boolean }` |

This is a BYOK (Bring Your Own Key) store — users supply their own LLM provider key; it is **not** a Curio-generated API key, so there is no key-generation endpoint and no full key is ever returned. Notes:

- **Write re-auth:** `POST` (add/replace) and `DELETE` require the account's `currentPassword` in the body (defense-in-depth: a stolen JWT alone can't drain someone's LLM credit). These write paths are rate-limited **5 attempts / 5 min per user**; `/validate` is limited **5 / min per user**.
- **`ApiKeySummary`** (never includes the plaintext or encrypted bytes): `provider`, `keyPreview` (masked, e.g. `sk-ant-...XYZW`), `validated`, `validatedAt`, `lastUsedAt`, `updatedAt`, `rotatedAt`, `stale` (true when not rotated in 90+ days).
- On `POST`, the key is **always** validated against the provider before storage (the old opt-out `validate` flag was removed).
- `provider` is a `UserApiKey.Provider` enum (claude / gemini / openai).

#### StudioController (`/api/v1/studio`)

| Method | Path | Auth | Request Body | Response |
|--------|------|------|-------------|----------|
| POST | `/generate` | JWT | - | Starts digest generation (live task) |
| POST | `/send-email` | JWT | - | Emails the latest unsent digest |
| GET | `/status` | JWT | - | Live task status + context |

#### WebhookController (`/api/v1/webhooks`)

| Method | Path | Auth | Request Body | Response |
|--------|------|------|-------------|----------|
| POST | `/email` | Svix signature | Raw JSON | 200/400 |

### Database Schema (Actual Entity Fields)

#### `users` table

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK, auto-generated | |
| `email` | VARCHAR | UNIQUE, NOT NULL | |
| `password_hash` | VARCHAR | nullable | Null for Google OAuth users |
| `google_id` | VARCHAR | UNIQUE, nullable | No `oauth_provider` column exists |
| `full_name` | VARCHAR | nullable | |
| `is_admin` | BOOLEAN | DEFAULT false | No `role` column -- admin status is a boolean |
| `delivery_enabled` | BOOLEAN | DEFAULT true | |
| `email_verified` | BOOLEAN | DEFAULT false | Auto-set `true` on register |
| `created_at` | TIMESTAMP | @PrePersist | |
| `updated_at` | TIMESTAMP | @PrePersist, @PreUpdate | |

User implements Spring Security's `UserDetails`. `isEnabled()` returns `emailVerified`. Authorities: `ROLE_ADMIN` if `isAdmin`, else `ROLE_USER`.

#### `user_preferences` table

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK -> users, NOT NULL | @ManyToOne LAZY |
| `topics` | TEXT[] | PostgreSQL array | |
| `language` | VARCHAR(8) | NOT NULL DEFAULT 'en', CHECK IN ('en','ko') | Edition the digest, quiz and daily email are written in (V28); same codes as the frontend locale |
| `created_at` | TIMESTAMP | @PrePersist | |
| `updated_at` | TIMESTAMP | @PrePersist, @PreUpdate | |

#### `digests` table

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK -> users, NOT NULL | |
| `content` | JSONB | NOT NULL | Contains `summaries` array, `generatedFor` topics and `language` (`"en"`/`"ko"`, the edition the stories were written in; absent on pre-V28 digests = English) |
| `generated_at` | TIMESTAMP | @PrePersist | NOT `created_at` |
| `email_sent_at` | TIMESTAMP | nullable | Set when email sent |
| `email_provider_id` | VARCHAR | UNIQUE, nullable | Resend message ID |
| `email_opened_at` | TIMESTAMP | nullable | Set by webhook |
| `email_clicked_at` | TIMESTAMP | nullable | Set by webhook |

#### `quizzes` table

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `digest_id` | UUID | FK -> digests, UNIQUE, NOT NULL | @OneToOne LAZY |
| `questions` | JSONB | NOT NULL | Contains `questions` array |
| `created_at` | TIMESTAMP | @PrePersist | |

#### `quiz_attempts` table

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `quiz_id` | UUID | FK -> quizzes, NOT NULL | UNIQUE(user_id, quiz_id) via `uq_quiz_attempts_user_quiz` (V25) |
| `user_id` | UUID | FK -> users, NOT NULL | One attempt row per (user, quiz); "better score wins" |
| `answers` | JSONB | NOT NULL | |
| `score` | INTEGER | NOT NULL | |
| `completed_at` | TIMESTAMP | @PrePersist | NOT `submitted_at` |

#### `refresh_tokens` table

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK -> users, NOT NULL | |
| `token_hash` | VARCHAR | UNIQUE, NOT NULL | SHA-256 hash of JWT refresh token (V9 migration) |
| `expires_at` | TIMESTAMP | NOT NULL | |
| `created_at` | TIMESTAMP | @PrePersist | |

#### `password_reset_tokens` table

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK -> users, NOT NULL | |
| `token_hash` | VARCHAR | UNIQUE, NOT NULL | SHA-256 hash (NOT raw token) |
| `expires_at` | TIMESTAMP | NOT NULL | 1 hour from creation |
| `used_at` | TIMESTAMP | nullable | NOT a boolean `used` flag |
| `created_at` | TIMESTAMP | @PrePersist | |

#### `user_api_keys` table (V17, V19)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `user_id` | UUID | FK -> users, NOT NULL | UNIQUE(user_id, provider) — one key per provider per user |
| `provider` | VARCHAR(32) | NOT NULL, CHECK | `CLAUDE` / `GEMINI` / `OPENAI` |
| `encrypted_key` | BYTEA | NOT NULL | AES-256-GCM ciphertext (master key = env `API_KEY_ENCRYPTION_KEY`) |
| `key_iv` | BYTEA | NOT NULL | GCM nonce |
| `key_preview` | VARCHAR(64) | NOT NULL | `prefix...last4` (e.g. `sk-ant-...XYZW`) — the only part any HTTP response includes |
| `validated_at` | TIMESTAMPTZ | nullable | Set after a successful live validation |
| `last_used_at` | TIMESTAMPTZ | nullable | |
| `rotated_at` | TIMESTAMP | nullable | V19 — backs the 90-day stale warning |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL | |

#### `audit_log` table (V18)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | BIGSERIAL | PK | Not a UUID |
| `actor_id` | UUID | nullable | Admin who performed the action |
| `actor_email` | VARCHAR(255) | nullable | Denormalized actor email at action time |
| `action` | VARCHAR(128) | NOT NULL | Action type (e.g. GENERATE_DIGESTS, SEND_EMAILS, CLEANUP) |
| `target_type` | VARCHAR(64) | nullable | Entity the action targeted (e.g. USER, DIGEST) |
| `target_id` | VARCHAR(255) | nullable | Identifier of the target |
| `request_id` | VARCHAR(128) | nullable | Correlation id of the originating request |
| `metadata` | JSONB | nullable | Structured action detail |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | |

Indexes: `idx_audit_log_actor_id` (actor_id), `idx_audit_log_action` (action), `idx_audit_log_created_at` (created_at DESC).

### Flyway Migrations

| Version | Description |
|---------|------------|
| V1 | `create_users` |
| V2 | `create_preferences` |
| V3 | `create_refresh_tokens` |
| V4 | `create_digests` |
| V5 | `create_quizzes` |
| V6 | `add_email_provider_id_to_digests` |
| V7 | `create_password_reset_tokens` |
| V8 | `add_performance_indexes` |
| V9 | `rename_refresh_token_to_token_hash` (SHA-256 hashing, truncates old tokens) |
| V10 | `create_subscriptions` (subscription table, later dropped by V22) |
| V11 | `migrate_legacy_topics` |
| V12 | `add_gin_index_digest_content` |
| V13 | `create_shedlock` (ShedLock table for distributed scheduling) |
| V14 | `add_performance_indexes_v2` |
| V15 | `digest_content_projection` |
| V16 | `rewrite_topics_for_model_focus` (AI model-centric 3-level hierarchy) |
| V17 | `create_user_api_keys` (BYOK support) |
| V18 | `create_audit_log` (Admin action audit trail) |
| V19 | `add_user_api_key_rotation` (Adds rotated_at column to user_api_keys for key rotation tracking) |
| V20 | `add_user_delivery_time` (Per-user custom delivery time) |
| V21 | `create_digest_content_fts` (Full-text search index) |
| V22 | `drop_subscriptions` (Removed Stripe integration) |
| V23 | `digest_unique_per_user_day` (one digest per user per UTC day; idempotency backstop — superseded by V29) |
| V24 | `email_verification_codes` (Email/password login 2FA codes: challenge_hash + code_hash SHA-256, attempts_remaining) |
| V25 | `quiz_attempt_unique_per_user` (adds `uq_quiz_attempts_user_quiz` UNIQUE(user_id, quiz_id): one attempt row per (user, quiz), "better score wins") |
| V26 | `add_timezone_auto` (Adds timezone_auto column to user_preferences for device-based timezone auto-follow) |
| V27 | `normalize_emails_lowercase` (Folds existing emails to trimmed-lowercase and adds a `lower(email)` unique index — one account per real mailbox regardless of case; backs `EmailNormalizer`) |
| V28 | `add_language_to_user_preferences` (Adds `language` VARCHAR(8) NOT NULL DEFAULT 'en' + CHECK ('en','ko') to user_preferences — the edition the AI writes the digest/quiz/email in) |
| V29 | `digest_day_kst_and_default_delivery_hour` (Replaces V23's UTC-day index with `idx_digests_user_digest_day` on `curio_digest_day(generated_at)` — one digest per user per 05:00-KST digest day; dedupes any pair sharing a digest day, keeping the newest; moves every `delivery_hour = 8` to the new default 6) |

### Service Layer

#### AiService Interface

```java
// Platform-key calls
List<NewsSummary> generateNewsSummaries(String topic);
QuizGenerationResult generateQuizQuestions(String digestContent);

// BYOK overloads — substitute a user-supplied key for a single call (null/blank
// falls back to the platform-key path). BYOK calls skip the shared cache and circuit breaker.
default List<NewsSummary> generateNewsSummaries(String topic, String overrideApiKey);
default QuizGenerationResult generateQuizQuestions(String digestContent, String overrideApiKey);

// Difficulty-aware quiz generation (the shared AbstractAiProvider prompt honors the hint for every provider)
enum DifficultyHint { EASIER, NORMAL, HARDER }
default QuizGenerationResult generateQuizQuestions(String digestContent, DifficultyHint hint);
default QuizGenerationResult generateQuizQuestions(String digestContent, DifficultyHint hint, String overrideApiKey);

// Edition-aware entry points (what NewsService / QuizService actually call). `Language` (shared/i18n) is EN or KO;
// the 1-/2-/3-arg overloads above all delegate here with Language.EN. Platform-key summaries are cached per
// topic+date+language (`news:summaries:{topic}:{date}:{lang}`); the quiz language must be the digest's own.
default List<NewsSummary> generateNewsSummaries(String topic, Language language, String overrideApiKey);
default QuizGenerationResult generateQuizQuestions(String digestContent, Language language, DifficultyHint hint, String overrideApiKey);
```

Provider selected via `AI_PROVIDER` env var:
- `claude` (default, `matchIfMissing=true`) -> `ClaudeService` (`@Service`)
- `gemini` -> `GeminiService` (`@Component`)
- `openai` -> `OpenAiService` (`@Component`)

All providers: Redis cache check first, call NewsApiClient for articles, call AI API, cache result. Retry: MAX_RETRIES=2, RETRY_DELAY_MS=2000 (linear: `delay * attempt`).

#### Other Services

| Service | Responsibilities |
|---------|-----------------|
| `NewsApiClient` | Shared `@Component`. Fetches articles from News API, parses responses, builds context strings for AI prompts. Gracefully returns empty list if API key is blank. |
| `AuthService` | Register (BCrypt, auto-sets `emailVerified=true`; duplicate-email race caught as clean 400), login (AuthenticationManager, then hands off to the 2FA challenge when enabled), Google login policy (merging into a password-only account found by email clears the unproven password and revokes sessions — pre-account-hijack defense), token refresh (hashes tokens before lookup), logout, password reset (SHA-256 hashed tokens). Owns session issuance; ~290 lines. |
| `EmailVerificationChallengeService` | The emailed-code second factor: `start` (hashed challenge id + code, attempts budget, TTL, sends the mail), `verify` → `Verification` (`VERIFIED`/`INVALID_CODE`/`LOCKED`/`EXPIRED`, attempts remaining, reset hint, user), `resend`. Constant-time compare, atomic attempt decrement, lockout marks the challenge consumed. |
| `GoogleIdTokenVerifier` | Resolves a Google ID token via the tokeninfo endpoint and adds the audience + issuer checks Google does not do; returns a `GoogleIdentity(sub, email, name, emailVerified)` record. Fails closed without a client id. |
| `UserService` | Profile CRUD, preferences upsert, account deletion, unsubscribe (sets `deliveryEnabled=false`). |
| `NewsService` | Digest retrieval (paginated, ownership check) and digest generation for one user: `generate(user, listener)` returns a `DigestGeneration` (`GENERATED` / `ALREADY_EXISTS` / `NO_TOPICS` / `NOTHING_GENERATED`), round-robins the user's topics through `AiService` in the account's edition and stamps `content.language`. Bulk runs are `shared/digest/DigestBatch`, not this class. |
| `QuizService` | Quiz retrieval by digest ID (questions served as `QuizQuestionSet`, answer key stripped), quiz generation via AI (if not exists), quiz submission scoring (`QuizSubmitResponse`), paginated history (`QuizHistoryEntry`, page size 10). Stored quiz JSON is read back through `objectMapper.convertValue(..., QuizGenerationResult.class)` — no unchecked casts. |
| `AdminUserService` | User listing with search (`AdminUserSummary` rows), user detail (`AdminUserDetail`: profile, topics, metrics, recent digests/attempts). |
| `AdminDigestService` | Digest listing with pagination and filters (topic, userEmail); the synchronous admin triggers delegate to `DigestBatch` (optionally topic-filtered). |
| `AdminStatsService` | Stats aggregation (total users, emails sent, quiz completions), topic distribution, topic status per-topic stats, job status retrieval. |
| `AdminOperationsService` | Manual triggers: `DigestEmailBatch.sendAllUnsent()` (every unsent digest, no hour gate) and the 30-day cleanup job. |
| `AdminManualJobService` | Backs the async admin triggers (`POST /admin/generate-digests`, `/admin/send-emails`): returns 202 `JobTriggerResponse(status)` `started`/`already_running` immediately and runs the batch on a background executor. |
| `EmailService` | Renders and orchestrates mail: digest email (claim → render `digest-email.html` in the digest's edition → send → release the claim on failure → record provider id), 2FA-code and password-reset mails (`auth-email.html`, edition from the user's preference). |
| `EmailTransport` | The wire call: Resend HTTP with 3 attempts on 429/5xx, fail-fast on other 4xx, SMTP fallback when no Resend key. Returns the provider message id. |
| `EmailEventProcessor` | Resend webhook events: open/click stamp the digest, hard bounce/complaint disable delivery for the address. |
| `UnsubscribeTokenService` | HMAC-SHA256 signed tokens with configurable TTL (default 720 hours / 30 days) — `shared/security`. |
| `UserDetailsServiceImpl` | Implements Spring Security `UserDetailsService`, loads users by email. |
| `StudioService` | Manual, user-driven digest generation (via `DigestPipeline`, with live progress) and emailing of the latest unsent digest (Studio dashboard). `GET /studio/status` returns `StudioStatusResponse` (two `TaskStatus` snapshots converted from the Redis maps + `StudioOverview`). |
| `StudioTaskStatusService` | Tracks live Studio task status and context for `GET /studio/status`. |
| `LabBlogFetcher` | Fetches lab/company blog (RSS) content for digest sourcing. |
| `LabBlogRegistry` | Registry of lab blog feed URLs consumed by `LabBlogFetcher`. |
| `LlmKeyValidator` | Validates user-supplied (BYOK) provider API keys. |
| `DigestPipeline` | `shared/digest` — one user end to end: `NewsService.generate` then `QuizService.generateQuizForDigest`; returns digest status + quiz status. Used by the scheduled batch, admin triggers and Studio. |
| `DigestBatch` / `DigestEmailBatch` | `shared/digest` — the two subscriber-wide runs, built on `SubscriberBatch`: generation for every subscriber and the hourly delivery run (`sendDue`: hour gate, today-only, generate-if-missing) or the admin `sendAllUnsent`. Each batch owns its job status through `JobRunRecorder` (success with the result map, or FAILED + Sentry when the run dies). A quiet news day (every topic empty) is a *skip*, not a failure, so it cannot trip the partial-failure alert. |
| `SubscriberBatch` | `shared/batch` — chunked (500) runner over `deliveryEnabled` users: at most `maxPool + 2` tasks in flight (a semaphore, so the per-user timeout clock starts when the task can actually run), per-user timeout, a failing chunk preload is recorded and skipped rather than aborting the run, outcome tally (success/skipped/failed, sampled errors, errors by type) in one result shape. |
| `JobRunRecorder` | `shared/jobs` — `recordRun(jobName, tally, durationMs, extras)` builds the result map, records success and raises chunk / partial-failure notifications; `recordFailure(jobName, e)` records FAILED with the root cause. `JobStatusRegistry` and `JobFailureNotifier` live beside it. |

### Scheduled Jobs

| Job | Schedule | What it Does |
|-----|---------------|--------------|
| `DigestGenerationJob` | 05:00 Asia/Seoul (20:00 UTC) | `DigestBatch.runForAll()`: one run per **digest day** (05:00 KST → 05:00 KST, `shared/time/DigestDay`), worldwide — digest **and quiz** for every user with `deliveryEnabled=true` and preferences set (the same pipeline Studio and the admin trigger use) |
| `EmailSendJob` | Hourly (`:00`) | `DigestEmailBatch.sendDue()`: emails each user whose local hour is at or past their delivery hour (default 06:00; catch-up gate — DST spring-forward cannot skip a day) the digest day that was current at that hour, at most once per local day: a digest day that starts later the same local day (e.g. 16:00 in New York) waits for tomorrow. Claim-before-send prevents doubles. Generates the digest + quiz just in time only if the 05:00 run missed the user |
| `CleanupJob` | 00:00 UTC | Deletes digests/quizzes older than 30 days via `digestRepository.deleteByGeneratedAtBefore()` |
| `ExpiredAuthRowReaper` | - | Reaps expired refresh tokens, password-reset tokens, and email-verification codes |
| `JobFailureNotifier` | - | Surfaces/notifies on scheduled-job failures (alongside `JobStatusRegistry`) |

**Note on CleanupJob:** Deletes digests directly; quiz and quiz_attempt rows are removed via the database `ON DELETE CASCADE` constraints.

**Note on job results:** every batch run (scheduled or admin) reports the same shape through `JobRunRecorder` — `durationMs`, `usersProcessed` (attempted: for the email job, users past their delivery hour), `usersScanned` (every delivery-enabled user paged through), `chunks`, `chunkSize`, `sampleErrors` (`[{userEmail, message}]`) and `errorsByType` — plus per-run counters (`digestSuccess`/`digestFail`/`digestSkipped`/`quizSuccess`/`quizFail` + `topicFilter` for generation, `sentCount`/`failCount` for delivery). `frontend/src/types/admin.ts` `JobResult` is the mirror; the admin job cards render the counts, quiz totals, beat filter and duration.

### Security Configuration

- CSRF: disabled (stateless JWT API)
- Sessions: STATELESS
- JWT: HMAC-SHA256 access tokens (15 min) + refresh tokens (3-hour sliding idle timeout, single-use rotation, stored SHA-256-hashed in DB). Lifetime derives from `jwt.refresh-expiration` (env `JWT_REFRESH_EXPIRATION`, default 10800000 ms = 3h); the DB row, cookie max-age, and JWT expiry share this one source of truth
- `JwtAuthenticationFilter` extracts Bearer token from Authorization header
- Public paths: `/api/v1/auth/**`, `/api/v1/webhooks/**`, `/api/v1/user/unsubscribe`, `/actuator/health`, `/actuator/info`, `/api-docs/**`, `/swagger-ui/**`
- Admin paths: `/api/v1/admin/**` requires `ROLE_ADMIN` (via SecurityConfig URL matcher)
- Auth provider: `DaoAuthenticationProvider` with BCrypt
- CORS: configured via `CorsConfig` bean (dev: `localhost:5173`, prod: via `FRONTEND_URL` env var)
- Webhook security: HMAC-SHA256 Svix signature verification
- Redis caching uses `RedisTemplate` directly (manual get/set), NOT `@Cacheable` annotations

### Repository Methods

| Repository | Key Methods |
|------------|-------------|
| `UserRepository` | `findByEmail`, `findByGoogleId`, `existsByEmail`, `findByEmailContainingIgnoreCase`, `findByDeliveryEnabledTrue` |
| `UserPreferencesRepository` | `findByUserId`, `findByUserIdIn` |
| `DigestRepository` | `findByUserIdOrderByGeneratedAtDesc`, `findTop5ByUserIdOrderByGeneratedAtDesc`, `findByEmailProviderId`, `countByUserId`, `countByEmailSentAtAfter`, `deleteByGeneratedAtBefore`, pagination/filtering support |
| `QuizRepository` | `findByDigestId` |
| `QuizAttemptRepository` | `findByUserIdWithQuiz` (JPQL JOIN FETCH), `findByUserIdOrderByCompletedAtDesc`, `findTop5ByUserIdOrderByCompletedAtDesc`, `countByUserId`, `countByCompletedAtAfter`, `findAverageScoreByUserId` (JPQL AVG) |
| `RefreshTokenRepository` | `findByTokenHash`, `deleteByUserId` (V9: changed from `findByToken` to hash-based lookup) |
| `PasswordResetTokenRepository` | `findByTokenHash`, `deleteByUserId` |

### Configuration Profiles

| File | Active When | Key Behavior |
|------|-------------|-------------|
| `application.yml` | Always (base) | Port 8080, DDL=validate, Flyway on, Actuator health+info, Swagger on. Default active profile is `${SPRING_PROFILES_ACTIVE:prod}` — an UNSET profile boots the fail-fast `prod` profile (requires real secrets), so local dev must pass `-Dspring-boot.run.profiles=dev` (or `SPRING_PROFILES_ACTIVE=dev`) |
| `application-dev.yml` | `dev` profile | Localhost DB/Redis, debug logging, `ai.provider=${AI_PROVIDER:claude}` |
| `application-prod.yml` | `prod` profile | All values from env vars, Swagger off, INFO logging |
| `application-test.yml` | `test` profile | H2 in-memory DB, Flyway disabled, hardcoded test keys |

### Global Exception Handler

`@RestControllerAdvice` with handlers for: `ResourceNotFoundException`, `UnauthorizedException`, `IllegalArgumentException`, `MethodArgumentNotValidException`, `AccessDeniedException`, and catch-all `Exception`. Returns `ErrorResponse` with `status`, `message`, `errors` (Map), and `timestamp` fields.

---

## Frontend

### Package Dependencies

| Package | Version | Status |
|---|---|---|
| Vue | ^3.5.24 | Active |
| Vite | ^7.2.4 | Active |
| Pinia | ^3.0.4 | Active (no persistedstate plugin) |
| Vue Router | ^4.6.4 | Active |
| vue-i18n | ^11.4.10 | Active (composition API, runtime message compiler over JSON catalogs) |
| Axios | ^1.13.5 | Active |
| Tailwind CSS | ^4.1.18 | Active (v4 with CSS-based config) |
| TypeScript | ~5.9.3 | Active |
| `@headlessui/vue` | ^1.7.23 | **INSTALLED BUT NEVER USED** |
| Cypress | ^15.10.0 | Dev dependency |
| Vitest | ^4.0.18 | Dev dependency |

### Build Scripts

```json
"dev": "vite",
"build": "vue-tsc -b && vite build",
"preview": "vite preview",
"test:unit": "vitest",
"test:e2e": "cypress open",
"lint": "vue-tsc -b && eslint src tests"
```

**Note:** No `format` script or Prettier config — `lint` is project-mode type-checking (`vue-tsc -b`; a bare `vue-tsc --noEmit` checks nothing because the root tsconfig only holds references) plus a deliberately narrow ESLint set (`eslint.config.mjs`: `no-use-before-define` for the computed-TDZ trap; no style rules).

### Routing

| Path | Name | Component | Auth | Admin |
|------|------|-----------|------|-------|
| `/` | `home` | `HomeView` | No | No |
| `/login` | `login` | `LoginView` | No | No |
| `/register` | `register` | `RegisterView` | No | No |
| `/verify` | `verify-code` | `VerifyCodeView` | No | No | (email 2FA for email/password login) |
| `/reset-password` | `reset-password` | `ResetPasswordView` | No | No |
| `/onboarding` | `onboarding` | `OnboardingView` | Yes | No |
| `/dashboard/archive` | `archive` | `ArchiveView` | Yes | No |
| `/dashboard/quiz/:digestId` | `quiz` | `QuizView` | Yes | No |
| `/dashboard/quiz-history` | `quiz-history` | `QuizHistoryView` | Yes | No |
| `/dashboard/settings` | `settings` | `SettingsView` | Yes | No |
| `/admin` | - | - | Yes | Yes | (redirects to `/admin/dashboard`) |
| `/admin/dashboard` | `admin-dashboard` | `AdminDashboardView` | Yes | Yes |
| `/admin/digests` | `admin-digests` | `AdminDigestsView` | Yes | Yes |
| `/admin/users` | `admin-users` | `UsersView` | Yes | Yes |
| `/admin/users/:id` | `admin-user-detail` | `UserDetailView` | Yes | Yes |
| `/admin/stats` | `admin-stats` | `StatsView` | Yes | Yes |
| `/admin/audit` | `admin-audit` | `AuditLogView` | Yes | Yes |
| `/:pathMatch(.*)* ` | `not-found` | `NotFoundView` | No | No | (404 catchall) |

**Navigation guard:** `requiresAuth` redirects to `/login` (with a `redirect` query) if there is no session. `requiresAdmin` first re-syncs the role from `GET /user/me` (`authStore.syncRole()`), then redirects non-admins to the archive.

**404 route:** Catch-all route `/:pathMatch(.*)* ` redirects to `NotFoundView` component.

### Pinia Stores

#### `auth` Store

| State | Type |
|-------|------|
| `accessToken` | `string \| null` (in-memory only — never persisted) |
| `refreshTokenValue` | `string \| null` (the refresh token proper lives in an httpOnly cookie, not readable by JS) |
| `user` | `AuthUser \| null` (id, email, fullName, isAdmin) |

Actions: `login()`, `register()`, `googleLogin()`, `logout()`, `refreshToken()`, `setTokens()`, `clearTokens()`

Computed: `isAuthenticated` (derived from accessToken presence)

**Note:** The access token lives in memory only; the refresh token is an HTTP-only cookie (not readable by JS). On page refresh the in-memory token is gone, so the app re-establishes the session via a silent single-flight `POST /auth/refresh` on boot (covered by the App.vue splash).

#### `user` Store

State: `profile` (User | null), `preferences` (string[])
Actions: `fetchProfile()`, `updateProfile()`, `fetchPreferences()`, `updatePreferences()`

#### `news` Store

State: `digests` (Digest[]), `currentDigest`, `totalPages`, `currentPage`
Actions: `fetchDigests(page, size)`, `fetchDigest(id)`

#### `quiz` Store

State: `currentQuiz`, `quizHistory` (QuizAttempt[]), `currentScore`
Actions: `fetchQuiz(digestId)`, `submitQuiz(quizId, answers)`, `fetchHistory(page)`

#### `persistence` Store

State: `lastVisitedPage` (string), `selectedTopics` (string[])
Actions: `saveState()`, `restoreState()`

Note: Manages client-side session state for UI recovery (e.g., last visited archive page, onboarding selections).

No `admin` store exists. Admin views call their API endpoints directly. All features are unlocked for all users (subscription and tiers were removed in V22).

### API Service (`services/api.ts`)

Single Axios instance with:
- `baseURL`: `/api/v1` (relative path — routed by Vite dev proxy or nginx)
- 30s timeout
- **Request interceptor:** attaches `Authorization: Bearer <token>` from auth store
- **Response interceptor:** handles 401 with token refresh queue (prevents infinite loops via `_retry` flag and `failedQueue`). On refresh failure, clears tokens and redirects to login.

#### Endpoint Map

| Method | Frontend Call | Backend Endpoint |
|---|---|---|
| POST | `api.auth.login(email, password)` | `/auth/login` |
| POST | `api.auth.verifyCode(challengeId, code)` | `/auth/verify-code` |
| POST | `api.auth.resendCode(challengeId)` | `/auth/resend-code` |
| POST | `api.auth.register(data)` | `/auth/register` |
| POST | `api.auth.googleLogin(token)` | `/auth/google` |
| POST | `api.auth.forgotPassword(email)` | `/auth/forgot-password` |
| POST | `api.auth.resetPassword(token, newPassword)` | `/auth/reset-password` |
| POST | `api.auth.refresh()` | `/auth/refresh` (refresh token rides the httpOnly cookie, not an arg) |
| POST | `api.auth.logout()` | `/auth/logout` |
| GET | `api.user.getProfile()` | `/user/me` |
| PUT | `api.user.updateProfile(data)` | `/user/me` |
| GET | `api.user.getPreferences()` | `/user/preferences` |
| PUT | `api.user.updatePreferences(topics, delivery?)` | `/user/preferences` |
| DELETE | `api.user.deleteAccount()` | `/user/me` |
| PUT | `api.user.changePassword(currentPassword, newPassword)` | `/user/me/password` |
| GET | `api.apiKeys.list()` | `/user/api-keys` |
| POST | `api.apiKeys.save(provider, apiKey, currentPassword)` | `/user/api-keys` |
| DELETE | `api.apiKeys.delete(provider, currentPassword)` | `/user/api-keys/{provider}` |
| GET | `api.news.getDigests(page, size)` | `/news/digests` |
| GET | `api.news.search(q, page, size)` | `/news/search` |
| GET | `api.quiz.getQuiz(digestId)` | `/quiz/digest/{digestId}` |
| POST | `api.quiz.submitQuiz(quizId, answers)` | `/quiz/{quizId}/submit` |
| GET | `api.quiz.getHistory(page)` | `/quiz/history` |
| GET | `api.studio.getStatus()` | `/studio/status` |
| POST | `api.studio.generate()` | `/studio/generate` |
| POST | `api.studio.sendEmail()` | `/studio/send-email` |
| GET | `api.admin.getUsers(page, search)` | `/admin/users` |
| GET | `api.admin.getUser(id)` | `/admin/users/{id}` |
| GET | `api.admin.getStats()` | `/admin/stats` |
| GET | `api.admin.getTopicDistribution()` | `/admin/stats/topics` |
| GET | `api.admin.getTopicsStatus()` | `/admin/topics/status` |
| GET | `api.admin.getDigests(params)` | `/admin/digests` |
| POST | `api.admin.generateDigests(topics)` | `/admin/generate-digests` |
| POST | `api.admin.sendEmails()` | `/admin/send-emails` |
| POST | `api.admin.runCleanup()` | `/admin/cleanup` |
| GET | `api.admin.getJobsStatus()` | `/admin/jobs/status` |
| GET | `api.admin.getAuditLog(page, size)` | `/admin/audit-log` |

All 37 frontend API calls (across the `auth`, `user`, `apiKeys`, `news`, `quiz`, `studio`, and `admin` groups) have matching backend endpoints and are typed end to end (`src/types/{user,news,quiz,studio,admin,page}.ts`). The backend also exposes `POST /user/api-keys/validate` and `GET /news/digests/{id}`, which the SPA no longer calls.

### Components

**UI (11):** `BaseButton`, `BaseInput`, `BaseModal`, `BaseSpinner`, `ToastContainer`, `AppIcon` (inline stroke SVG icons; 8 names, every one rendered somewhere), `LanguageSelect` (edition dropdown — a native `<select>` overlaid invisibly on a styled label; `inline` variant for mastheads/footers, shrinking to the EN / KO code below `sm`, `row` variant for the sidebar foot), `PageMasthead` (kicker + `<i18n-t>` headline with an emphasised word; `display`/`compact` sizes), `ErrorState` (kicker/headline/body + retry), `PagerNav` (newer/older pager; `counterKeypath` picks the "page X of Y" copy so admin and archive share it), `SegmentedControl` (`defineModel` radiogroup with one `label`, used by the Settings theme picker)

**Auth (5):** `LoginForm`, `RegisterForm`, `AuthMasthead` (shared auth-page header with the edition link), `AuthColophon` (the dated ink-aside row under the auth forms), `MailSentPanel` ("check your inbox" state for reset/verify)

**Layout (4):** `DashboardLayout` (desktop sidebar + mobile drawer shell), `DashboardMenu` (nav links), `DashboardMenuFoot` (theme + edition switches, sign-out), `LegalLayout` (wrapper for the legal/privacy/terms/contact pages)

**Archive (3):** `ArchiveSearch` (full-text search box + results), `BeatFilter` (topic chips), `DigestIssue` (one digest rendered as an issue)

**Quiz (2):** `QuizQuestion`, `QuizResults`

**Studio (1):** `TaskProgress` (live generate/send progress bar driven by `GET /studio/status`)

**Admin (1):** `JobCard` (one job's state, last-run summary and optional detail line — quiz counts, beat filter, duration — on the admin dashboard)

**Settings (1):** `ApiKeyManager` (BYOK key management UI, embedded in Studio/Settings)

**Error Handling (1):** `ErrorBoundary` (global error boundary component, wraps App.vue)

**Note:** Admin views (`UsersView`, `UserDetailView`, `StatsView`, `AdminDashboardView`, `AdminDigestsView`) do NOT use `DashboardLayout` -- they have their own standalone layouts with no shared admin navigation.

### Composables

| Composable | Status | Notes |
|---|---|---|
| `useToast()` | Active | Module-level singleton. Used throughout views for notifications. |
| `useEnsureTimezone()` | Active | Captures/re-syncs the device timezone on authenticated app entry (wired in `DashboardLayout`) for timezone auto-follow. |
| `useTheme()` | Active | Light/dark theme state + toggle. |
| `useLocale()` | Active | Edition state (`en` \| `ko`): `locale`, `intlLocale` (BCP-47 for Intl), `locales`, `setLocale`. Persists only on explicit choice; keeps `<html lang>` + `document.title` in sync. |
| `useTopicLabels()` | Active | `topicLabel(name)`, `groupName(l1\|l2)`, `groupDescription(l1)` — render-time labels for the canonical topic taxonomy. |
| `useFocusTrap()` | Active | Traps focus within modals/drawers for keyboard accessibility. |
| `useFocusOnEnter()` | Active | Moves focus to a target element on mount/enter. |
| `useFormat()` | Active | `formatDate(x, 'full'\|'short'\|'stamp')`, `formatDateTime(x, 'short'\|'compact')`, `formatTime`, `formatNumber`, `formatDuration(ms)` — every `toLocale*String` / `Intl` call in the UI goes through here with `intlLocale`. |
| `useDisclosureSet()` | Active | `toggle(key)` / `isOpen(key)` over a reactive `Set` — the open/closed state of the onboarding + Settings beat accordions and the archive beat filter. |
| `usePoller()` | Active | `{ start, stop, active }` around an async `tick(isCurrent)`; the next tick is armed only after the previous response lands, with `maxAttempts` / `maxConsecutiveFailures` caps. Studio status and admin job status. |
| `usePagedAdminList()` | Active | `load(page)` + `{ rows, page, totalPages, loading, error }` for the admin users/digests/audit tables; each view supplies its `fetcher`. |
| `useTopicSelection()` | Active | Chosen leaf topics, unfolded L1 domains and per-group counts for the beat picker (onboarding + Settings). |

### Internationalization (i18n)

The UI ships in two editions, **English (default) and Korean**, via **vue-i18n v11** (composition mode). Digest *content* (headlines, TL;DRs, quiz questions) and the daily email are written by the backend in the **account's edition** (`user_preferences.language`, set at onboarding from the UI locale and in Settings → Edition) — a separate, server-side choice from the device-level UI locale. The Settings dropdown sets both; the masthead/sidebar dropdowns change only the UI.

- **Catalogs:** `src/i18n/locales/<en|ko>/<namespace>.json`, auto-discovered by `import.meta.glob` in `src/i18n/index.ts` and exposed as `t('<namespace>.<key>')`. Namespaces map to feature areas (`common`, `home`, `layout`, `topics`, `auth`, `archive`, `quiz`, `settings`, `studio`, `onboarding`, `admin`, `legal`). Both editions must ship the same key tree — `src/__tests__/i18n/useLocale.test.ts` asserts it. `fallbackLocale: 'en'` is a safety net, not a plan.
- **Detection order:** explicit choice in `localStorage['curio:locale']` → `navigator.languages` (first `en`/`ko` match) → `en`. A browser-detected locale is never written to storage; only `setLocale()` persists.
- **Document sync:** `useLocale.ts` stamps `<html lang>` and `document.title` at boot (`main.ts` calls `syncDocumentLocale()` before mount, like the stored-theme pre-paint) and on every switch. `:lang(ko)` CSS and the Noto KR font fallbacks key off that attribute.
- **Switch points:** `LanguageSelect` dropdown in the landing masthead + footer, auth-page mastheads, `LegalLayout` header, the onboarding strip, the dashboard sidebar/drawer foot (next to Lights), and a plain `<select>` in Settings → Edition.
- **Topic taxonomy:** names in `data/topics.ts` are canonical ids (stored in `user_preferences.topics`, carried in digest JSON) — never translated at the data layer. `useTopicLabels()` maps them at render time from `topics.json` (`leaves` keyed by canonical name, `groups` keyed by L1/L2 id).
- **Locale-sensitive formatting:** views pass `intlLocale` (`en-US` / `ko-KR`) to `toLocale*String` instead of a hard-coded `'en-US'`.
- **Message-format gotchas:** vue-i18n compiles every string — `{name}` interpolates, `|` splits plural forms, `@` starts a linked message; a literal `@`/`|` must be written `{'@'}` / `{'|'}`. Inline markup inside a sentence goes through `<i18n-t scope="global">` with named slots.
- **Tests:** Vitest installs the app's i18n singleton for every mount and resets it to `en` before each test (`__tests__/setup.ts`); Cypress pins `curio:locale=en` in `tests/e2e/support.ts` unless a test already chose an edition (`i18n.cy.ts` covers switching + persistence).

### TypeScript Types

Defined in `types/user.ts`, `types/news.ts`, `types/quiz.ts`. Admin API responses for digests and topics are typed; user list responses use inline local interfaces (`AdminUser`, `UserDetail`).

Unused type: `LoginRequest` (defined in `types/user.ts` but never imported).

### Styling

The design system is an editorial "newsprint" theme, not the old indigo/Manrope scaffold.

- **Tailwind CSS v4**, imported via `@import "tailwindcss"` in `src/assets/styles/main.css` (loaded by `main.ts`). Design tokens live in an `@theme inline` block that maps Tailwind color utilities (`bg-paper`, `text-ink`, `text-signal`, …) onto runtime CSS variables, so the palette flips between light and dark by redefining those variables under `:root[data-theme='dark']` / `:root.dark` (kept in sync by `useTheme.ts`).
- **Palette:** warm paper (`--paper #f3ede1`) / ink (`--ink #14130f`) neutrals with a signal-orange accent (`--signal #ff4a1c`) and a leaf green — no indigo primary. Components use these editorial tokens throughout.
- **Fonts** load from Google Fonts in `index.html`: **Fraunces** (display, `--font-display`), **Inter Tight** (body, `--font-body`), and **JetBrains Mono** (kickers/metadata, `--font-mono`), each with a **Noto Serif KR / Noto Sans KR** fallback for Hangul (served as unicode-range subsets, so English readers never download them). Unlayered `:root:lang(ko)` rules at the end of `main.css` drop the synthesized italic (Hangul has none — emphasis keeps colour and gains weight), set mono labels at a tighter 0.04em tracking, and scale Hangul down with `font-size-adjust` (Hangul fills the em box, so at equal px it reads a size or two larger than Latin): `0.5` on body text (×0.92), `0.4` on `.display-headline` (Fraunces ×0.92, Noto Serif KR ×0.78), `from-font` on other `.font-display` text (Noto Serif KR ×0.85), none on mono. Because it scales the computed size, every `clamp()`/px utility follows with no per-view overrides; each font-family class states its own value since it inherits. There is no Manrope.
- `tailwind.config.js` (v3-style) is still present and in use for `darkMode: 'class'` and the `content` globs; its `primary #6366F1` / `secondary #F59E0B` colors are legacy leftovers that no component references.

---

## Integration

### API Contract Alignment

**All frontend API calls have matching backend endpoints.** Zero mismatches in paths, HTTP methods, or DTO field names.

All typed DTOs (`AuthResponse`, `UserResponse`, `DigestResponse`, `QuizResponse`) are field-compatible between TypeScript and Java (Jackson serializes UUID as string, LocalDateTime as ISO string).

### Authentication Flow

1. Frontend keeps the `accessToken` in memory only; the refresh token is held in an HTTP-only cookie (not readable by JS)
2. Every API request attaches `Authorization: Bearer <token>` via Axios interceptor
3. On 401, the response interceptor triggers token refresh via `POST /auth/refresh`
4. Concurrent 401s are batched via a `failedQueue` to prevent duplicate refresh calls
5. On refresh failure, tokens are cleared and user is redirected to login

### Google OAuth Flow

1. Frontend uses Google Identity Services (`window.google.accounts.id`) to get an ID token
2. Frontend sends the token via `POST /api/v1/auth/google`
3. Backend verifies the token by calling Google's `tokeninfo` REST API
4. Backend upserts the user and returns JWT tokens

There is NO Spring OAuth2 callback flow (`/oauth2/callback`) and no `OAuth2SuccessHandler` class.

### Pagination

Backend uses Spring Data `Page` format (`content`, `totalPages`, `totalElements`, `number`, `size`). Frontend types correctly match this structure.

### Backend-Only Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/user/unsubscribe?token=` | Email unsubscribe link -- returns HTML directly |
| `POST /api/v1/webhooks/email` | Resend server-to-server webhook |

These are correctly not consumed by the frontend SPA.

---

## Infrastructure & DevOps

### Docker

**Frontend Dockerfile:** Multi-stage (`node:22-alpine` build -> `nginx:alpine` runtime). Non-root `nginx` user. Port 80. Build arg `VITE_GOOGLE_CLIENT_ID` only (`VITE_API_URL` is no longer used — the frontend uses relative `/api/v1` paths).

**Backend Dockerfile:** Multi-stage (`eclipse-temurin:21-jdk-alpine` build -> `eclipse-temurin:21-jre-alpine` runtime). Exploded JAR layout. Non-root `appuser` (UID 1001). Port 8080.

**nginx.conf:** Gzip enabled, 1-year immutable cache for `/assets/`, SPA fallback, API reverse proxy to `backend:8080`. This eliminates CORS in Docker/prod since frontend and API share the same origin.

### Docker Compose (Split Architecture)

Infrastructure is split into two compose files for local dev vs production:

**`docker-compose.infra.yml` — Local dev (infra only):**

| Service | Image | Host Port | Health Check |
|---------|-------|-----------|-------------|
| postgres | `postgres:16-alpine` | 5432 | `pg_isready` every 10s |
| redis | `redis:7-alpine` | 6379 | `redis-cli ping` every 10s |

Run app code natively (`npm run dev` + `mvn spring-boot:run`) for HMR and fast iteration. No Docker network — services exposed to localhost.

**`docker-compose.yml` — Production/staging (full stack):**

| Service | Image | Exposed Port | Health Check | Resource Limits | Notes |
|---------|-------|-------------|-------------|-----------------|-------|
| postgres | `postgres:16-alpine` | Internal only | `pg_isready` every 10s | 512M / 1 CPU | Requires `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` env vars |
| redis | `redis:7-alpine` | Internal only | `redis-cli -a $REDIS_PASSWORD ping` every 10s | 256M / 0.5 CPU | Password required for authentication |
| backend | `curio-backend:latest` | Internal only | `wget /actuator/health` every 30s | 1G / 2 CPU | `SPRING_PROFILES_ACTIVE=prod` |
| frontend | `curio-frontend:latest` | 80 (configurable) | `wget /` every 30s | 128M / 0.5 CPU | Nginx reverse proxy |

Production features: Redis password required, resource limits on all services, `prod` Spring profile, supports pre-built images via `BACKEND_IMAGE`/`FRONTEND_IMAGE` env vars. Only the frontend port is exposed to the host.

Named volumes for postgres and redis data. Shared `curio-network` bridge. Environment variables required: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `JWT_REFRESH_SECRET`, `CLAUDE_API_KEY`, `RESEND_API_KEY`, `RESEND_WEBHOOK_SECRET`, `NEWS_API_KEY`, `FRONTEND_URL`, `BACKEND_URL`, `UNSUBSCRIBE_SECRET`, `API_KEY_ENCRYPTION_KEY`. Optional passthroughs: `GOOGLE_CLIENT_ID` (Google sign-in; ID-token flow needs no client secret — there is no `GOOGLE_CLIENT_SECRET`, and the `spring-boot-starter-oauth2-client` dependency has been dropped), `AI_PROVIDER` (default `claude`) plus `GEMINI_API_KEY`/`OPENAI_API_KEY`, and `SENTRY_DSN` / `SENTRY_ENVIRONMENT` / `SENTRY_TRACES_SAMPLE_RATE` (the backend service passes these through so `.env.prod` can wire Sentry).

### Environment Variables

The full variable reference (what each var is, why it's needed, prod-required vs optional) lives in **[`ENV_VARIABLES.md`](ENV_VARIABLES.md)** — the single source of truth. In brief, how the profiles consume them:

- **Backend dev (`application-dev.yml`):** hardcoded localhost DB/Redis and placeholder secret defaults (labeled "change-in-production"); almost no env vars needed to boot.
- **Backend prod (`application-prod.yml`):** every value comes from env vars with no secret defaults, so a missing required secret fails fast at boot.
- **Frontend:** `VITE_GOOGLE_CLIENT_ID` (Docker build arg / `.env.local`) and optional `VITE_SENTRY_DSN`. `VITE_API_URL` is not used — the SPA calls relative `/api/v1` paths routed by the Vite dev proxy (local) or nginx (Docker/prod).

---

## Testing

### Backend Test Inventory

39 test classes / 274 tests (as of 2026-09-17) including unit, integration, and architecture tests. All tests pass, including ArchUnit enforcement of hexagonal architecture rules.

| Test Class | Type | What It Tests |
|---|---|---|
| `CurioApplicationTests` | Smoke | Spring context loads |
| `HexagonalArchitectureTest` | Architecture | ArchUnit rules (ports ≠ adapters, only adapters/repositories touch repositories, controllers depend on inbound ports, adapters isolated, no cycles among `shared.*` packages) |
| `AuthServiceTest` | Unit | Register/login/refresh/logout/password-reset paths, token rotation, used-token rejection, 2FA hand-off mapping, Google merge policy (unverified email rejected, password cleared + sessions revoked on merge) |
| `EmailVerificationChallengeServiceTest` | Unit | Challenge start persists hashes not plaintext, 6-digit code, mail failure still issues the challenge, attempts/lockout/expiry outcomes and copy, resend rotates code + expiry |
| `GoogleIdTokenVerifierTest` | Unit | tokeninfo success, bare/https issuer, audience mismatch, blank client id fails closed, missing sub/email, empty response, transport error |
| `ClaudeServiceTest` | Unit | Cache hit/miss, API calls, JSON parsing, code-fence stripping, error handling, retry, quiz generation |
| `GeminiServiceTest` | Unit | Same matrix as Claude + API-key-in-URL verification |
| `OpenAiServiceTest` | Unit | Same matrix as Claude + Bearer auth header verification |
| `NewsApiClientTest` | Unit | Blank API key guard, article parsing, error handling, context building |
| `NewsServiceTest` | Unit | Digest generation statuses (`GENERATED`/`ALREADY_EXISTS`/`NO_TOPICS`/`NOTHING_GENERATED`), edition stamp, filtering, pagination |
| `QuizServiceTest` | Unit | Quiz fetch/generate, ownership enforcement, scoring, per-question feedback, stored-JSON round-trip via `convertValue`, answer-key stripping, history mapping |
| `QuizAttemptRecorderTest` | Unit | REQUIRES_NEW attempt persistence, concurrent-first-submit recovery, "better score wins" |
| `UnsubscribeTokenServiceTest` | Unit | HMAC-SHA256 token roundtrip, expiry enforcement, tampering detection (`shared/security`) |
| `UserApiKeyServiceTest` | Unit | BYOK key save (encrypt + live-validate), masked preview, rotation staleness |
| `UserServicePasswordTest` | Unit | In-session password change, session revocation on change |
| `AuthControllerIntegrationTest` | API | Forgot password, reset password, Google login |
| `AdminControllerSecurityIntegrationTest` | Security | Anonymous rejected, regular user rejected, admin user allowed |
| `AdminOperationsServiceTest` | Unit | Delegation to `DigestEmailBatch.sendAllUnsent()` + 30-day cleanup trigger |
| `AdminStatsServiceTest` | Unit | Stats aggregation, topic distribution, topic status |
| `AdminUserServiceTest` | Unit | User roster rows and detail record (metrics rounding, recent digests/attempts) |
| `StudioServiceTest` | Unit | Redis status map → `TaskStatus` record (unset keys omitted, unknown keys tolerated), overview assembly |
| `AuditLogServiceTest` | Unit | Admin-action audit record write + paginated read |
| `UserControllerPasswordIntegrationTest` | API | Password change functionality |
| `UserControllerUnsubscribeIntegrationTest` | API | Two-step unsubscribe pages: confirm (GET, no state change), done (POST), invalid token, token HTML-escaped in the hidden input |
| `WebhookControllerSignatureIntegrationTest` | Security | Invalid/valid HMAC signature verification |
| `ApiKeyCipherTest` | Unit | API key encryption/decryption |
| `JwtSecretsValidatorTest` | Unit | Boot-time JWT secret validation (length, distinctness, non-placeholder) |
| `SingleFlightTest` | Unit | In-process per-key single-flight lock (one cold-cache call per topic+date) |
| `SubscriberBatchTest` | Unit | Chunked runner: include predicate, outcome tally, per-user timeout → failed, a queued user is not timed out before it can run, chunk preload failure recorded and skipped, sampled errors / errors by type in the result map |
| `JobRunRecorderTest` | Unit | Result map assembly + success record, chunk / partial-failure notifications, FAILED with root cause |
| `AiPromptsTest` | Unit | Summary + quiz prompt text: edition blocks, difficulty mix, source context placement |
| `DigestPipelineTest` | Unit | One-user pipeline: status pass-through, quiz generated / failed / not attempted, listener hooks |
| `DigestBatchTest` | Unit | Subscriber-wide generation: counters, topic filter, job record via `JobRunRecorder`, a quiet news day counts as skipped (no partial-failure alert), FAILED recorded when the run dies |
| `DigestEmailBatchTest` | Unit | Hourly gate (at-or-past delivery hour in the user's zone, fixed `Clock`), today-only, generate-if-missing (a thrown generation error is a failed send), claim-before-send, `sendAllUnsent` ignores the gate, FAILED recorded when the run dies |
| `EmailTransportTest` | Unit | Resend retry on 429/5xx, fail-fast on 4xx, SMTP fallback id, missing-key behaviour |
| `EmailEventProcessorTest` | Unit | open/click stamp the digest; hard bounce/complaint disable delivery; unknown events ignored |
| `LanguageTest` | Unit | Edition parsing: lenient `fromCode` for stored values, strict `isSupportedCode` for API input, digest-content stamp with English fallback |
| `EmailServiceRenderTest` | Unit | Renders the real digest template through the real `messages*.properties` (no Spring context): Korean vs English chrome, `<html lang>`, generic-reader fallback, quiz block omission, localized subject |
| `UserServicePreferencesTest` | Unit | `language` on PUT /preferences: lowercased on store, unchanged when omitted, rejected when unsupported, exposed on GET with English default |

### Frontend Test Inventory

**Unit tests (Vitest): 42 test files / 214 tests** in `src/__tests__/` (as of 2026-09-17)
- Components: BaseButton, BaseInput, BaseModal, LoginForm, RegisterForm, QuizQuestion, QuizResults, LanguageSelect, PageMasthead, ErrorState, PagerNav, SegmentedControl, AuthMasthead, AuthColophon, DashboardLayout, DashboardMenu, ArchiveSearch, BeatFilter, DigestIssue, TaskProgress, JobCard
- Views (both editions — each mounts in English, switches to Korean, asserts translated copy and unchanged English): HomeView, LoginView, ArchiveView, SettingsView, StatsView, LegalViews, StudioView, AdminDashboardView
- Stores: Pinia store tests (auth, user, news, quiz)
- Composables / i18n: `useToast`, `useTopicLabels`, `useFormat`, `usePoller`, `usePagedAdminList`, `useTopicSelection`, `useDisclosureSet`, `useLocale` + catalog key-tree parity (every key in `en` exists in `ko` and vice versa)
- Utils: `safeUrl`

**E2E tests (Cypress): 10 spec files / 67 tests** (as of 2026-09-17; `tests/e2e/support.ts` pins the edition to English before boot)

Session mocking is centralized in `tests/e2e/helpers.ts`: the app keeps the access token in memory and re-establishes sessions via a silent `POST /auth/refresh`, so specs seed the localStorage `user` (`withAuth()`) and stub the refresh call (`stubSession()`) — **never seed localStorage tokens**.

| Spec | Coverage |
|---|---|
| `auth.cy.ts` | Authentication flows (login, register, token refresh) |
| `onboarding.cy.ts` | Onboarding flow (topic selection, preferences) |
| `archive.cy.ts` | Digest archive view, pagination, full-text search, digest detail |
| `quiz.cy.ts` | Quiz view, question navigation, submission, scoring |
| `settings.cy.ts` | Settings page, profile updates, preferences |
| `not-found.cy.ts` | 404 catchall route, navigation |
| `admin.cy.ts` | Admin dashboard, users list, user detail, stats, digest browser, audit log |
| `reset-password.cy.ts` | Password reset flow |
| `i18n.cy.ts` | Edition dropdown in the landing masthead and dashboard sidebar, `<html lang>` + title, persistence across reload and routes |
| `ui-screenshots.cy.ts` | Visual screenshots (7 pages desktop, 3 mobile) -- no assertions, screenshot capture only |

Coverage gaps and test to-dos are tracked in the private working notes (`docs-internal/CODEBASE_NOTES.md`).

---

## Hexagonal Architecture (Implemented)

The backend was migrated to hexagonal (ports & adapters) architecture on 2026-03-04 across four phases. Architecture documents in `docs/architecture/`:

| Document | Content |
|----------|---------|
| `ADR-001-hexagonal-architecture.md` | Decision record, rationale, and as-built caveat |

**Implementation Status:** COMPLETE (as of 2026-03-04)

The backend has been refactored into hexagonal (ports & adapters) architecture with 7 feature packages (auth, user, news, quiz, studio, admin, shared). Each package contains its own controller, services (implementing inbound ports), entities, repositories, DTOs, and adapters. All inbound ports are defined as interfaces in `port/in/`, all outbound ports in `port/out/`, and all JPA adapters in `adapter/persistence/`. ArchUnit tests enforce four key rules: ports cannot depend on adapters, services cannot import repositories directly, controllers must depend on inbound port interfaces, and adapters cannot depend on each other. All backend tests pass including ArchUnit validation.
