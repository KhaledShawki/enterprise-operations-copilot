# EOC Frontend Product & UI Engineering Guidelines

**Product:** Enterprise Operations Copilot (EOC)
**Document:** Frontend Product & UI Engineering Guidelines
**Version:** 0.1
**Status:** Living standard — v0.1 baseline complete
**Applies to:** EOC authenticated web application
**Initial theme:** Light operational theme
**Last updated:** 2026-08-14

---

## 1. Purpose

This document defines how the EOC frontend should **behave, communicate, present operational information, and evolve as a product UI**.

It is intentionally separate from `ARCHITECTURE.md`.

- `ARCHITECTURE.md` defines how the Angular application is engineered.
- `UI_ENGINEERING_GUIDELINES.md` defines how the product behaves and presents information to users.

This standard is authoritative, but intentionally incomplete.

A guideline becomes specific only when the product requirement is understood well enough to justify the constraint.

The frontend must establish strong product and interaction boundaries early while allowing reusable patterns, abstractions, and detailed component rules to emerge from real feature work.

### 1.1 Primary product contexts

These guidelines currently target:

- authenticated operational workspaces
- tenant-aware navigation
- receivables and financial/operational data
- dense list and table workflows
- dashboards and summary information
- loading, refresh, empty, stale, partial, unavailable, unauthorized, and failure states
- evidence-backed Copilot experiences
- desktop, tablet, and mobile use

### 1.2 Current non-goals

Version 0.1 does **not** attempt to define:

- a complete component library
- a generic CRUD framework
- server-driven page layouts
- JSON-defined application screens
- a complete dark theme
- detailed form architecture before a real form workflow exists
- detailed charting rules before real analytical charts exist
- final Copilot interaction patterns before the Copilot vertical slice exists
- fixed performance budgets before the first production bundle is measurable

---

## 2. Normative Language

The terms below are used deliberately:

- **MUST / MUST NOT** — required for product consistency, correctness, accessibility, or trust.
- **SHOULD / SHOULD NOT** — expected default; deviation requires a concrete reason.
- **MAY** — optional and requirement-dependent.

When a guideline conflicts with an actual product requirement, the product requirement wins only after the exception is understood and documented.

---

# 3. Governing Product Principles

## 3.1 Operational clarity

EOC is operational software. The main purpose of the interface is to help users understand business state accurately and act with low error risk.

Data, status, totals, dates, relationships, evidence, and actions MUST take priority over decoration.

### Rules

- Important business values MUST be easy to scan.
- Primary actions MUST be obvious.
- Destructive actions MUST be clearly distinguishable.
- Loading, failure, and permission states MUST be explicit.
- Decorative styling MUST NOT compete with financial or operational information.
- Dense screens MAY be compact, but MUST remain readable.

---

## 3.2 Evidence before decoration

EOC handles operational and AI-assisted information. Trust depends on showing where information came from and what it means.

### Rules

- Source records, business dates, status, and provenance MUST be visually clearer than decorative elements.
- Grounded Copilot output MUST expose supporting evidence when the product provides it.
- AI-generated text MUST NOT visually masquerade as an authoritative system-of-record field.
- Provider/model implementation details are not business evidence and SHOULD remain hidden from normal users.
- When data freshness matters, the relevant date or freshness state MUST be visible.

---

## 3.3 Progressive disclosure

The first view should show the most decision-relevant information. Secondary details should remain available without overwhelming the primary workflow.

### Rules

- Important operational facts SHOULD appear before metadata.
- Rare actions SHOULD NOT compete visually with frequent actions.
- Supporting evidence MAY be collapsed initially when the answer remains trustworthy without immediate expansion.
- Advanced filters SHOULD be progressively disclosed when the default workflow can remain simpler.
- Hidden information MUST remain discoverable.

---

## 3.4 Calm density

EOC SHOULD be information-rich without becoming visually noisy or artificially spacious.

### Rules

- Dense operational screens SHOULD use consistent spacing and alignment rather than large decorative cards.
- Financial tables SHOULD favor readable density over marketing-style presentation.
- Empty whitespace SHOULD be used to separate meaning, not to make screens look artificially minimal.
- Large decorative surfaces SHOULD be rare in authenticated operational areas.
- Density MAY vary by workflow, but interaction targets and accessibility MUST remain usable.

---

## 3.5 Predictable interaction

