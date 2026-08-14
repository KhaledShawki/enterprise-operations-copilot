# EOC Frontend Architecture

**Product:** Enterprise Operations Copilot (EOC)
**Version:** 0.1
**Status:** Baseline architecture
**Applies to:** `apps/web`
**Backend baseline:** `main@d9b719e`
**Last updated:** 2026-08-14

---

## 1. Purpose

This document defines **how the EOC Angular frontend is engineered**.

It complements:

```text
docs/frontend/UI_ENGINEERING_GUIDELINES.md
```

The split is deliberate:

```text
ARCHITECTURE.md
→ code structure, dependencies, state, routing, API/auth boundaries,
  testing, runtime configuration, and architecture evolution

UI_ENGINEERING_GUIDELINES.md
→ product behavior, state honesty, information hierarchy,
  accessibility, visual semantics, content, and interaction rules
```

This architecture is progressive:

> Establish boundaries early. Introduce abstractions only when demonstrated complexity or repetition justifies them.

Working software takes priority over speculative infrastructure.

---

# 2. Goals

The frontend should optimize for:

1. complete vertical slices early
2. clear feature/infrastructure boundaries
3. state in the smallest correct scope
4. backend-authoritative data and authorization
5. deep-linkable operational workflows
6. fast tests below the browser-E2E layer
7. progressive reuse instead of speculative frameworks
8. measurable performance and quality
9. replaceable integration boundaries
10. maintainable growth without ceremonial layering

---

# 3. Technology Baseline

Initial baseline:

```text
Angular 22
strict TypeScript
standalone components
zoneless change detection
Angular Router
Angular HttpClient
Angular Signals
RxJS
Vitest
npm
```

Rules:

- EOC-authored application and feature architecture MUST use standalone Angular APIs.
- EOC-authored application and feature architecture MUST NOT introduce `NgModule` patterns.
- Compatibility with third-party Angular libraries that expose NgModule-based integration APIs is allowed when necessary and does not justify introducing NgModule-based EOC architecture.
- The application MUST remain zoneless.
- `zone.js` SHOULD NOT be added.
- Signals are the default synchronous reactive primitive.
- RxJS is used primarily for asynchronous/event composition.
- Dependencies require a current product or engineering need.
- The repository SHOULD pin a Node version compatible with the selected Angular release.
- The npm lockfile is authoritative for reproducible installs.

---

# 4. Repository Placement

The frontend lives at:

```text
apps/web/
```

It is a peer application in the repository, not a Maven module.

```text
enterprise-operations-copilot/
├── apps/
│   └── web/
├── deployment/
├── docs/
│   └── frontend/
├── modules/
├── services/
└── pom.xml
```

The frontend uses Node/npm and an independent CI job. Backend verification remains Maven-based.

Do not introduce Nx, Turborepo, microfrontends, multiple Angular applications, or a shared npm-package graph without a demonstrated need.

---

# 5. Application Structure

Organize **feature first, concern second**.

Conceptual shape:

```text
apps/web/src/app/
├── app.ts
├── app.config.ts
├── app.routes.ts
├── shell/
├── platform/
├── features/
│   ├── receivables/
│   └── copilot/
└── ui/
```

Directories are created when code needs them; empty architecture folders are unnecessary.

## 5.1 `app/`

Owns composition:

- bootstrap
- root providers
- root routes
- top-level wiring

It SHOULD contain little business behavior.

## 5.2 `shell/`

Owns the authenticated application frame:

- primary navigation
- router outlet/layout
- tenant switcher placement
- profile/global actions
- responsive shell
- route-transition focus behavior

It MUST NOT contain feature business logic.

## 5.3 `platform/`

Owns cross-feature technical capabilities such as:

```text
auth
tenant
api
config
errors
observability
```

Code belongs here only when it is application-wide and infrastructural.

`platform/` is not a dumping ground.

## 5.4 `features/`

Each business workflow owns its implementation.

A feature may start very small:

```text
features/receivables/
├── receivables.routes.ts
├── receivables.page.ts
└── receivables-api.ts
```

