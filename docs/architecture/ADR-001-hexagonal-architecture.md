# ADR-001: Adopt Hexagonal Architecture for Curio Backend

**Status:** Implemented

**Date:** 2026-03-04

> **As built:** the per-operation CQRS ports this ADR's rationale sketches (`RegisterUserUseCase`,
> `GetDigestsQuery`, and similar one-method interfaces) were consolidated into coarser
> per-feature inbound ports in the implementation — `AuthUseCase`, `UserUseCase`, `NewsUseCase`,
> `QuizUseCase`, `StudioUseCase`, and the admin `Admin*UseCase` set. The hexagonal boundaries
> (ports in `port/in` and `port/out`, JPA adapters in `adapter/persistence`) hold as described;
> only the port granularity differs from the examples below.

---

## Context

The Curio backend is a Spring Boot 3.2 application organized as a traditional layered architecture: controllers call services, services call repositories. All classes live under a flat `com.curio` package with sub-packages by technical role (`controller/`, `service/`, `repository/`, `entity/`, `dto/`, `config/`, `security/`, `scheduler/`). As the codebase has grown through the MVP sprint, several concrete pain points have emerged.

### Pain Point 1: Services with excessive repository dependencies

`AdminService` injects four repositories directly (`UserRepository`, `UserPreferencesRepository`, `DigestRepository`, `QuizAttemptRepository`). It reaches across every aggregate in the system to assemble user listings, user detail views, statistics, and topic distributions. There is no domain boundary preventing a single service from querying any table it wants, which makes the class a cross-cutting data aggregator rather than a focused domain component.

### Pain Point 2: God-class services mixing too many responsibilities