Equivalent actions and states should behave consistently across the application.

### Rules

- The same action label MUST mean the same thing across features.
- Filtering, sorting, paging, refresh, selection, and reset behaviors SHOULD follow consistent patterns.
- Primary, secondary, destructive, and low-emphasis actions MUST have stable semantics.
- Users SHOULD NOT need to relearn common interaction patterns between features.
- Browser navigation and deep links MUST behave predictably.

---

## 3.6 State honesty

The UI MUST accurately represent what the application actually knows.

The following states are not interchangeable:

```text
initial loading
refreshing
stale
success
empty
filtered empty
partial
unavailable
unauthorized
forbidden
failed
```

### Rules

- `empty` MUST NOT be used when a request failed.
- `failed` MUST NOT be presented as `no results`.
- `refreshing` SHOULD preserve already-valid data when appropriate instead of replacing it with a full-page loading state.
- `stale` data MUST be identifiable when freshness materially affects decisions.
- `partial` results MUST NOT look identical to complete results.
- `unauthorized` and `forbidden` MUST remain distinguishable where that distinction is useful to the user.
- Copilot must distinguish `no supporting evidence` from `evidence retrieval failed`.
- A backend or provider outage MUST NOT be represented as a valid business answer.

---

# 4. Information Hierarchy

Every screen SHOULD communicate hierarchy through structure before styling.

The default hierarchy is:

```text
application
  → tenant
    → feature
      → page
        → section
          → record / value
            → metadata / evidence
```

## 4.1 Page-level hierarchy

A normal feature page SHOULD make these elements identifiable:

1. current feature / location
2. page title
3. relevant context such as tenant or business date
4. primary action, when one exists
5. query controls, when needed
6. main result/content region
7. supporting metadata or secondary actions

## 4.2 Business values

High-value business information includes:

- outstanding amount
- currency
- due date
- status
- customer
- invoice number
- business date
- totals
- risk indicators
- evidence/source identifiers

These values SHOULD receive stronger hierarchy than technical metadata.

## 4.3 Technical metadata

Technical metadata SHOULD be visible only when it helps the workflow, supports trust, or is needed for diagnosis.

Examples:

- event identifier
- aggregate version
- timestamp
- correlation identifier

Technical metadata MUST NOT dominate ordinary business presentation.

---

# 5. Semantic Design Token Philosophy

The token system is separate from this guideline.

This document defines token **semantics and rules**. Runtime token files are the implementation source of truth for concrete values.

EOC will use three conceptual token levels:

```text
primitive
   ↓
semantic
   ↓
component-specific, only when justified
```

## 5.1 Primitive tokens

Primitive tokens describe raw scales or values.

Examples:

```text
space-1
space-2
font-size-300
neutral-100
neutral-900
```

Primitive layout scales such as spacing, sizing, and approved typography scales MAY be consumed directly when they represent a shared system scale and no additional product meaning is required.

Raw visual color primitives SHOULD normally remain behind semantic tokens.

Do not invent semantic tokens merely to rename ordinary spacing relationships. For example, prefer a shared spacing primitive over a token such as `receivables-header-left-gap` unless that spacing has become a stable reusable product concept.

## 5.2 Semantic tokens

Semantic tokens describe product meaning.

Examples:

```text
surface-canvas
surface-default
surface-subtle
surface-raised

text-primary
text-secondary
text-disabled
text-inverse

border-default
border-strong

action-primary
action-primary-hover
action-destructive

status-success
status-warning
status-danger
status-info

focus-ring
```

Feature and shared UI code SHOULD prefer semantic tokens.

## 5.3 Component-specific tokens

Component-specific tokens MAY exist when a reusable component has stable semantics that cannot be expressed clearly with existing semantic tokens.

Examples that may eventually be justified:

```text
table-row-selected-background
copilot-evidence-border
navigation-active-background
```

Do not create component-specific tokens speculatively.

## 5.4 Color rules

- Brand/action color is a signal, not decoration.
- Semantic red, yellow/amber, green, and informational colors MUST represent real semantic meaning.
- Color MUST NOT be the only signal for status, selection, error, success, warning, or focus.
- Dense operational surfaces SHOULD remain primarily neutral.
- Large saturated backgrounds SHOULD be rare in data-heavy workflows.
- Feature code MUST NOT introduce arbitrary one-off colors when a semantic token exists.