Only introduce `state/`, `model/`, `data-access/`, `ui/`, or other subfolders when the feature actually needs them.

## 5.5 `ui/`

Contains reusable EOC presentation patterns that have earned shared status.

Do not prebuild a component library.

## 5.6 Forbidden generic roots

Avoid broad roots such as:

```text
shared/
common/
helpers/
utils/
services/
models/
components/
```

Keep code near its owner until genuine application-wide reuse exists.

---

# 6. Dependency Direction

Allowed direction:

```text
app        → shell, platform, feature route entries, ui
shell      → platform, ui
feature    → platform, ui, Angular/framework libraries
platform   → Angular/integration libraries
ui         → Angular/presentation libraries
```

Hard rules:

- A feature MUST NOT import another feature's internals.
- `platform/` MUST NOT depend on business features.
- `ui/` MUST NOT depend on business features.
- `ui/` SHOULD NOT depend on auth, tenant, or HTTP infrastructure.
- `shell/` MUST NOT depend on feature internals.
- Cross-feature reuse MUST NOT be solved by reaching into another feature's private files.

Prefer cross-feature interaction through routes, URL state, backend APIs, platform contracts, or promoted shared UI.

---

# 7. Progressive Layering

There is no mandatory layer count.

A simple feature MAY use:

```text
Page
  ↓
data-access service
  ↓
HttpClient
```

When state becomes meaningful:

```text
Page
  ↓
feature state
  ↓
data-access service
  ↓
HttpClient
```

Add a gateway/mapper boundary only when it provides concrete value, for example:

- transport DTOs differ meaningfully from feature models
- multiple endpoints/sources are composed
- significant normalization/mapping exists
- caching/offline behavior exists
- the boundary materially improves correctness or testability

Do **not** require:

```text
Page → Store → Gateway → API Client → HttpClient
```

for every feature.

Layers are tools, not ceremony.

---

# 8. State Strategy

State belongs in the smallest scope that correctly owns it.

Decision order:

```text
URL?
  ↓ no
component Signal?
  ↓ no
feature service + Signals?
  ↓ no
SignalStore justified by real complexity?
```

## 8.1 URL state

Use the URL for state that must survive refresh, browser navigation, bookmarking, or sharing.

Typical examples:

```text
tenant
filters
search
sort
page
page size
stable tab
record identity
```

Sensitive/security information MUST NOT be placed in URLs.

When the URL is authoritative for navigational state, avoid maintaining a second independent source of truth unless feature behavior requires one. Feature code MAY derive Signals or view state from route/query state, but MUST NOT create competing URL and in-memory state that require permanent bidirectional synchronization without a concrete reason.

## 8.2 Component state

Use local Signals for local presentation state.

Examples:

```text
panel open
temporary selection
local input
view toggle
```

## 8.3 Feature state

Use a feature-scoped service with Signals when several components need coordinated state.

Scope it to the feature/route where practical.

## 8.4 SignalStore

NgRx SignalStore is **not** a foundation dependency.

Introduce it only when real complexity appears, such as:

- several coordinated request states
- substantial derived state
- filter/query coordination
- refresh/reload orchestration
- complex update transitions

The Receivables slice is the first likely evaluation point.

Classic global Redux-style NgRx Store is not planned.

## 8.5 RxJS

Use RxJS primarily for:

```text
HTTP
cancellation
debouncing
authentication/session events
event streams
multi-request composition
explicit retry/backoff
```

Do not convert every synchronous value into an Observable.

## 8.6 Server state

Backend business data remains authoritative.

Caching, optimistic updates, and stale-data policies require explicit feature decisions.

---

# 9. HTTP and API Boundary

Feature components SHOULD NOT call `HttpClient` directly.

Default:

```text
Page/Component
      ↓
feature data access
      ↓
HttpClient
      ↓
Spring Boot
```

## 9.1 Interceptors

Cross-cutting HTTP behavior SHOULD use functional Angular interceptors.

Expected concerns:

```text
Bearer token
correlation/trace propagation later
transport normalization where appropriate
```

Interceptors MUST NOT contain feature business logic.

## 9.2 Problem Details

Normalize backend Problem Details into one typed application shape, conceptually:

```ts
interface ApiProblem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  code?: string;
}
```

Features MAY interpret stable problem codes when product behavior differs by case.

Transport problem `detail` MUST NOT automatically be treated as display-safe user-facing copy. Features SHOULD prefer controlled messages based on stable status/problem codes, and SHOULD display backend `detail` only when that endpoint contract explicitly defines the detail as suitable for end users.

## 9.3 API contracts

Endpoint contracts MUST be typed.

Initially, TypeScript transport types MAY live near the owning API boundary.

OpenAPI generation MUST NOT delay the first vertical slice. Introduce it when API size/duplication makes generation clearly valuable.

## 9.4 Model mapping

Separate transport and feature models only when they actually differ in meaning or shape.

Avoid ceremonial one-to-one mappers.

## 9.5 Configuration

Feature code MUST NOT hard-code API or identity-provider origins.

One platform configuration boundary owns environment-specific browser configuration.

For local development, prefer an Angular development proxy where it simplifies same-origin API calls and avoids unnecessary local CORS configuration.

Browser-visible configuration is public; secrets never belong in it.

---

# 10. Authentication and Security

Authentication is implemented in the Authentication + Tenant Context slice, but these boundaries are fixed now.

Browser authentication model:

```text
OpenID Connect
Authorization Code
PKCE S256
public browser client
```

Rules:

- The browser MUST NOT contain a client secret.
- Implicit flow is not part of the design.
- Password/direct-grant authentication is not part of the design.
- The exact OIDC/Keycloak library is selected during the authentication slice.
- Feature code MUST NOT depend directly on the identity-provider SDK.
- Authentication belongs behind a platform boundary.
- Access/refresh tokens SHOULD remain in memory.
- Access/refresh tokens MUST NOT be deliberately persisted in `localStorage` or `sessionStorage` without a documented threat-model decision.
- This does not prohibit the selected OIDC implementation from temporarily storing narrowly scoped protocol transaction state such as `state`, `nonce`, or PKCE/redirect correlation data when required for a secure authorization flow.
- Temporary OIDC transaction state MUST NOT be treated as general application session storage and SHOULD be removed/expired according to the selected integration's secure protocol behavior.
- Tokens MUST NOT appear in URLs, application logs, analytics payloads, or user-visible diagnostics.
- Frontend role checks MAY improve UX; backend authorization remains authoritative.
- Route guards are navigation UX, not security controls.

Conceptual boundary:

```text
Feature / Shell
      ↓
AuthSession
      ↓
OIDC integration
      ↓
Keycloak
```

---

# 11. Current User and Tenant Context

Authenticated boot flow:

```text
OIDC session
   ↓
GET /api/v1/me
   ↓
GET /api/v1/me/tenants
   ↓
resolve tenant route
   ↓
TenantContext
   ↓
tenant-scoped feature
```

Normal tenant routes SHOULD use the readable tenant key:

```text
/t/:tenantKey/receivables
/t/:tenantKey/copilot
```

Rules:

- The URL owns selected tenant navigation state.
- The accessible-tenant response owns selectable tenants.
- Route tenant keys are resolved to backend tenant IDs before tenant-scoped API calls.
- Tenant switching MUST invalidate tenant-specific feature state that no longer applies.
- An inaccessible tenant MUST produce an honest access/not-found flow.
- Silent substitution to another tenant is forbidden.

---

# 12. Routing

Features are lazy-loaded by route.

Conceptual hierarchy:

```text
/
└── /t/:tenantKey
    └── authenticated shell
        ├── receivables
        └── copilot
```

Rules:

- Feature routes SHOULD be lazy.
- Route configuration SHOULD stay near its feature.
- Route/query parameters are parsed at the feature boundary.
- Back/Forward behavior MUST remain natural.
- Guards MAY improve navigation UX but do not replace backend authorization.
- Route transitions MUST integrate with shell-level focus and page-title behavior.

