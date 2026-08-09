# User Management Service

`usermanagement` adalah service Java 21 untuk identity dan authorization multi-tenant. Service
melakukan registrasi tenant, autentikasi username/password, menerbitkan JWT dengan expiry berbeda
untuk setiap tenant, serta mengelola role dan permission granular dalam format `resource:action`.

JVM, JDBC session, persisted timestamps, logs, JWT claims, dan API timestamps menggunakan UTC
secara default melalui `APP_TIMEZONE=UTC`.

## Fitur utama

- Isolasi user, role, permission, dan policy berdasarkan `tenant_id`.
- Registrasi tenant sekaligus membuat owner account tanpa default password.
- JWT HMAC-SHA256 dengan issuer validation dan expiry tenant-specific.
- Permission granular seperti `user:view`, `user:create`, `user:download`, dan `user:upload`.
- Custom permission untuk resource/action milik tenant.
- Role dapat memiliki banyak permission dan user dapat memiliki banyak role.
- Spring method security melalui authority `PERM_<resource>:<action>`.
- PostgreSQL, Flyway, Spring JDBC, Actuator, Prometheus, ECS logging, trace ID, dan OpenAPI.
- Response envelope dan HTTP exception handling dari `sdk-util`.

## Batasan dibanding Keycloak

Service ini dapat menggantikan autentikasi username/password dan RBAC/action authorization dasar
untuk internal microservices. Versi ini belum menyediakan identity federation, social login,
SAML, full OAuth2 authorization-code flow, user self-service, MFA, refresh token, token revocation,
admin console, atau centralized session management seperti Keycloak.

Perubahan role, permission, TTL, atau status user berlaku pada token yang diterbitkan berikutnya.
JWT yang sudah diterbitkan tetap valid sampai expiry, sehingga gunakan TTL pendek jika perubahan
akses harus cepat berlaku.

## Teknologi

| Komponen | Implementasi |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 4.1.0 |
| REST | Spring MVC + Jakarta Validation |
| Security | Spring Security resource server + self-issued JWT |
| Password | BCrypt strength 12 |
| Database | PostgreSQL + Spring JDBC |
| Migration | Flyway |
| Observability | Actuator, Prometheus, ECS logging, trace ID |
| Shared library | `com.mac:sdk-util:1.0.0` |

## Alur utama

```text
Register tenant
      │
      ├── tenant + tenant token TTL
      ├── default permission catalog
      ├── TENANT_OWNER role with all permissions
      └── owner user with BCrypt password

Login (tenantKey + username + password)
      │
      ├── resolve tenant and tenant-specific TTL
      ├── verify enabled tenant/user and BCrypt password
      ├── resolve roles + resource:action permissions
      └── issue JWT containing tenant_id, roles, permissions, iat, exp

Protected request
      │
      ├── verify signature, issuer, expiry, and tenant_id claim
      ├── map JWT claims to ROLE_* and PERM_* authorities
      └── reject path tenantId != token tenant_id
```

## Menjalankan lokal

Prasyarat: JDK 21, PostgreSQL, Maven, dan `sdk-util:1.0.0` pada local Maven repository.