## 5.5 Theme scope

Version 0.1 targets one excellent light operational theme.

The token model SHOULD remain structurally capable of supporting a future dark theme, but substantial work to design and verify a complete dark theme SHOULD NOT be undertaken until a real requirement justifies the cost.

---

# 6. Typography and Operational Data

Typography in EOC exists to improve scanning, hierarchy, and data accuracy.

## 6.1 Typography rules

- Use a modern, highly readable sans-serif UI typeface.
- Page and section titles SHOULD use sentence case.
- Button labels SHOULD use concise verb-first language.
- Table headers SHOULD use sentence case unless a compact domain-specific convention clearly benefits from another treatment.
- Long body text SHOULD use comfortable line height.
- Dense table typography MAY be smaller than standard body text but MUST remain readable.

## 6.2 Numerals

Operational data requires consistent numeral behavior.

### Currency

- Currency values MUST include an unambiguous currency.
- Decimal precision MUST be consistent for equivalent values.
- Numeric values SHOULD align consistently in table columns.
- The UI MUST NOT imply arithmetic aggregation across different currencies unless the backend/domain explicitly supports it.

### Percentages

- Percentages MUST include `%`.
- Rounding MUST be consistent.
- Approximate values SHOULD be distinguishable from exact values when that distinction matters.

### Dates

- Date-only values MUST remain date-only concepts.
- Display SHOULD be locale-aware.
- Business-date context MUST remain explicit when it changes interpretation.
- Date/time values SHOULD communicate timezone when ambiguity matters.
- The UI MUST NOT silently shift date-only business values due to client timezone conversion.

### Negative values

Negative values MUST use an explicit minus sign.

Color MAY reinforce negative or risky meaning, but color alone MUST NOT define it.

### Identifiers

Invoice numbers, customer numbers, event IDs, and similar identifiers SHOULD use presentation that improves recognition without making the interface look like a developer console.

---

# 7. Application Shell and Navigation

The application shell provides stable orientation and global context.

## 7.1 Global shell responsibilities

The shell MAY contain:

- product identity
- primary feature navigation
- current tenant
- user/profile actions
- global application actions
- responsive navigation controls

The shell MUST NOT become a dumping ground for page-specific actions.

The shell MUST define a consistent route-transition focus strategy. After client-side navigation, focus MUST move to a meaningful destination in the new view when leaving focus on the previous control would be confusing or invalid. Page changes SHOULD also produce an understandable announcement experience for assistive-technology users.

## 7.2 Tenant context

Tenant context is security- and meaning-relevant.

- The active tenant MUST be identifiable.
- Tenant switching MUST be explicit.
- A tenant switch MUST NOT silently retain invalid tenant-scoped page state.
- Tenant route context SHOULD be represented in the URL.
- Frontend tenant state MUST NOT override backend authorization.
- If the requested tenant is not accessible, the user MUST receive an honest permission/not-found experience rather than silently falling back to another tenant.

## 7.3 Navigation hierarchy

- Only one primary navigation destination SHOULD appear strongly active.
- Related destinations SHOULD be grouped consistently.
- Navigation labels SHOULD use business language rather than backend implementation terminology.
- Deep navigation SHOULD avoid unnecessary nesting.
- Parent navigation SHOULD remain discoverable.
- Mobile navigation MAY use a drawer or equivalent compact pattern.

## 7.4 Breadcrumbs

Breadcrumbs MAY be used where they materially improve orientation.

They SHOULD:

- represent meaningful hierarchy
- use recognizable entity names/numbers
- keep the current page non-clickable
- avoid excessive depth

---

# 8. Page Anatomy

A standard EOC page SHOULD be composed from meaningful regions rather than arbitrary containers.

Conceptually:

```text
page context
page header
query/actions region
primary content
supporting content
feedback/state region
```

## 8.1 Page header

A page header SHOULD contain only what is needed for orientation and page-level action.

Typical content:

- title
- concise supporting context
- primary action
- important page-level secondary actions

Page headers SHOULD NOT contain every available filter and utility when a dedicated result toolbar is clearer.

## 8.2 Primary action

- A visual region SHOULD normally have at most one dominant primary action.
- The primary action SHOULD be easy to locate.
- The same action MUST NOT appear redundantly in multiple nearby locations without a usability reason.

## 8.3 Secondary actions

Secondary actions SHOULD be visually quieter.