---

# 13. Components and Shared UI

## Page components

Page components coordinate route-level workflows.

They MAY:

- read route state
- call feature state/data access
- compose feature UI
- present page-level state

They SHOULD NOT accumulate low-level HTTP, identity SDK, or unrelated shared logic.

## Feature UI

Feature-local UI remains local until stable reuse is demonstrated.

## Shared EOC UI

Promotion path:

```text
feature-local
   ↓
stable/repeated product concept
   ↓
shared EOC UI
```

The extraction rules are defined in `UI_ENGINEERING_GUIDELINES.md`.

---

# 14. Styling

Global styling may evolve around:

```text
src/styles/
├── tokens.css
├── reset.css
├── typography.css
└── global.css
```

Rules:

- Feature styles SHOULD use semantic color tokens.
- Shared spacing/type scales MAY be consumed directly.
- Avoid arbitrary color literals when a semantic token exists.
- Component styles are locally scoped by default.
- Global CSS is for tokens, reset/base behavior, typography, and truly application-wide rules.
- Do not introduce a utility CSS framework by default.

---

# 15. Forms and Schema-Driven UI

No general form framework is selected in the foundation slice.

When a real form arrives:

- use modern typed Angular form capabilities appropriate to the selected Angular version
- keep validation testable
- distinguish form/transport/domain models when useful
- represent mutation state honestly
- use schemas where they reduce real complexity

EOC is **not** a server-driven UI system.

Potentially appropriate schema uses:

```text
form validation
table-column configuration
filter definitions
API contracts
```

Not default:

```text
JSON page builders
backend-defined Angular component trees
generic CRUD layout engines
```

---

# 16. Testing

Use the smallest reliable test layer:

```text
pure logic
   ↓
service/state
   ↓
component
   ↓
router/HTTP integration
   ↓
small browser E2E
```

## Vitest

Use for:

- pure functions
- parsers/formatters
- services/state
- component behavior
- route-adjacent logic

## Router tests

Use real route configuration and Angular's router testing harness instead of mocking the Router.

## HTTP tests

Use Angular HTTP testing facilities for:

- request path/query construction
- interceptors
- Problem Details behavior
- failure paths
- cancellation/debounce where relevant

## Browser E2E

Select the E2E framework when the first complete workflow needs it.

Keep browser coverage focused on contracts such as:

```text
login
tenant selection
receivables workflow
Copilot evidence workflow
critical authorization behavior
```

Do not duplicate broad unit/component coverage in E2E.

---

# 17. CI

Frontend verification is independent of Maven verification.

PR #69 should establish a web CI gate conceptually equivalent to:

```text
install pinned Node
npm ci
lint
test once
production build
```

Future gates are added only when measurable implementation exists:

```text
accessibility automation
bundle budgets
performance regression checks
focused E2E
deployment/container verification
```

Do not invent arbitrary performance budgets before a real production bundle exists.

---

# 18. Performance

Initial structural choices:

```text
client-rendered SPA
lazy feature routes
zoneless Angular
Signals for synchronous UI state
no SSR
no microfrontends
```

Measure before introducing:

```text
manual virtualization
complex caching
SSR/hydration
offline/service-worker architecture
custom preloading
worker processing
```

The Engineering Quality phase defines budgets from measured behavior.

---

# 19. Dependency Policy

A dependency is justified when it solves a concrete requirement better than Angular/platform capabilities without disproportionate cost.

PR #69 does **not** need, by default:

```text
NgRx
generic UI framework
chart library
E2E framework
OpenAPI generator
utility CSS framework
schema-form framework
```

Avoid dependencies for trivial helpers.

---

# 20. Architecture Enforcement

Architecture should become executable as the codebase grows.

Use, progressively:

```text
Angular/TypeScript strictness
ESLint
import-boundary rules
tests
CI
code review
```

Do not build a custom architecture-test framework in the foundation slice.

