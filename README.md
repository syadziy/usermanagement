# User Management Service

`usermanagement` adalah service Java 21 untuk identity dan authorization multi-tenant. Service
melakukan registrasi tenant, autentikasi username/password, menerbitkan JWT dengan expiry berbeda
untuk setiap tenant, serta mengelola role dan permission granular dalam format `resource:action`.

JVM, JDBC session, persisted timestamps, logs, JWT claims, dan API timestamps menggunakan UTC
secara default melalui `APP_TIMEZONE=UTC`.

## Fitur utama

- Isolasi user, role, permission, dan policy berdasarkan `tenant_id`.
- Registrasi tenant sekaligus membuat owner account tanpa password yang ditanam di source code.
- Bootstrap awal `Syadziy Company` dengan owner `syadziy.owner` dari BCrypt hash environment.
- JWT RSA-SHA256 dengan issuer validation, audience, expiry tenant-specific, dan key ID.
- Discovery metadata dan public JWKS untuk validasi token tanpa membagikan private key.
- Permission granular seperti `user:view`, `user:create`, `user:download`, dan `user:upload`.
- Bootstrap permission gateway `alert:write`, `alert:read-recipients`,
  `alert:manage-recipients`, `alert:read-notifications`, `audit:read`, `scheduler:read`, dan
  `scheduler:manage` untuk role tenant owner.
- Custom permission untuk resource/action milik tenant.
- Role dapat memiliki banyak permission dan user dapat memiliki banyak role.
- Spring method security pada controller melalui authority `PERM_<resource>:<action>`.
- PostgreSQL, Flyway, Spring JDBC, Actuator, Prometheus, ECS logging, trace ID, dan OpenAPI.
- Audit autentikasi login melalui Kafka topic `centralized-audit.requested`; audit request API
  lainnya dimiliki oleh `api_gateway` agar tidak tercatat ganda.
- Centralized alert untuk exception dan HTTP 5xx tanpa mengirim password, token, atau request body.
- Response envelope dan HTTP exception handling dari `sdk-util`.

## Peran sebagai issuer utama

Service ini menjadi issuer JWT utama untuk gateway dan service yang memakai `sdk-util`. Consumer
mengambil public key melalui discovery/JWKS; hanya `usermanagement` yang menyimpan private key.

Service ini menggantikan autentikasi username/password dan RBAC/action authorization dasar
Keycloak untuk internal microservices. Versi ini belum menyediakan identity federation, social login,
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
      ├── SUPERADMIN system role with all permissions
      └── owner user with BCrypt password

Login (tenantKey + username + password)
      │
      ├── resolve tenant and tenant-specific TTL
      ├── verify enabled tenant/user and BCrypt password
      ├── resolve roles + resource:action permissions
      └── issue RS256 JWT containing aud, tenant_id, roles, permissions, scope, iat, exp

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
export DEFAULT_SUPERADMIN_PASSWORD_HASH="$(htpasswd -bnBC 12 syadziy 'replace-with-strong-password' | cut -d: -f2)"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Pada database kosong, migration membuat tenant awal `syadziy-company` dan tenant platform
`superadmin`. Migration V8 membuat akun platform berikut:

- tenant key: `superadmin`
- username: `superadmin`
- email: `superadmin@platform.local`
- password: password yang digunakan untuk menghasilkan `DEFAULT_SUPERADMIN_PASSWORD_HASH`

`DEFAULT_SUPERADMIN_PASSWORD_HASH` wajib berupa BCrypt strength 12; tidak ada password plaintext
di migration. Tenant platform menerima seluruh permission katalog dan hanya tenant tersebut yang
dapat membaca daftar tenant. Migration bersifat idempotent dan tidak mengganti password akun yang
sudah ada.

Port default adalah `9005`. Profile `local` membuat RSA key sementara agar mudah dijalankan. Token
lokal otomatis tidak valid setelah service restart. Shared environment dan production wajib
menyediakan `JWT_PRIVATE_KEY` PKCS#8 dan `JWT_PUBLIC_KEY` X.509 dalam Base64 melalui secret manager,
serta mematikan `JWT_GENERATE_EPHEMERAL_KEY`.