Rare or low-priority actions MAY move into an overflow menu when the visible action area becomes crowded.

## 8.4 Destructive actions

Destructive actions MUST:

- use destructive semantics
- communicate the consequence
- require confirmation when the action is difficult or impossible to reverse
- avoid competing visually with the normal primary action

---

# 9. Action and Interaction Hierarchy

## 9.1 Action categories

EOC uses these semantic action categories:

| Category | Meaning | Examples |
|---|---|---|
| Primary | Main forward action | Save, Create, Continue, Ask |
| Secondary | Useful alternative | Export, Refresh, Preview |
| Low-emphasis | Navigation or utility | Back, Reset, Cancel |
| Destructive | Irreversible/risky | Delete, Void, Remove |
| Contextual | Record-specific | View, Open, More actions |

## 9.2 Labels

- Prefer verb-first labels.
- Use domain language.
- Avoid generic `Submit` where a more precise action exists.
- Avoid long explanatory button text.
- Destructive labels MUST describe the actual action.

Examples:

```text
Good:
Save
Create invoice
Refresh
Reset filters
Discard changes

Avoid:
Submit
Proceed
Click here
Do action
Clear everything
```

## 9.3 Loading actions

Actions that trigger asynchronous work MUST prevent accidental duplicate submission when duplication would be unsafe.

The control SHOULD communicate progress without causing layout instability.

---

# 10. Loading, Refresh, Empty, Partial, and Failure Behavior

State behavior is part of the product contract.

## 10.1 Initial loading

Use when the page has no valid content to display yet.

The UI SHOULD communicate:

- that work is in progress
- what region is loading
- enough stable layout to avoid unnecessary visual movement

## 10.2 Refreshing

Use when valid content already exists and newer content is being requested.

Whenever safe:

- keep current content visible
- show a non-blocking refresh indicator
- avoid replacing the entire page with a loading screen

## 10.3 Stale

If the system knows content is stale and freshness matters:

- the stale state MUST be visible
- the last-known value MAY remain visible
- refresh/recovery action SHOULD be available where appropriate

## 10.4 Empty

`Empty` means the request succeeded and there is no data.

An empty state SHOULD include:

- clear title
- concise explanation
- next action when useful

Example:

```text
No receivables found.
```

## 10.5 Filtered empty

Filtered empty means data may exist, but nothing matches the active query.

Example:

```text
No receivables match the current filters.
```

The UI SHOULD offer an easy way to inspect or reset filters.

## 10.6 Partial

Partial means the system produced only part of the expected result.

- Partial results MUST be labeled.
- Missing portions MUST NOT silently disappear.
- The UI SHOULD explain what is incomplete when possible.
- A partial Copilot result MUST NOT look fully grounded.

## 10.7 Unavailable

Unavailable means a required system or dependency could not provide data.

Example:

```text
Receivables are temporarily unavailable.
```

Do not use an empty-state illustration that implies there are genuinely no records.

## 10.8 Unauthorized and forbidden

Authentication and authorization failures MUST not be represented as ordinary application errors.

Where useful:

- unauthenticated → explain that sign-in is required
- forbidden → explain that access is not permitted

Do not leak sensitive authorization internals.

## 10.9 Failed

A failure state SHOULD:

- say what could not be completed
- avoid exposing internal stack traces or provider details
- offer retry/recovery when meaningful
- preserve valid existing content when the failed operation was only a refresh

---

# 11. URL and Navigational State UX

The URL is part of the user experience.

State that should survive refresh, support browser navigation, or be shareable SHOULD live in the URL.

Typical examples:

- tenant selection
- filters
- search terms
- sorting
- pagination
- selected tab when semantically important
- stable record identity

## 11.1 URL rules

- URLs SHOULD use readable stable route concepts.
- Query parameters SHOULD represent actual navigational/query state.
- Refreshing a URL SHOULD reconstruct the same meaningful view when the user remains authorized.
- Browser Back/Forward MUST behave naturally.
- Resetting filters SHOULD update the URL.
- Temporary presentation-only state such as hover or an unimportant drawer toggle SHOULD remain local.
- Sensitive, secret, personal, authentication, authorization, or transient security data MUST NOT be encoded in route paths or query parameters.
- Before placing business data in a URL, consider that URLs may appear in browser history, logs, analytics systems, copied links, screenshots, and referrer metadata.

---