Add enforcement when a boundary has enough code that accidental violations become realistic.

---

# 21. Delivery Sequence

```text
#69 Foundation
      ↓
#70 Authentication + Tenant Context
      ↓
#71 Receivables Vertical Slice
      ↓
    Early deployment
      ↓
#72 Copilot Vertical Slice
      ↓
#73 Engineering Quality
      ↓
#74 Final Documentation + Refinement
```

### #69 Foundation

```text
Angular workspace
strict TypeScript
standalone + zoneless
Vitest
routing skeleton
application shell
initial semantic tokens
basic styling
lint/test/build scripts
frontend CI
architecture + UI guideline
```

No NgRx, generated API client, complete design system, or business feature.

### #70 Authentication + Tenant Context

```text
OIDC Code + PKCE
AuthSession boundary
token attachment/refresh
/api/v1/me
/api/v1/me/tenants
tenant routes/context/switcher
Problem Details normalization
```

### #71 Receivables

Complete vertical slice:

```text
API
→ state
→ URL synchronization
→ summary/list UI
→ filters/sort/pagination
→ honest states
→ tests
```

This slice decides whether SignalStore/additional data layers are justified.

### #72 Copilot

Operational capability:

```text
question
business date
grounded answer
supporting evidence
provenance
partial/unavailable/failure states
```

No fake persistent chat history.

### #73 Engineering Quality

Measure and harden accessibility, keyboard/focus, performance, bundle size, observability, security, integration coverage, and focused E2E.

### #74 Refinement

Document the architecture/design system as actually evolved and capture measurable engineering results.

---

# 22. Architecture Decision Test

Before adding a layer, framework, store, abstraction, or shared component, ask:

1. What concrete problem exists now?
2. Can Angular/platform capabilities solve it simply?
3. Is the complexity repeated or durable?
4. Does the abstraction create a meaningful boundary?
5. Does it improve correctness, testability, accessibility, or maintainability?
6. What concepts/files/dependencies does it add?
7. Can it be removed if requirements change?
8. Are we solving a current problem or a hypothetical one?

If the justification is primarily hypothetical, defer it.

---

# 23. v0.1 Invariants

These rules are locked for the initial implementation:

1. EOC web is a client-rendered Angular SPA.
2. EOC-authored code uses standalone Angular architecture; third-party NgModule compatibility is allowed when necessary.
3. Keep the app zoneless.
4. Organize feature first.
5. No generic root `shared/` dumping ground.
6. Features do not import other features' internals.
7. `platform/` and `ui/` do not depend on business features.
8. State lives in the smallest correct scope.
9. URL owns restorable/shareable navigational state.
10. Sensitive/security data never belongs in URLs.
11. Signals are the synchronous-state default.
12. RxJS owns asynchronous stream composition.
13. SignalStore is optional and evidence-driven.
14. Backend business state remains authoritative.
15. Components do not own cross-cutting HTTP/auth infrastructure.
16. Prefer functional HTTP interceptors for cross-cutting transport behavior.
17. Backend authorization remains authoritative.
18. Browser auth uses Authorization Code + PKCE with a public client.
19. Browser tokens are not deliberately persisted by default.
20. Features are lazy-loaded by route.
21. Shared UI emerges from real product repetition or stable semantics.
22. Typed API contracts are required; generation is optional.
23. Schema-driven UI is selective, not the application architecture.
24. Vitest is the unit/component baseline.
25. Browser E2E remains small and workflow-focused.
26. Frontend CI is independent of Maven CI.
27. SSR and microfrontends are not planned.
28. Measure before optimizing.
29. Convert architecture rules into automated checks when useful.
30. Every vertical slice leaves the application runnable and deployable.

---

# 24. Evolution

This is a baseline, not a frozen framework.

Update this document when implementation changes a durable rule involving:

```text
dependency direction
state ownership
routing
authentication
API boundaries
testing strategy
shared UI ownership
runtime/deployment configuration
```

Small implementation details do not require architecture-document churn.

The goal is evidence-based, understandable architecture evolution.
