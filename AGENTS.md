# AGENTS.md

## Project Overview

`usermanagement` adalah Java 21/Spring Boot 4.1 service untuk multi-tenant identity,
username/password authentication, tenant-specific JWT expiry, role, dan action-level permission.

Prioritas desain:

- Semua identity dan authorization data terisolasi oleh `tenant_id`.
- Password hanya disimpan sebagai BCrypt hash dan tidak pernah dilog.
- JWT RS256 selalu memiliki issuer, audience, subject, issued-at, expiry, tenant claim, dan `kid`.
- Hanya service ini yang menyimpan private key; consumer memvalidasi melalui discovery dan JWKS.
- Permission memakai format stabil `resource:action`.
- Database constraint memperkuat tenant isolation dan uniqueness.
- Service observable melalui ECS log, trace ID, Actuator, dan Prometheus.

## Project Structure

```text
src/main/java/com/mac/usermanagement/
├── config/                 # Security, JWT, clock, password encoder
│   └── properties/         # Type-safe JWT and registration settings
├── controller/             # REST boundaries and SDK response mapping
├── entities/
│   ├── constant/           # Authorization catalog and log fields
│   ├── dto/                # REST contracts
│   ├── mapper/             # Domain-to-response mapping
│   └── model/              # Immutable domain/persistence records
├── repository/impl/        # Tenant-scoped parameterized JDBC
├── service/impl/           # Registration, login, user, role, permission policy
└── utils/
    ├── exception/          # Domain exceptions
    └── handler/            # HTTP exception translation

src/main/resources/
├── db/migration/           # Versioned Flyway migrations
├── json/                   # REST request/response examples
├── application.yaml
└── application-local.yaml
```

## Commands

```bash
cd ../sdk_util && mvn clean install
cd ../usermanagement
mvn test
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Coding and Spring Rules

- Use Java 21, records for immutable contracts/models, constructor injection, `Instant`, and an
  injected `Clock`.
- Use UTC for the JVM, JDBC session, persistence, logs, JWT timestamps, and API timestamps.
  Regional conversion belongs only at an explicit presentation boundary.
- Controllers validate and delegate; services own policy; repositories own SQL and row mapping.
- Return `ResponseDTO` through `ResponseHelper` and reuse SDK exception/logging/OpenAPI support.
- A custom `SecurityFilterChain` is intentional because this service issues and validates its own
  JWT; do not enable the SDK security chain at the same time.
- This custom chain is the documented exception to centralized SDK `permit-all-paths`; do not add
  `sdk.security.permit-all-paths` YAML because the SDK security chain is disabled here.
- Put `@PreAuthorize` with `PERM_<resource>:<action>` on protected controller endpoint methods.
  Service implementations must not carry HTTP endpoint authorization annotations; they still own
  business policy and tenant guards.
- Keep client-facing messages in English and never expose hashes, tokens, SQL, or stack traces.
- Organize every application YAML by major property group with a three-line uppercase banner.

## Multi-Tenancy and Database

- Every user, role, permission, and join query must include `tenant_id`.
- Every path tenant ID must be compared with the authenticated `tenant_id` claim.
- Never trust a request-supplied tenant ID without `TenantAccessGuard`.
- Use `NamedParameterJdbcTemplate`; never concatenate request-derived SQL.
- Every schema change requires a new Flyway migration. Never edit a migration already deployed.
- Preserve composite foreign keys that prevent cross-tenant assignments.
- Keep list operations bounded and introduce real pagination before raising limits.

## Authentication and Authorization

- Passwords require at least 12 characters and are encoded with BCrypt strength 12.
- JWT RSA private keys belong in environment/secret management and must be at least 2048 bits.
- Ephemeral key generation is local-only; shared environments require stable keys and coordinated
  rotation. Existing tokens become invalid when the currently published key is replaced.
- Do not log passwords, hashes, JWTs, Authorization headers, or personal data unnecessarily.
- Tenant-specific TTL must remain between 60 and 86,400 seconds.
- Permission names are lowercase `resource:action`; role names are uppercase snake case.
- The primary user created during tenant registration owns the `SUPERADMIN` system role with all
  tenant permissions. Existing `TENANT_OWNER` users are promoted by Flyway V5; never seed a
  plaintext/default superadmin password in a migration.
- Permission and role changes affect newly issued tokens; document any revocation enhancement.
- Security changes require unauthenticated, forbidden, cross-tenant, expired-token, and permitted
  tests according to the changed behavior.

## Logging and Error Handling

- Use `StructuredLog`, common `LogFields`, and `UserManagementLogFields`.
- Include stable tenant/user/role IDs and `event.action`, `event.outcome`, `event.dataset`.
- REST validation and common errors use SDK `GlobalExceptionHandler`.
- Translate conflicts to HTTP 409 and invalid credentials to a generic HTTP 401 message.
- Never reveal whether tenant or username exists during failed login.

## Testing and Coverage

- Unit-test line coverage must be at least 90% for controllers, mappers, repository
  implementations, service implementations, and exception handlers.
- Measure coverage with JaCoCo; never estimate it from test count.
- Cover tenant normalization/bootstrap, tenant-specific TTL, invalid credentials, tenant mismatch,
  duplicate records, unknown permissions/roles, role assignment, and JWT claims.
- Use Testcontainers PostgreSQL for Flyway, composite constraints, and JDBC integration tests.
- Do not weaken coverage scope, exclusions, or assertions merely to pass the gate.
- If Docker or another dependency is unavailable, report the exact skipped test and never claim it
  passed.

## Before Finishing

1. Preserve unrelated user changes and inspect `git status`.
2. Compile and run focused tests while developing.
3. Run `mvn test`, `mvn clean verify`, and `git diff --check`.
4. Confirm no secret or generated `target/` content is staged.
5. Update README, `.env.example`, JSON examples, and Flyway migration for contract changes.