# 12. Responsive Philosophy

EOC uses responsive behavior to preserve task effectiveness, not merely to fit content on smaller screens.

## 12.1 General approach

Prefer:

```text
content-driven layout
+
CSS Grid/Flexbox
+
component-aware adaptation
+
container queries where appropriate
+
a small set of global viewport breakpoints
```

Avoid forcing every feature through one rigid universal grid.

## 12.2 Desktop

Desktop SHOULD optimize for:

- information density
- side-by-side context
- efficient scanning
- full operational tables
- persistent navigation when space allows

## 12.3 Tablet

Tablet SHOULD preserve the same workflow while reducing non-essential simultaneous content.

Possible adaptations:

- collapsed navigation
- reduced column count
- secondary information moved below primary content
- selectively hidden low-priority table columns

## 12.4 Mobile

Mobile SHOULD preserve the essential workflow rather than reproduce desktop geometry.

Possible adaptations:

- single-column layouts
- drawer navigation
- reduced visible actions
- key-value representations instead of overly compressed wide tables
- progressive disclosure of secondary metadata

## 12.5 Responsive invariants

Across all sizes:

- primary actions remain discoverable
- important values remain readable
- state and errors remain visible
- keyboard/touch targets remain usable
- horizontal scrolling SHOULD be intentional, not accidental

---

# 13. Accessibility Baseline

EOC targets **WCAG 2.2 AA** for user-facing workflows.

Accessibility is part of correctness.

## 13.1 Required baseline

- All critical workflows MUST be usable with keyboard alone.
- Every interactive control MUST have an accessible name.
- Focus MUST be visible.
- Focus order MUST follow interaction order.
- Application pages MUST use meaningful landmarks and a logical heading hierarchy.
- Route/page changes MUST produce a meaningful focus destination; focus MUST NOT remain on a control that no longer represents the visible view.
- Route/page changes SHOULD be understandable to assistive-technology users through focus movement, page-title updates, announcements, or an equivalent accessible mechanism.
- Dialogs/overlays MUST manage focus correctly.
- Inputs MUST have persistent labels.
- Placeholder text MUST NOT replace labels.
- Errors MUST use text, not color alone.
- Status MUST NOT rely on color alone.
- Meaningful images MUST have alternative text.
- Decorative images MUST be ignored by assistive technology.
- Motion MUST respect `prefers-reduced-motion`.
- Dynamic state changes SHOULD be announced when users of assistive technology would otherwise miss important information.
- Touch-heavy controls SHOULD provide practical target sizes.

## 13.2 Contrast

Text, controls, focus indicators, and meaningful UI boundaries MUST meet the applicable WCAG AA contrast requirements.

Contrast SHOULD eventually be verified automatically for the token system.

## 13.3 Data accessibility

Operational tables MUST preserve:

- semantic headers
- understandable column labels
- keyboard-accessible actions
- non-color-only status communication
- sensible reading order

Detailed table accessibility rules will be expanded with the Receivables vertical slice.

---

# 14. Content and Terminology

EOC should communicate in clear, professional, calm operational language.

## 14.1 Voice

The UI should be:

- clear
- concise
- professional
- actionable
- consistent
- calm
- specific

Avoid slang, playful error text, or unnecessarily dramatic wording in operational workflows.

## 14.2 Domain terminology

The same business concept MUST use the same term throughout the product.

Prefer:

```text
Customer
Invoice
Receivable
Outstanding amount
Business date
Supporting evidence
```

Avoid exposing backend field names such as:

```text
customerTypeCode
aggregateVersion
source_event_id
```

unless the technical field itself is intentionally shown as diagnostic/evidence metadata.

## 14.3 Error text

Errors SHOULD:

- explain what failed
- explain a recovery step when known
- avoid blame
- avoid internal implementation jargon
- avoid leaking provider, stack, SQL, infrastructure, or security internals

## 14.4 Empty-state text

Empty states SHOULD distinguish:

```text
no records exist
no records match filters
access is restricted
data is unavailable
```

These are different product states and MUST NOT share misleading copy.

## 14.5 Privacy and data exposure

EOC may display sensitive operational and business information. The UI MUST minimize unnecessary exposure.