```bash
cd ../sdk_util
mvn clean install
cd ../usermanagement

createdb usermanagement
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Port default adalah `9005`. Salin `.env.example` ke environment lokal dan ganti `JWT_SECRET`.
Jangan memakai default development secret pada shared environment atau production.

## Docker

Build JAR terlebih dahulu, kemudian buat runtime image Java 21:

```bash
mvn clean package
docker build -t usermanagement:1.0.0 .
docker run --rm --env-file .env -p 9005:9005 usermanagement:1.0.0
```

Isi `.env` dari `.env.example`, ganti seluruh secret, dan gunakan hostname service Docker untuk
PostgreSQL atau dependency container lain. Image berjalan sebagai user non-root dan memakai UTC.

## REST API

Contoh request dan response lengkap tersedia di `src/main/resources/json/index.json`.

### 1. Register tenant dan owner

`POST /api/v1/tenants` adalah public endpoint dan dapat dimatikan dengan
`TENANT_REGISTRATION_ENABLED=false` setelah provisioning selesai.

```json
{
  "tenantKey": "acme-id",
  "tenantName": "ACME Indonesia",
  "accessTokenTtlSeconds": 1800,
  "ownerUsername": "tenant.owner",
  "ownerEmail": "owner@example.com",
  "ownerPassword": "replace-with-strong-password"
}
```

### 2. Login

`POST /api/v1/auth/login`

```json
{
  "tenantKey": "acme-id",
  "username": "tenant.owner",
  "password": "replace-with-strong-password"
}
```

Gunakan access token pada endpoint lain:

```http
Authorization: Bearer <accessToken>
```

### 3. Tenant token policy

`PATCH /api/v1/tenants/{tenantId}/token-policy` membutuhkan `tenant:update`.

### 4. User

- `POST /api/v1/tenants/{tenantId}/users` membutuhkan `user:create`.
- `GET /api/v1/tenants/{tenantId}/users` membutuhkan `user:view` dan dibatasi 100 user.
- `PUT /api/v1/tenants/{tenantId}/users/{userId}/roles` membutuhkan `role:assign`.

### 5. Role dan permission

- `POST /api/v1/tenants/{tenantId}/roles` membutuhkan `role:create`.
- `GET /api/v1/tenants/{tenantId}/roles` membutuhkan `role:view`.
- `PUT /api/v1/tenants/{tenantId}/roles/{roleId}/permissions` membutuhkan `role:edit`.
- `POST /api/v1/tenants/{tenantId}/permissions` membutuhkan `permission:create`.
- `GET /api/v1/tenants/{tenantId}/permissions` membutuhkan `permission:view`.

Role request memakai permission authority penuh:

```json
{
  "name": "REPORT_OPERATOR",
  "description": "Can view and download reports",
  "permissions": ["report:view", "report:download"]
}
```

## JWT claims

```json
{
  "iss": "http://localhost:9005",
  "sub": "user-uuid",
  "username": "tenant.owner",
  "tenant_id": "tenant-uuid",
  "tenant_key": "acme-id",
  "roles": ["TENANT_OWNER"],
  "permissions": ["tenant:update", "user:create", "role:assign"],
  "iat": 1786300000,
  "exp": 1786301800
}
```

Client tidak boleh mempercayai claim tanpa memverifikasi signature, issuer, dan expiry. Secret JWT
yang sama dibutuhkan oleh service consumer jika masih menggunakan HMAC. Untuk deployment dengan
banyak consumer atau trust boundary berbeda, migrasikan signing ke RSA/EC dan publikasikan JWKS.

## Database

Flyway membuat tabel:

- `tenant`
- `user_account`
- `role`
- `permission`
- `user_role`
- `role_permission`

Unique constraint dan foreign key komposit memastikan username/email unik per tenant serta
mencegah assignment user, role, atau permission lintas tenant.

## Observability

- Health: `GET /actuator/health`
- Prometheus: `GET /actuator/prometheus`
- Metrics: `GET /actuator/metrics`
- HTTP trace: `X-Correlation-Id`
- Structured business fields: `event.action`, `event.outcome`, `event.dataset`, `tenant.id`,
  `user.id`, dan `role.id`

Password, JWT, dan credential tidak boleh ditulis ke log.

## Build dan test

```bash
mvn test
mvn clean verify
```

JaCoCo menggagalkan `verify` bila line coverage production business code berada di bawah 90%.
Integration test Flyway menggunakan PostgreSQL Testcontainers dan otomatis di-skip jika Docker
tidak tersedia.

## Production checklist

- Set `TENANT_REGISTRATION_ENABLED=false` jika tenant tidak boleh mendaftar sendiri.
- Simpan `JWT_SECRET`, database password, dan credential lain pada secret manager.
- Gunakan secret acak minimal 256 bit dan lakukan rotation terencana.
- Gunakan TLS untuk seluruh endpoint.
- Batasi CORS dan Actuator pada jaringan/role operasional.
- Tambahkan rate limiting dan account lockout pada login di gateway atau enhancement service.
- Gunakan database role dengan least privilege dan backup terenkripsi.
- Pertimbangkan asymmetric signing, JWKS, refresh-token rotation, revocation, MFA, dan audit event
  sebelum menggantikan Keycloak untuk sistem berisiko tinggi.