Contoh membuat key production tanpa passphrase pada file runtime:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem
export JWT_PRIVATE_KEY="$(openssl pkcs8 -topk8 -nocrypt -in jwt-private.pem -outform DER | openssl base64 -A)"
export JWT_PUBLIC_KEY="$(openssl pkey -in jwt-private.pem -pubout -outform DER | openssl base64 -A)"
export JWT_KEY_ID="usermanagement-2026-01"
export JWT_GENERATE_EPHEMERAL_KEY=false
export SPRING_PROFILES_ACTIVE=production
```

Simpan nilai Base64 tersebut di secret manager dan hapus file PEM lokal secara aman sesuai prosedur
operasional organisasi. `JWT_AUDIENCES` menerima daftar dipisahkan koma; default-nya
`api-gateway`.

## Docker

Build JAR terlebih dahulu, kemudian buat runtime image Java 21:

```bash
mvn clean package
docker build -t usermanagement:1.0.0 .
docker run --rm --env-file .env -p 9005:9005 usermanagement:1.0.0
```

Isi `.env` dari `.env.example`, ganti seluruh secret, dan gunakan hostname service Docker untuk
PostgreSQL atau dependency container lain. Image berjalan sebagai user non-root dan memakai UTC.

Audit login membutuhkan Kafka yang dapat dijangkau melalui `KAFKA_BOOTSTRAP_SERVERS`; service
`audit_log` harus mengonsumsi topic yang sama dengan `USERMANAGEMENT_AUDIT_TOPIC`. Error operasional
dikirim ke `CENTRALIZED_ALERT_URL`. Atur recipient melalui
`USERMANAGEMENT_ERROR_ALERT_RECIPIENTS` dan authorization header melalui secret
`USERMANAGEMENT_ERROR_ALERT_AUTHORIZATION_HEADER`. Kegagalan login tetap diaudit sebagai `FAILURE`,
tetapi hanya exception dan response 5xx yang menghasilkan centralized alert untuk menghindari
alert noise dari kesalahan autentikasi.

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

Browser menerima JWT melalui cookie `ACCESS_TOKEN` dengan atribut `HttpOnly`, `SameSite=Strict`,
dan `Secure` di luar profile lokal. JavaScript tidak menerima atau menyimpan JWT. Gunakan endpoint
berikut untuk memulihkan dan mengakhiri session:

- `GET /api/v1/auth/session`
- `POST /api/v1/auth/logout`

Postman dan service client tetap dapat menggunakan Bearer token jika token diperoleh melalui alur
non-browser yang tepercaya:

```http
Authorization: Bearer <accessToken>
```

### Discovery dan JWKS

Endpoint berikut public dan digunakan otomatis oleh `sdk-util` serta gateway:

- `GET /.well-known/openid-configuration`
- `GET /.well-known/oauth-authorization-server`
- `GET /oauth2/jwks`

`JWT_ISSUER` harus sama persis dengan nilai `iss` yang dapat diakses consumer. Contoh Docker network
adalah `http://usermanagement:9005`; production sebaiknya memakai URL HTTPS stabil.
Metadata ini ditujukan untuk discovery JWT/JWKS dan tidak menjadikan service sebagai implementasi
OAuth2 authorization-code atau OpenID Connect yang lengkap.

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
  "aud": ["api-gateway"],
  "roles": ["SUPERADMIN"],
  "permissions": ["tenant:update", "user:create", "role:assign"],
  "scope": "role.assign tenant.update user.create",
  "nbf": 1786300000,
  "iat": 1786300000,
  "exp": 1786301800
}
```

Client tidak boleh mempercayai claim tanpa memverifikasi signature, issuer, audience, dan expiry.
Consumer tidak memerlukan private key; public key diperoleh dari JWKS dan dipilih berdasarkan `kid`.

`permissions` dipetakan `sdk-util` menjadi authority `PERM_<resource>:<action>`. Claim `scope`
memakai format ekuivalen `resource.action`, sehingga gateway memperoleh `SCOPE_resource.action`.

Path `/internal/**` tidak memerlukan JWT. Jangan daftarkan path ini pada public ingress; gunakan
network policy atau service mesh untuk membatasi akses hanya dari workload tepercaya.

## Database

Flyway membuat tabel:

- `tenant`
- `user_account`
- `role`
- `permission`
- `user_role`
- `role_permission`

Migration V2 menambahkan permission gateway alert/audit/scheduler untuk tenant yang sudah ada dan
memasangkannya ke role sistem `TENANT_OWNER`. User perlu login kembali agar token baru membawa
permission tersebut.

Migration V4 menambahkan `alert:read-notifications` dan memasangkannya ke role sistem
`TENANT_OWNER` yang sudah ada. Migration V5 membuat role sistem `SUPERADMIN`, memberikan seluruh
permission tenant, lalu memasangkannya ke user utama yang memegang `TENANT_OWNER`. Tenant baru
langsung membuat user utama dengan role `SUPERADMIN`. User perlu login kembali agar JWT membawa
role baru tersebut.

V5 tidak membuat username atau password default. Credential user utama tetap berasal dari proses
registrasi tenant dan password tetap disimpan sebagai BCrypt hash.

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
- Simpan `JWT_PRIVATE_KEY`, database password, dan credential lain pada secret manager.
- Gunakan RSA minimal 2048 bit, key ID stabil, dan HTTPS. Versi ini memublikasikan satu signing key;
  rotasi key akan membuat token lama tidak valid sehingga harus dikoordinasikan dengan login ulang.
- Gunakan TLS untuk seluruh endpoint.
- Batasi CORS dan Actuator pada jaringan/role operasional.
- Tambahkan rate limiting dan account lockout pada login di gateway atau enhancement service.
- Gunakan database role dengan least privilege dan backup terenkripsi.
- Pertimbangkan refresh-token rotation, revocation, MFA, identity federation, dan audit event untuk
  sistem berisiko tinggi.