- Sensitive or confidential data MUST NOT be placed in URLs.
- Errors, notifications, diagnostics, and client-visible logs MUST NOT expose secrets, credentials, access tokens, infrastructure internals, or unnecessary sensitive business data.
- Clipboard actions, exports, screenshots, print views, and downloadable artifacts SHOULD expose only the information required by the workflow.
- Hidden or collapsed content SHOULD NOT contain sensitive information merely because it is not currently visible.
- UI telemetry and analytics MUST NOT capture sensitive field values by default.
- Security-sensitive information SHOULD be revealed only when the user has a legitimate product need and the backend authorizes access.

## 14.6 Internationalization and localization

Version 0.1 does not mandate a complete localization framework, but new UI MUST remain localization-ready.

- User-facing strings SHOULD NOT be assembled from fragments in ways that prevent natural translation.
- Layouts and controls SHOULD tolerate reasonable text expansion.
- Domain identifiers, codes, invoice numbers, and other stable identifiers MUST remain distinct from translated display labels.
- Locale-aware formatting SHOULD be used for dates, times, numbers, and currencies where appropriate.
- Business semantics MUST NOT depend on translated display text.
- Bidirectional/RTL support MAY be introduced when required; new layout decisions SHOULD avoid unnecessary assumptions that make future directionality support expensive.

---

# 15. Reusable Component Extraction Policy

Reusability is discovered, not predicted.

A pattern SHOULD become reusable when at least one of these is true:

1. it represents a stable product concept
2. the same interaction/presentation repeats
3. centralization materially improves accessibility
4. centralization materially improves correctness
5. centralization materially improves visual consistency
6. feature duplication is becoming costly

Do not extract a component merely because it might be reused later.

## 15.1 Promotion model

Preferred evolution:

```text
feature-local implementation
        ↓
stable or repeated pattern discovered
        ↓
refine API and semantics
        ↓
promote to shared EOC UI
```

Examples that may emerge later:

```text
MoneyValue
StatusBadge
ProblemNotice
PageHeader
DataTable
EvidenceRecord
GroundingPanel
```

Their existence is not mandated by version 0.1.

---

# 16. Initial Product UI Review Checklist

Every frontend PR that changes user-facing behavior SHOULD be reviewed against the relevant items below.

## 16.1 Information and hierarchy

- [ ] Page has one clear purpose.
- [ ] Page title is easy to identify.
- [ ] Important business values have stronger hierarchy than metadata.
- [ ] Technical details do not dominate ordinary workflows.
- [ ] Active tenant/context is clear where relevant.

## 16.2 Actions

- [ ] Primary action is easy to identify.
- [ ] Secondary actions do not visually compete with the primary action.
- [ ] Destructive actions use destructive semantics.
- [ ] Action labels use clear domain language.
- [ ] Duplicate submission is prevented where required.

## 16.3 State honesty

- [ ] Initial loading is explicit.
- [ ] Refresh behavior is distinct from initial loading where applicable.
- [ ] Empty is not used for failure.
- [ ] Filtered empty is distinguishable from truly empty data.
- [ ] Partial/stale states are visible when applicable.
- [ ] Unavailable is not presented as no data.
- [ ] Permission failures are represented honestly.

## 16.4 Tokens and visual consistency

- [ ] Semantic tokens are used instead of arbitrary values where available.
- [ ] Color is not the only status signal.
- [ ] Dense operational surfaces remain visually calm.
- [ ] Typography is consistent.
- [ ] Financial/numeric values are formatted consistently.

## 16.5 Navigation and URL

- [ ] Refresh restores meaningful navigational state.
- [ ] Filters/sort/page live in the URL when they should be shareable/restorable.
- [ ] Back/Forward navigation behaves naturally.
- [ ] Tenant context is not lost or silently substituted.
- [ ] Sensitive, secret, personal, authentication, authorization, or transient security data is not encoded in URLs.

## 16.6 Accessibility

- [ ] Keyboard navigation works for the changed workflow.
- [ ] Focus is visible.
- [ ] Interactive controls have accessible names.
- [ ] Page landmarks and heading hierarchy are meaningful.
- [ ] Route transitions leave focus in a meaningful place.
- [ ] Inputs have labels.
- [ ] Errors are not color-only.
- [ ] Status is not color-only.
- [ ] Responsive behavior does not make controls unusable.
- [ ] Reduced-motion behavior is respected when motion is introduced.

## 16.7 Content