`AuthService` is 245 lines and handles seven distinct operations: email/password registration, email/password login, Google OAuth login (including direct HTTP calls to Google's `tokeninfo` endpoint), JWT token refresh, logout, password reset request, and password reset execution. It depends on eight collaborators (three repositories, `PasswordEncoder`, `JwtTokenProvider`, `AuthenticationManager`, `EmailService`, `RestTemplateBuilder`). This concentration of responsibilities makes the class difficult to test in isolation, hard to reason about, and risky to modify.

### Pain Point 3: Mixed read and write paths in domain services

`NewsService` combines read-only query methods (`getDigests`, `getDigest`) with write-heavy generation logic (`generateDigestForUser`, `generateDigestsForAllUsers`). The generation path orchestrates user preference lookups, AI API calls via `AiService`, and digest persistence, while the read path is simple repository delegation. Grouping these in one class means changes to the generation pipeline risk affecting the query path, and transactional boundaries (`@Transactional` vs. `@Transactional(readOnly = true)`) are mixed within a single class.

### Pain Point 4: Flat package structure with no domain boundaries

All services live in `com.curio.service`, all entities in `com.curio.entity`, and all repositories in `com.curio.repository`. There is no structural indication of which classes belong to which domain concept (authentication, news delivery, quiz engagement, administration). Any service can freely import any repository or entity, and nothing in the package structure communicates architectural intent or enforces boundaries.

### Pain Point 5: Infrastructure concerns leak into domain logic

`AuthService` directly constructs and calls a `RestTemplate` to verify Google ID tokens. `NewsService` depends on `AiService` (an interface, which is good) but the implementations (`ClaudeService`, `GeminiService`, `OpenAiService`) contain HTTP client construction, JSON parsing, retry logic, and Redis caching all in one class. There is no clear separation between what the domain needs (generate summaries for a topic) and how that need is fulfilled (HTTP call to Claude API, cache in Redis, retry on failure).

---

## Decision

Adopt **Hexagonal Architecture** (Ports & Adapters) for the Curio backend. The codebase will be restructured into three concentric layers:

1. **Domain Core** -- Pure business logic and domain models with zero framework dependencies.
2. **Ports** -- Interfaces that define how the domain communicates with the outside world (inbound use cases and outbound dependencies).
3. **Adapters** -- Concrete implementations that connect ports to infrastructure (Spring MVC controllers, JPA repositories, HTTP clients, email providers).

The migration will be incremental, proceeding module by module without breaking the existing REST API contract.

---

## Rationale

Each benefit below addresses a specific pain point identified in the Context section.

### Enforced dependency direction (addresses Pain Point 4 & 5)

Hexagonal architecture enforces a strict rule: dependencies point inward. Domain code never imports infrastructure code. This is enforced structurally through package boundaries and can be validated with tools like ArchUnit. The flat package structure is replaced with domain-oriented modules (`auth`, `news`, `quiz`, `admin`) where each module's internal structure makes the architecture visible.

### Focused domain services via use-case ports (addresses Pain Point 2)

Instead of a single `AuthService` handling seven operations, each operation becomes a distinct inbound port (e.g., `RegisterUserUseCase`, `LoginUseCase`, `GoogleLoginUseCase`, `RefreshTokenUseCase`, `ResetPasswordUseCase`). Each use case has a single implementation class with a narrow set of dependencies, making classes small, testable, and independently modifiable.

### Separated read and write paths (addresses Pain Point 3)

Inbound ports naturally separate queries from commands. `NewsService` splits into query ports (`GetDigestsQuery`, `GetDigestQuery`) and command ports (`GenerateDigestCommand`). Each port implementation has a focused transactional context and a clear single responsibility.

### Bounded repository access (addresses Pain Point 1)

Outbound ports define only the data access operations that a specific domain module needs. The `admin` module declares its own outbound port (e.g., `AdminQueryPort`) that exposes only the aggregated queries it requires, rather than directly importing four JPA repositories. This makes cross-module data access explicit and auditable.

### Infrastructure isolation via outbound adapters (addresses Pain Point 5)

Google token verification, AI API calls, Redis caching, and email sending are implemented as outbound adapters behind outbound port interfaces. The domain says "verify this Google token" through a port; the adapter handles HTTP, retry, and error translation. Swapping infrastructure (e.g., replacing Resend with SendGrid, or adding a new AI provider) means writing a new adapter without touching domain code.

### Improved testability

Domain logic can be unit-tested with plain Java (no Spring context, no mocking frameworks for infrastructure). Adapters can be integration-tested independently. The current test suite, which relies on H2 and mocked HTTP layers, can be supplemented with fast, reliable domain tests.

---

## Consequences

### Positive

- **Clear module boundaries** make the codebase navigable and reduce cognitive load for new contributors.
- **Independent deployability** becomes feasible in the future if any module needs to be extracted into a separate service.
- **Faster test suites** from pure domain unit tests that do not require Spring context startup.
- **Safer refactoring** because changes to infrastructure adapters cannot break domain logic and vice versa.
- **Explicit architectural intent** that is visible in the package structure rather than in documentation alone.

### Negative

- **Increased file count.** Port interfaces, use-case classes, and adapter classes add boilerplate compared to the current flat structure. A module that previously had 1 service class may now have 3-5 files.
- **Learning curve.** Team members unfamiliar with hexagonal architecture need onboarding time to understand the port/adapter pattern and dependency rules.
- **Migration effort.** The refactoring must be done incrementally to avoid breaking the API contract. During migration, the codebase will temporarily contain both old-style and new-style modules.
- **Potential over-engineering for MVP.** Curio is currently a 2-week sprint project. The architectural overhead is justified only if the project continues to grow beyond MVP.
- **Indirection overhead.** Reading a request flow requires tracing through controller, inbound port, use-case implementation, outbound port, and adapter. This is more hops than the current controller-service-repository chain.

---

## Alternatives Considered

### Alternative 1: Keep Current Layered Architecture

Maintain the existing `controller → service → repository` structure and address pain points through code review discipline and naming conventions.

**Rejected because:** The pain points are structural, not stylistic. Code review cannot prevent a service from importing any repository it wants. The flat package structure provides no guard rails, and discipline-based approaches degrade as team size and codebase complexity grow.

### Alternative 2: CQRS Only

Adopt Command Query Responsibility Segregation to separate read and write paths without the full hexagonal restructuring.

**Rejected because:** CQRS addresses Pain Point 3 (mixed reads and writes in `NewsService`) but does not address Pain Points 1, 2, 4, or 5. It does not enforce dependency direction, does not isolate infrastructure, and does not provide domain module boundaries. It solves one symptom without addressing the underlying structural issues.

### Alternative 3: Feature-Based Package Structure Only

Reorganize packages by feature (`com.curio.auth`, `com.curio.news`, `com.curio.quiz`, `com.curio.admin`) while keeping the layered approach within each feature.

**Rejected because:** Feature packages improve navigability (addressing Pain Point 4) but do not enforce dependency direction. A feature module can still import infrastructure directly, services can still become god classes, and there is no structural mechanism to isolate domain logic from framework concerns. This is a useful first step but insufficient as a target architecture.

---

## Implementation Plan

The migration follows four phases, executed incrementally to maintain API stability throughout.

### Phase 1: Package Restructuring

Reorganize into feature packages (`auth`, `user`, `news`, `quiz`, `admin`, `shared`), each with internal `service/`, `port/in`, `port/out`, and `adapter/persistence/` sub-packages. Move existing classes into the new structure with minimal code changes. Validate with ArchUnit rules.

### Phase 2: Port Extraction

Define inbound port interfaces (use cases) and outbound port interfaces (repository, external service). Refactor existing services into use-case implementations that depend on ports rather than concrete infrastructure.

### Phase 3: Adapter Implementation

Extract infrastructure concerns (JPA repositories, HTTP clients, Redis caching, email sending) into adapter classes that implement outbound ports. Wire adapters via Spring dependency injection.

### Phase 4: Validation and Cleanup

Add ArchUnit tests to enforce dependency rules permanently. Remove legacy code paths. ArchUnit tests run automatically during `mvn test`.

---

## Related Documents

- [ARCHITECTURE.md](../ARCHITECTURE.md) -- Current as-built system architecture
- [CODEBASE.md](../CODEBASE.md) -- Whole-codebase map, including the per-feature package trees

> Earlier drafts referenced companion planning docs (`hexagonal-package-design.md`, `hexagonal-test-strategy.md`, `migration-plan.md`, and others). Those described a centralized `domain/`/`application/`/`adapter/` layout that was not what shipped, and have been removed; this ADR plus the as-built caveat above is the surviving record of the decision.
