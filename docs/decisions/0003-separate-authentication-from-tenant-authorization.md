# ADR 0003: Separate authentication from tenant authorization

## Status

Accepted

## Context

Keycloak authenticates users and issues JWT access tokens. Spring Security
validates those tokens.

A valid token tells us who the user is. It does not prove that the user may
access a specific tenant.

Tenant membership can change while a token is still valid. A user may also
belong to several tenants. Therefore, JWT claims must not be the authoritative
source for tenant membership.

Application and domain code must also remain independent of JWT and Spring
Security.

## Decision

Keycloak is responsible for:

- login and credentials
- authentication flows
- issuing access tokens

The platform service is responsible for validating:

- token signatures
- expiration
- issuer
- audience

An external user is uniquely identified by the combination of the JWT `iss` and
`sub` claims. Mutable values such as username and email are not identity keys.

Keycloak realm roles provide platform-wide permissions such as
`platform-admin`.

The application database is the authoritative source for:

- tenant membership
- tenant-scoped roles
- membership status

Tenant-scoped operations receive the target tenant explicitly. Before accessing
tenant data, the application verifies that the authenticated user has an active
membership in that tenant.

Missing, inactive, or invalid membership results in access being denied.

Only inbound adapters may access JWT or Spring Security objects. They translate
authentication data into framework-independent objects or application-command
fields.

Tenant information may later be added to tokens as an optimization hint, but it
will never replace the application membership check.

## Alternatives considered

### Store tenant membership in JWT claims

Rejected because membership data could remain stale until the token expires.
Tokens could also become large for users who belong to many tenants.

### Manage tenant membership only through Keycloak

Rejected because tenant membership is application data. It must support
application workflows, auditing, and consistency rules.

### Read Spring Security directly from application services

Rejected because it would hide required use-case input and couple application
code to Spring Security and thread-local state.

## Consequences

Tenant authorization uses current membership data instead of potentially stale
token claims.

Authenticated identity and target tenant become explicit, testable use-case
inputs.

The application will need persistent user and tenant-membership models.
Tenant-scoped operations may require an additional database lookup.

Caching may be introduced later if measurements justify it. Membership changes
must invalidate cached authorization data.

Platform-wide roles and tenant-scoped roles remain separate concepts.