- [ ] Domain terminology is consistent.
- [ ] Error text avoids internal implementation details.
- [ ] Empty/failure messages describe the actual state.
- [ ] UI text is concise and actionable.
- [ ] Sensitive information is not unnecessarily exposed through notifications, diagnostics, clipboard/export behavior, or hidden content.
- [ ] New UI text and layout do not introduce avoidable localization blockers.

---

# 17. Evolution Policy

This document evolves with the product.

## 17.1 Governing rule

> A guideline becomes specific only when the product requirement is understood well enough to justify the constraint.

## 17.2 Engineering counterpart

> Establish boundaries early. Introduce abstractions only when demonstrated complexity or repetition justifies them.

## 17.3 When to expand this document

The guideline SHOULD be expanded when:

- a new vertical slice introduces a new stable interaction model
- multiple features repeat the same pattern
- a recurring usability inconsistency appears
- accessibility requirements require a shared rule
- measurable performance behavior requires a product constraint
- Copilot introduces trust/evidence behavior that should apply consistently
- a design token or shared component becomes a stable system concept

## 17.4 When not to add a rule

Do not add rules merely because:

- a design system might eventually need them
- another product uses them
- a framework supports them
- a hypothetical future feature could use them
- they make the document appear more complete

---

# 18. Deferred Sections

These areas are intentionally deferred until a concrete product slice provides sufficient evidence.

## 18.1 Receivables and operational tables

**Expand during:** Receivables vertical slice

Expected topics:

- table density
- column priority
- sorting semantics
- filtering
- pagination
- loading/refresh behavior
- selection
- money/currency
- overdue/risk presentation
- responsive transformations
- table keyboard/accessibility behavior
- large-data performance

## 18.2 Forms

**Expand during:** first meaningful form workflow

Expected topics:

- form model design
- required fields
- validation timing
- server validation
- dirty-state behavior
- save/discard
- date-only values
- async validation
- long-form navigation
- destructive state transitions
- mutation states such as saving, submitting, deleting, pending/optimistic state, saved state, and conflict

## 18.3 Dialogs and overlays

**Expand when:** product workflows justify them

Expected topics:

- focus management
- confirmation
- destructive actions
- selection
- responsive sizing
- nested interaction limits

## 18.4 Charts and analytics visualization

**Expand when:** real analytical charts are introduced

Expected topics:

- semantic vs categorical color
- accessible legends
- non-color encoding
- aggregation disclosure
- tooltip behavior
- responsive chart behavior

## 18.5 Copilot and evidence UX

**Expand during:** Copilot vertical slice

Expected topics:

- question composer
- business-date control
- answer hierarchy
- grounding
- evidence/provenance
- supporting records
- partial grounding
- unavailable evidence
- trustworthy failure presentation
- navigation from evidence to records
- absence of fake conversation history
- distinction between AI synthesis and source-of-record facts

## 18.6 Performance budgets

**Expand after:** first production bundle and real feature measurements

Expected topics:

- initial bundle budget
- lazy feature chunk budgets
- render performance
- table performance
- API latency presentation
- Core Web Vitals where useful
- regression thresholds

## 18.7 Dark theme

**Expand when:** a real product requirement exists

The semantic token architecture SHOULD make a future dark theme possible without requiring one in the initial product.

---

# 19. Source and Design Heritage

This guideline was informed by prior ERP UI experience, especially proven principles around:

- clarity before decoration
- predictable action placement
- neutral data-heavy surfaces
- semantic status color
- readable financial tables
- explicit loading/error/empty states
- responsive operational workflows
- accessibility
- structured UI review

Those principles have been retained where they remain valid.

Framework-specific assumptions, product-specific themes, React/MUI examples, and predeclared Blue component contracts were intentionally not carried forward.

EOC owns its own product identity, interaction system, and implementation evolution.

---

# 20. Version 0.1 Exit Criteria

Version 0.1 is sufficient to begin the Angular foundation when:

- governing product principles are accepted
- semantic token philosophy is accepted
- shell/navigation/page behavior is defined at principle level
- state honesty is defined
- URL-state behavior and sensitive-URL constraints are defined
- accessibility baseline and SPA route-focus expectations are defined
- privacy/data-exposure baseline is defined
- localization-readiness baseline is defined
- component extraction policy is defined
- initial PR checklist is usable
- future feature-specific rules remain intentionally deferred

At that point implementation should begin and the document should evolve from observed product needs rather than speculation.
