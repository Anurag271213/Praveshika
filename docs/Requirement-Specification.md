# Login Feature — Requirement Specification

**Version:** 1.0
**Date:** 2026-10-07
**Status:** Completed (Phase 5 — Verified & Demo Ready)
**Audience:** Intern developers, QA engineers, technical reviewers

---

## 1. Project Title

**Login Feature Development — Intern Training Program Reference Implementation**

A production-quality, industry-standard username/email + password login subsystem built to train intern developers on modern full-stack practices (Java/Spring Boot backend, React frontend, JWT authentication, MySQL persistence).

---

## 2. Project Objective

Deliver a small but **complete**, **secure**, and **well-layered** login feature that demonstrates:

1. Correct separation of concerns (frontend client → REST API → service layer → data layer → database).
2. Secure authentication based on signed JWT tokens issued after successful credential verification against a MySQL database.
3. Strong password hygiene (BCrypt hashing, never exposing secrets).
4. Proper input validation on both client and server sides with clear, non-leaking error responses.
5. Consistent HTTP status codes, API contracts, and global exception handling.
6. Logout semantics that correctly remove the client-side authentication token.
7. A codebase that is clean, maintainable, fully unit-testable, and can serve as a teaching reference for the intern training program.

---

## 3. Scope

### 3.1 In Scope

- Backend REST API module (Spring Boot 3.x / Java 17 / Maven) exposing authentication endpoints.
- React 19 + Vite frontend login UI with a protected route (demo "dashboard" after login).
- MySQL 8 persistence layer (`users` table) via Spring Data JPA.
- JWT-based stateless authentication with BCrypt password verification.
- Bean Validation on every DTO plus matching client-side validation rules.
- Centralized exception handling returning a consistent JSON error envelope.
- Secure default admin-user seeding on first startup (BCrypt-hashed, env-configurable).
- Logout action on the client that invalidates the local auth session.
- Unit tests for backend service logic and validation.
- Frontend build verification via production build.

### 3.2 Out of Scope

See **Section 16 — Out-of-Scope Features** for the explicit exclusion list.

---

## 4. Functional Requirements

Each requirement is uniquely identifiable and testable.

| ID   | Requirement Statement |
|------|-----------------------|
| FR-01 | User must be able to enter **either a username or an email** into the same identifier field on the login form; the backend shall accept the same value and look up the user by either attribute. |
| FR-02 | User must be able to enter a **password** into a masked input field on the login form. |
| FR-03 | The system must validate that mandatory fields (identifier + password) are present **both on the client side (before submission)** and **on the server side (before business logic)**, rejecting requests that miss either. |
| FR-04 | On valid credentials the system must authenticate the user by: (a) looking up the user record by username OR email; (b) comparing the submitted password with the stored BCrypt hash; (c) issuing an authentication token on success. |
| FR-05 | On invalid credentials (identifier not found **OR** password does not match) the system must reject the request with a single non-specific auth failure response and MUST NOT reveal which part was wrong. |
| FR-06 | The system must return a structured, consistent, machine-readable JSON error response for every failure path: validation errors (per-field), authentication errors, access-denied errors, and unexpected server errors. |
| FR-07 | After successful authentication the backend must return an HTTP 200 response containing: a signed JWT access token, token type (`Bearer`), token expiry duration (seconds), and a user-info object (`id`, `username`, `email` only — never the password or its hash). |
| FR-08 | The system must allow an authenticated user to **logout**: the frontend SHALL remove the locally stored JWT token and navigate to the login screen. (Server-side token invalidation via token blacklist is explicitly out of scope for this phase — see Section 16.) |
| FR-09 | Passwords MUST be stored **exclusively** as BCrypt hashes — the raw password must never be persisted. The application MUST use a BCryptPasswordEncoder (or equivalent) with a strength parameter appropriate for the target hardware. |
| FR-10 | Passwords (raw or hashed) MUST NOT appear in API responses, log statements, or any diagnostic output. Any auto-generated logging that could leak a credential field must be explicitly suppressed. |
| FR-11 | The backend must expose a single HTTP POST endpoint for login at the path `/api/auth/login` consuming `application/json` and producing `application/json`. |
| FR-12 | The backend must expose an HTTP POST endpoint for logout at `/api/auth/logout`. Because tokens are JWT (stateless), the endpoint exists for audit/logging, returns HTTP 200, and actual invalidation semantics are client-side token removal. |
| FR-13 | On first application startup, if no user exists in the `users` table, the system MUST seed one default admin user whose username, email, and password are sourced from configuration (never hardcoded); the password is hashed with BCrypt before insertion. |
| FR-14 | Any attempt to access a protected resource without a valid JWT (missing, malformed, expired, or incorrectly signed) must be rejected with HTTP 401 Unauthorized. |
| FR-15 | If a request contains a syntactically valid JWT but the token's subject no longer exists in the database (e.g., user was deleted after issuance), the request must still be rejected with HTTP 401. |
| FR-16 | The login identifier input field must accept values that are either a valid email (containing `@`) OR a valid username (no `@`). When the value contains `@`, it must additionally be validated as a well-formed email on the backend. |

---

## 5. Non-Functional Requirements

| ID      | Category              | Requirement Statement |
|---------|-----------------------|-----------------------|
| NFR-01  | Performance           | Backend login endpoint (happy path) must respond in **≤ 500 ms** p95 under a light concurrent load of 10 concurrent users on developer-grade hardware (MySQL co-located). |
| NFR-02  | Availability          | Login endpoint must return valid JSON (either success or error envelope) for 100% of well-formed HTTP requests; no unhandled exceptions escaping to the servlet container. |
| NFR-03  | Scalability           | Architecture must be **stateless** on the server side (JWT in `Authorization` header; no HTTP session storage) so that instances can be horizontally scaled behind a load balancer without sticky sessions. |
| NFR-04  | Maintainability       | Code MUST follow a **layered architecture**: `controller` (HTTP), `service` (business logic), `repository` (data access), `entity` (domain), `dto` (contract), `exception` (errors), `security` (authN/authZ), `config` (configuration). Layers may only depend inward. |
| NFR-05  | Testability           | All business logic classes (services, validators, JWT provider) must be dependency-injected with interfaces where practical so they can be unit-tested in isolation with mocked collaborators. |
| NFR-06  | Portability / Config  | **Zero hardcoded secrets or credentials in source.** All DB connection info, JWT signing secret, default admin credentials, server port, and token TTL MUST be sourced from environment variables or externalized configuration (`application.properties` supports env placeholders with sensible development defaults). |
| NFR-07  | Observability         | All entry/exit points of business interest must emit structured SLF4J log lines at appropriate levels: `INFO` for login success/failure counts, `DEBUG` for request trace, `WARN` for repeated failed auth, `ERROR` for unhandled exceptions. Logs MUST NOT include passwords or tokens. |
| NFR-08  | API Consistency       | Every API response (success or error) must be valid JSON. Error responses use a single envelope shape (see Section 12/12.1). Dates use ISO-8601. |
| NFR-09  | Compatibility         | Backend targets Java 17 LTS + Spring Boot 3.x. Frontend targets React 19 + Vite (latest stable) with JavaScript. MySQL driver targets MySQL 8.x. |
| NFR-10  | Build Quality         | Backend `mvn clean verify` (compile + unit tests) must succeed before merging. Frontend `npm run build` must succeed with zero build errors. |
| NFR-11  | Dependency Hygiene    | Unused dependencies MUST NOT be added. Every `pom.xml` and `package.json` dependency must be justifiable against the 10 core features. |
| NFR-12  | Security (CORS)       | Backend CORS policy must be explicitly configured; no global wildcard credentials. Development environment may allow `http://localhost:5173` (Vite default). |
| NFR-13  | Input Sanitization    | All user-supplied strings (identifier, password) are validated for length before reaching business logic. No HTML/JS sanitization is required for these fields (they are never rendered as HTML by the backend), but the frontend will render them only via React's safe text interpolation. |

---

## 6. User Roles / Personas Involved

The login feature itself has no role-based authorization (see Section 16), but two concrete personas interact with it:

### Role 1 — End User / Application User (`USER`)
- Has an existing record in the `users` table (username, email, BCrypt password hash).
- Uses the login UI to authenticate; after success, consumes protected app features.
- Can **logout** to clear their session.

### Role 2 — System Bootstrap / Default Administrator  (`ADMIN-SEED`)
- The first (seeded) user created automatically on initial startup if the `users` table is empty.
- Has credentials fully driven by environment variables: `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.
- Note: role-based access control (RBAC) for this user vs regular users is **out of scope** for the login feature itself — the seed exists only so the system always has at least one credential available for testing/training.

### Role 3 — QA / Intern Reviewer (non-system)
- Executes both manual test scripts and automated suites against the acceptance criteria in Section 15.
- Not authenticated by the system, but consumes logs and API responses during verification.

---

## 7. Login Workflow

### 7.1 High-Level Flow

```
 ┌──────────────┐        ┌────────────────┐        ┌─────────────────┐        ┌─────────────┐
 │  React UI    │  (1)   │ Spring Security│  (2)   │   AuthService   │  (3)   │  MySQL 8    │
 │  Login Form  │───────▶│ Filter Chain + │───────▶│ + BCryptEncoder │───────▶│  users tbl  │
 │              │◀───────│ JWT Filter     │◀───────│ + JwtProvider   │◀───────│             │
 └──────────────┘  (7)   └────────────────┘  (6)   └─────────────────┘  (4/5) └─────────────┘
       JWT stored
       in-memory/local
```

### 7.2 Step-by-Step Happy Path

1. User navigates to `/login`; browser renders Login UI (2 input fields + Submit button).
2. User enters identifier (username or email) and password, clicks **Login**.
3. **Client-side validation** runs immediately. If fields fail → inline error messages displayed, request NOT sent.
4. If valid → React client POSTs JSON body `{ usernameOrEmail, password }` to `/api/auth/login` via Axios.
5. Spring `JwtAuthenticationFilter` allows unauthenticated access to `/api/auth/**` (permitted-all list).
6. `AuthController` receives the request; `@Valid` triggers **server-side Bean Validation** on `LoginRequest`. If invalid → `GlobalExceptionHandler` returns HTTP 400 with per-field errors.
7. `AuthService.authenticate(request)` is invoked:
   - (a) Calls `UserRepository.findByUsernameOrEmail(…)` → returns `Optional<User>`.
   - (b) If empty → short-circuit (do NOT run BCrypt unnecessarily); throw `InvalidCredentialsException`.
   - (c) If present → invoke `BCryptPasswordEncoder.matches(rawPassword, user.getPasswordHash())` in **constant time**.
   - (d) If BCrypt returns `false` → throw `InvalidCredentialsException`.
   - (e) If match → call `JwtTokenProvider.generateToken(userId, username, email)` → signed JWT string.
8. Controller builds `LoginResponse` (`token`, `tokenType="Bearer"`, `expiresIn=…`, `user={id,username,email}`) → HTTP 200 OK.
9. React client stores the `token` in `localStorage` (or memory-store) under the key `auth.token`.
10. Client React Router redirects to the protected route `/dashboard`.
11. Every subsequent API call to a protected endpoint includes the header `Authorization: Bearer <token>`.
12. `JwtAuthenticationFilter` validates signature + expiry + subject on each protected request; `SecurityContextHolder` populated with an authenticated principal.

### 7.3 Logout Workflow

1. User clicks **Logout** in the UI navigation bar.
2. React client:
   - Calls `POST /api/auth/logout` (fire-and-forget for audit logging; returns HTTP 200 `{ "message": "Logged out successfully" }`).
   - Removes `auth.token` (and any cached user info) from `localStorage`/memory.
   - React Router navigates back to `/login`.
3. Any attempt to revisit `/dashboard` without a token → redirect to `/login`.

---

## 8. Input Fields

### 8.1 Frontend Form Fields

| Field Key          | HTML Element           | Label              | Placeholder                 | Maxlength | Autocomplete         |
|--------------------|------------------------|--------------------|-----------------------------|-----------|----------------------|
| `usernameOrEmail`  | `<input type="text">`  | Username or Email  | `username or you@example.com` | 100     | `username`           |
| `password`         | `<input type="password">` | Password       | `••••••••`                  | 128       | `current-password`   |

The form also has one **primary button**: `Login` (disabled while submission is in-flight).

### 8.2 Backend DTO Fields (`LoginRequest`)

| Field               | Java Type | Constraints (Bean Validation)                          |
|---------------------|-----------|--------------------------------------------------------|
| `usernameOrEmail`   | `String`  | `@NotBlank`, `@Size(max=100)`, plus conditional `@Email` check when value contains `@` (custom validator or service-level check) |
| `password`          | `String`  | `@NotBlank`, `@Size(min=6, max=128)`                   |

### 8.3 Persistent Entity Fields (`User`)

| Column              | Java Type      | Constraints                          |
|---------------------|----------------|--------------------------------------|
| `id`                | `Long`         | PK, `IDENTITY` auto-generated        |
| `username`          | `String(50)`   | `NOT NULL`, `UNIQUE`                 |
| `email`             | `String(100)`  | `NOT NULL`, `UNIQUE`                 |
| `password_hash`     | `String(255)`  | `NOT NULL` — stores BCrypt `$2a$…`  |
| `created_at`        | `LocalDateTime`| `NOT NULL`, auto on insert           |
| `updated_at`        | `LocalDateTime`| `NOT NULL`, auto on insert + update  |

---

## 9. Validation Rules

Validation is enforced **twice** (client and server) per the defense-in-depth principle.

### 9.1 Universal Rules (Both Sides)

| Rule ID | Applies To            | Rule |
|---------|-----------------------|------|
| V-01    | `usernameOrEmail`     | **Must not be blank** (null, empty string, or whitespace-only are all rejected). |
| V-02    | `usernameOrEmail`     | **Maximum 100 characters** (DB/schema limit). |
| V-03    | `usernameOrEmail`     | **If the value contains `@`, it must be a well-formed email address.** A valid email here means: exactly one `@`, a local part on the left, a domain with at least one dot on the right (standard RFC 5322 subset used by Jakarta `@Email` validator). |
| V-04    | `password`            | **Must not be blank.** |
| V-05    | `password`            | **Minimum 6 characters.** A deliberately low bar for the training project; noted as a configurable baseline. |
| V-06    | `password`            | **Maximum 128 characters** (protects against extreme input sizes; BCrypt itself caps at 72 bytes of entropy, but the enforced limit prevents pathological payloads). |

### 9.2 Server-Only / Semantic Validation

| Rule ID | Rule |
|---------|------|
| V-07    | If lookup by username/email returns no user → immediately produce the same generic auth failure as a wrong password (per V-08). |
| V-08    | **Credential ambiguity (non-leakage):** regardless of whether the identifier was not found OR the password did not match, the system MUST respond with exactly the same `401 Unauthorized` body and the same generic message: `"Invalid username/email or password"`. Response timing SHOULD be made as uniform as practical (always run at least one BCrypt-like workload; a minimum-cost `PasswordEncoder.upgradeEncoding`/dummy hash comparison on the empty-found branch is recommended if measurable timing differences appear in testing). |
| V-09    | JWT presented to a protected endpoint must pass all of: signature check, `exp` claim (not expired), `iss` claim matches expected issuer, and `sub` claim corresponds to an existing user in the database. Any failure → HTTP 401 with generic "Invalid or expired token". |

### 9.3 Validation Failure Presentation

- **Client side:** Inline red text below each offending field; form NOT submitted.
- **Server side (HTTP 400):** JSON envelope with per-field `fieldErrors[]` array (`{ field, message }`) + top-level `message: "Validation failed"`.
- **Server side (HTTP 401/403/500):** JSON envelope with a single top-level `message` (no field-level granularity — prevents enumeration attacks).

---

## 10. Success Conditions

A request is considered **successful** (HTTP 200 OK) **only if ALL of the following hold simultaneously**:

1. The HTTP request is a valid `POST /api/auth/login` with `Content-Type: application/json`.
2. The JSON body deserializes cleanly to a `LoginRequest` object.
3. Every validation rule in Section 9.1 (V-01…V-06) passes on the server.
4. A `User` row exists whose `username` equals the submitted identifier **OR** whose `email` equals the submitted identifier (case-insensitive comparison may be enforced at DB column collation; application lookup is exact match).
5. `BCryptPasswordEncoder.matches(submittedPassword, storedPasswordHash)` returns `true`.
6. The JWT provider is able to produce a signed token without exception.
7. The response includes:
   - HTTP status `200 OK`
   - Header `Content-Type: application/json`
   - Body shape exactly matching `LoginResponse`:
     ```json
     {
       "token": "<base64url-signed-jwt>",
       "tokenType": "Bearer",
       "expiresIn": 86400,
       "user": { "id": 1, "username": "admin", "email": "admin@example.com" }
     }
     ```
8. After the client stores the token, **any subsequent request** to a protected endpoint with header `Authorization: Bearer <token>` is accepted (HTTP 2xx/3xx) until the token expires or is removed locally.

The **Logout success condition** is simpler:

- HTTP `POST /api/auth/logout` → HTTP 200 `{ "message": "Logged out successfully" }`; client removes the token and redirects to `/login`.

---

## 11. Failure Conditions & Responses

Every failure path returns a **consistent JSON envelope**.

### 11.1 Common Error Envelope (`ApiErrorResponse`)

```json
{
  "timestamp": "2026-10-07T10:15:30.123456Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/login",
  "fieldErrors": [
    { "field": "password", "message": "Password must be between 6 and 128 characters" }
  ]
}
```

`fieldErrors` is only present on **validation failures** (HTTP 400). For all other statuses it is `null` or omitted (Jackson `non_null`).

### 11.2 Failure Matrix

| #  | Scenario                                                         | HTTP Code          | `message` field                                    | Notes |
|----|------------------------------------------------------------------|--------------------|----------------------------------------------------|-------|
| F-01 | Blank / whitespace-only `usernameOrEmail`                       | **400 Bad Request** | `Validation failed` + per-field error             | `fieldErrors` populated |
| F-02 | `usernameOrEmail` over 100 chars                                 | **400 Bad Request** | `Validation failed`                                | `fieldErrors` populated |
| F-03 | Value contains `@` but fails email format                        | **400 Bad Request** | `Validation failed`                                | `fieldErrors.usernameOrEmail = "Must be a valid email address"` |
| F-04 | Blank / whitespace-only `password`                               | **400 Bad Request** | `Validation failed`                                | `fieldErrors` populated |
| F-05 | Password < 6 characters                                          | **400 Bad Request** | `Validation failed`                                | `fieldErrors` populated |
| F-06 | Password > 128 characters                                        | **400 Bad Request** | `Validation failed`                                | `fieldErrors` populated |
| F-07 | Identifier not found in DB                                       | **401 Unauthorized** | `Invalid username/email or password`              | **Exact same text as F-08** to prevent user enumeration. |
| F-08 | Identifier found but BCrypt `matches(…)` = `false`               | **401 Unauthorized** | `Invalid username/email or password`              | **Exact same text as F-07**. |
| F-09 | Request body is malformed JSON / missing field types             | **400 Bad Request** | `Validation failed` or `Bad Request`               | Handled by `GlobalExceptionHandler` / Spring defaults. |
| F-10 | Unauthenticated request to a protected endpoint (no JWT)         | **401 Unauthorized** | `Invalid or expired token`                         | JWT filter sets status. |
| F-11 | Protected request with JWT that is malformed / bad signature     | **401 Unauthorized** | `Invalid or expired token`                         | |
| F-12 | Protected request with JWT past its `exp` claim                  | **401 Unauthorized** | `Invalid or expired token`                         | |
| F-13 | Protected request with JWT whose `sub` (user id) was deleted     | **401 Unauthorized** | `Invalid or expired token`                         | |
| F-14 | Any unexpected RuntimeException / SQLException bubbling up       | **500 Internal Server Error** | `An unexpected error occurred. Please try again later.` | **Full stack trace logged server-side only; safe message to client.** |
| F-15 | CORS preflight/origin disallowed                                 | Rejected by browser / HTTP 403 | — | Controlled by `CorsConfigurationSource`. |

---

## 12. Assumptions

1. **Network transport (TLS):** In production deployments the API and frontend are served over HTTPS (TLS 1.2+). The project itself does not enforce TLS at the container level, but the documentation will flag that JWT over plain HTTP is insecure.
2. **JWT lifetime and revocation:** A non-revocable, short-lived access token is acceptable for this training scope. Refresh tokens and token blacklists are explicitly out of scope (Section 16).
3. **User provisioning:** The login feature does **not** include self-service registration; users exist because an admin or bootstrap seeder created them.
4. **MySQL availability:** A MySQL 8 instance is reachable from the backend process at the configured `DATABASE_URL` with the supplied credentials. In local development this is typically `localhost:3306`; in CI/tests we use an H2 in-memory database profile.
5. **Client-side storage choice:** For simplicity and training clarity the JWT is persisted in `localStorage`. Interns will be taught that this is a trade-off (subject to XSS if other vulnerabilities exist) vs. `HttpOnly` cookies, and the decision is acceptable for a single-device training demo.
6. **Single-tenant / single-realm authentication:** There is exactly one user table, one JWT issuer, and one BCrypt encoding strength globally. Multi-tenancy and realm separation are out of scope.
7. **Password policy baseline:** 6-character minimum is the baseline for the training program. A production deployment would raise this and add complexity rules; acceptance criteria AC-09 (Section 15) explicitly tests the 6-character floor.
8. **Browser environment:** The React UI is tested against evergreen desktop browsers (latest two versions of Chrome, Firefox, Edge). Legacy IE11/Safari <16 compatibility is not required.

---

## 13. Constraints

1. **Technology stack is fixed per the project brief:**
   - Backend: Java 17, Spring Boot 3.x, Spring Web, Spring Data JPA, Spring Security, Bean Validation, JWT, BCrypt, Maven.
   - Database: MySQL 8 (primary) + H2 (tests only).
   - Frontend: React 19, Vite, JavaScript, Axios, React Router.
   - **No substitutions are permitted** without a change request.
2. **No placeholder implementations.** Every service method listed in this spec must ship with working code; no `throw new UnsupportedOperationException(…)` or `// TODO` stubs in production code paths.
3. **No hardcoded credentials or JWT secrets in source.** All sensitive values come from environment variables or `application.properties` placeholders.
4. **Compatible dependency versions only.** All Maven/JAR dependencies must resolve cleanly against the Spring Boot 3.3.x BOM; frontend `package.json` must not contain version conflicts that break a fresh `npm install`.
5. **Layer crossing is forbidden:** Controllers may not directly reference repositories; entities may not appear in HTTP responses (only DTOs).
6. **Passwords never leave the backend boundary as plaintext.** The UI transmits the password in the body of an HTTPS POST request (this is unavoidable in a standard form login), but after the request body is bound to `LoginRequest` the raw password is:
   - compared to BCrypt,
   - then immediately becomes unreferenced and eligible for GC.
   It is never written to a log, response, audit table, or message queue.
7. **Compilation gate:** Every backend change must pass `mvn clean test`. Every frontend change must pass `npm run build`. Both gates are automated before marking a phase complete.
8. **No duplicate classes / configs.** Before adding any new class or configuration file the existing package structure must be searched to avoid creating a duplicate.
9. **Public API contract stability.** Once Phase 3 finalizes the JSON contract for `/api/auth/login`, `/api/auth/logout`, and the error envelope, frontend and backend must remain in lockstep; changing the contract requires updating both and incrementing a spec version.

---

## 14. Security Requirements

This section expands on FR-09, FR-10, and V-08/V-09 into auditable security controls.

| ID      | Requirement Statement                                                                                                            | Mitigates                        |
|---------|----------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| SEC-01  | **BCrypt-only password hashing:** Every persisted credential (seeded admin and any future provisioning paths) MUST use `BCryptPasswordEncoder` with strength ≥ 10. The encoding identifier column MUST start with `$2a$` / `$2b$` / `$2y$`. | Offline credential theft         |
| SEC-02  | **No credential logging:** Slf4j log statements MUST NEVER interpolate or print `password`, `passwordHash`, `token` (JWT), or the full `LoginRequest` DTO. If request-body logging is ever added, these fields MUST be redacted via a custom `ResponseBodyAdvice` / filter. | Log injection / log leak         |
| SEC-03  | **No credential reflection in responses:** The server never echoes back or confirms values of `usernameOrEmail` or `password` in any error message. The DTO serializer (`LoginResponse`) only exposes `id`, `username`, `email`. | Response-side credential leak    |
| SEC-04  | **User enumeration prevention:** Identical `401 Unauthorized` response bodies, response lengths, and (as much as practical) response timing for "user not found" vs. "wrong password". | Username enumeration             |
| SEC-05  | **JWT signing algorithm:** Only HMAC-SHA-256 (HS256) with a **minimum 256-bit secret** is permitted. `none` algorithm must be rejected by the JJWT parser configuration. | Algorithm-confusion attacks      |
| SEC-06  | **JWT claims validation:** On every read, the JWT MUST verify: signature, `exp` (not before), `iat` (present), `iss` equals the expected `app.jwt.issuer`, `sub` is non-empty numeric user ID. | Token tampering, token reuse     |
| SEC-07  | **Stateless sessions:** `SessionCreationPolicy.STATELESS`. `JSESSIONID` cookie must never be set by Spring Security for this application. | Session hijacking via cookies    |
| SEC-08  | **CORS least privilege:** In production `allowedOrigins` is an explicit allow-list; `allowCredentials=true` is set only if needed; development configuration may allow `http://localhost:5173`. | Cross-origin abuse               |
| SEC-09  | **CSRF disabled appropriately:** Because the architecture uses JWT via `Authorization` header (not cookie-based auth), CSRF protection can and should be turned off for this app. The rationale must be documented in `SecurityConfig`. | Unnecessary attack surface       |
| SEC-10  | **Secret management:** `JWT_SECRET`, `DATABASE_PASSWORD`, and `ADMIN_PASSWORD` must never appear in source control. A `.env.example` file may list keys with blanks; actual `.env` is gitignored. | Source-control credential leak   |
| SEC-11  | **Binding length limits:** Bean Validation `@Size` max values mirror DB column sizes to prevent truncation and DoS via oversized input. | Oversized input / DoS            |
| SEC-12  | **Rate limiting / brute force baseline:** For the training project itself, implementation of an explicit rate limiter (e.g., Bucket4j) is OPTIONAL. The spec requires at minimum that repeated failed attempts produce `WARN`-level log output so downstream WAF/rate-limit tooling can act on them. | Online brute force (baseline)    |
| SEC-13  | **Parameterized queries only:** Spring Data JPA repository methods use named parameters internally. Native SQL (if ever added) MUST use `@Param` binding, never string concatenation. | SQL injection                    |
| SEC-14  | **Logout UX:** After client-side logout, any page that re-opens in a new tab must NOT auto-authenticate purely from cached DOM state; the app must re-read `localStorage` on load. | Session persistence after logout |
| SEC-15  | **Least-privilege DB user (deployment guidance):** The `DATABASE_USERNAME` account should be granted only `SELECT, INSERT, UPDATE` on the application schema, never `SUPER` or file privileges. | DB lateral movement              |

---

## 15. Acceptance Criteria

An automated/manual test pass of the login feature is **green** only when every item below is true. Items tagged `[A]` are automated; `[M]` are manual.

### 15.1 Build & Deploy

| ID       | Criterion | Kind |
|----------|-----------|------|
| AC-01    | Backend builds without errors: `mvn clean test` exits with code 0. | [A] |
| AC-02    | Frontend builds without errors: `npm run build` exits with code 0. | [A] |
| AC-03    | Backend starts cleanly and logs `Started LoginFeatureApplication in …s`; no WARN-level exception stack traces on startup. | [A]/[M] |
| AC-04    | On a fresh empty `users` table exactly one admin user is seeded with the env-configured username/email; `SELECT password_hash FROM users` starts with `$2a$` or `$2b$`. | [M] |

### 15.2 Validation

| ID       | Criterion | Kind |
|----------|-----------|------|
| AC-05    | POST `/api/auth/login` with `{ usernameOrEmail: "", password: "anything" }` → HTTP 400 and a field error on `usernameOrEmail`. | [A] |
| AC-06    | POST `/api/auth/login` with `{ usernameOrEmail: "validUser", password: "" }` → HTTP 400 and a field error on `password`. | [A] |
| AC-07    | POST `/api/auth/login` with `{ usernameOrEmail: "not-an-email@", password: "p@ssw0rd" }` → HTTP 400 and a field error on `usernameOrEmail` (email malformed). | [A] |
| AC-08    | POST `/api/auth/login` with `{ usernameOrEmail: "validUser", password: "12345" }` (5 chars) → HTTP 400 and a field error on `password`. | [A] |
| AC-09    | POST `/api/auth/login` with `{ usernameOrEmail: "validUser", password: "123456" }` (6 chars) → NOT blocked by validation (passes V-05) — auth itself proceeds to step F-07/F-08 (may still be 401 if user doesn't exist, but must not be a validation 400). | [A] |
| AC-10    | The same validation rules fire on the frontend BEFORE any HTTP request is made; DevTools Network panel shows zero requests when submitting empty fields. | [M] |

### 15.3 Authentication (Happy & Unhappy Paths)

| ID       | Criterion | Kind |
|----------|-----------|------|
| AC-11    | POST `/api/auth/login` with the correct seeded admin username + correct password → HTTP 200, body contains non-empty `token` (3 JWT segments separated by `.`), `tokenType === "Bearer"`, `expiresIn > 0`, `user.id > 0`, `user.username` and `user.email` match. Body MUST NOT contain any key containing `password`. | [A] |
| AC-12    | Same as AC-11 but using the admin **email** (not username) as `usernameOrEmail` → identical 200 response shape. | [A] |
| AC-13    | Correct username, wrong password → HTTP 401. Body `message` is exactly `"Invalid username/email or password"`. | [A] |
| AC-14    | Wrong username, any password → HTTP 401. Body `message` is exactly `"Invalid username/email or password"`. Response payload length must be within ±2 bytes of the payload in AC-13. | [A] |
| AC-15    | The returned JWT token, when decoded at https://jwt.io (or library), shows `exp = iat + (expiresIn seconds)` and `iss === app.jwt.issuer` and `sub === String(user.id)`. | [M]/[A] |
| AC-16    | A GET to a sample protected endpoint (e.g., `/api/me` to return current user info, to be added in later phases) with `Authorization: Bearer <valid-token>` → HTTP 200 with the user's info. | [A] |
| AC-17    | Same endpoint with `Authorization: Bearer <garbage>` → HTTP 401. | [A] |
| AC-18    | Same endpoint with `Authorization: Bearer <expired-token>` → HTTP 401. (Test with a short TTL override.) | [A] |
| AC-19    | Same endpoint with NO `Authorization` header → HTTP 401. | [A] |

### 15.4 Logout & Session Cleanup

| ID       | Criterion | Kind |
|----------|-----------|------|
| AC-20    | Clicking **Logout** from the dashboard removes `auth.token` from `localStorage` (DevTools Application tab → Local Storage → empty or key absent). | [M] |
| AC-21    | After logout, navigating to `/dashboard` redirects back to `/login`. | [M] |
| AC-22    | POST `/api/auth/logout` returns HTTP 200 `{ "message": "Logged out successfully" }`. | [A] |

### 15.5 Security Hygiene

| ID       | Criterion | Kind |
|----------|-----------|------|
| AC-23    | Inspect `application.log` for a successful login and a failed login. Neither log line, nor any stack trace in the log file, contains the plaintext password or the BCrypt hash. | [M] |
| AC-24    | `grep` / search the entire source tree for the literal string `"Admin@123"` (default admin password) — it must appear **zero** times outside of `application.properties` as a `${ADMIN_PASSWORD:Admin@123}` default placeholder. | [A] |
| AC-25    | No `.java` file under `src/main/java` uses a `String` literal as a JWT secret, DB password, or admin password; all come through `@ConfigurationProperties` or `@Value`. | [A] |
| AC-26    | `User` entity does NOT have any field named `password`; only `passwordHash`. | [A] |

---

## 16. Out-of-Scope Features

The following items are explicitly **not part of the Login Feature project**. They are listed here to avoid scope creep during intern development. Any of them may become a follow-on training module.

1. **User registration / sign-up page** (including email verification, confirmation tokens).
2. **"Forgot password" / password reset flow** (emailed reset tokens, time-limited one-time links).
3. **Change password / update profile** for authenticated users.
4. **Multi-factor authentication (MFA / 2FA)** — TOTP, SMS, email codes, hardware keys.
5. **"Remember me"** long-lived refresh tokens or persistent cookies.
6. **Refresh token rotation / token blacklist / server-side logout revocation.** (Logout is client-side only per FR-08.)
7. **OAuth2 / social login** (Google, GitHub, Microsoft, SAML SSO, OIDC providers).
8. **Role-Based Access Control (RBAC)** — roles, authorities, permission matrices, admin vs. regular-user endpoints. (The seeded "admin" user is a convenience label only; the login feature does not gate any actions by role.)
9. **Account lockout after N failed attempts** — only WARN logging is required; actual lockout/captcha is a follow-on.
10. **Rate limiting / WAF integration** (may be added at reverse proxy; not in the Spring app itself).
11. **Password complexity rules** beyond length (uppercase / digit / symbol). The `@Size(min=6)` baseline is sufficient for this training feature.
12. **Email/SMS/audit event dispatch** on login success/failure (only SLF4J logging is required).
13. **Impersonation, "switch user", service-to-service authentication.**
14. **User profile pictures, display names, locale/timezone preferences** (entity only stores `id`, `username`, `email`, password hash, timestamps).
15. **Mobile client SDKs or native apps.** The frontend target is the React 19 SPA only.
16. **Database sharding, multi-tenancy, or row-level security.**
17. **Internationalization (i18n) of error messages / UI labels** — English only.
18. **Accessibility (WCAG) compliance testing beyond basic semantic HTML (labels on inputs, button text).** Full screen-reader + contrast audits are out of scope.
19. **Analytics / product metrics tracking (third-party SDKs).**
20. **Containerization (Dockerfiles, docker-compose), Kubernetes manifests, CI pipeline YAML.** The project produces buildable source only; deployment orchestration is handled separately by the training platform.

---

## Final Requirement List (Summary)

The document defines **62 individually identifiable requirements** grouped as follows:

### Functional Requirements (16): FR-01 … FR-16
*(Includes the mandatory FR-01…FR-10 from the project brief plus 6 derived from the brief's validation and endpoint sections.)*

1. FR-01 Username/email identifier field
2. FR-02 Password field
3. FR-03 Mandatory-field validation (client + server)
4. FR-04 Valid-credential authentication (lookup + BCrypt + JWT)
5. FR-05 Invalid-credential rejection (non-specific)
6. FR-06 Structured JSON error responses
7. FR-07 Token issue on success (token + type + expiry + user info)
8. FR-08 Logout (client token removal + `/api/auth/logout` endpoint)
9. FR-09 BCrypt password hashing only
10. FR-10 No password/token in responses or logs
11. FR-11 `POST /api/auth/login` contract
12. FR-12 `POST /api/auth/logout` contract
13. FR-13 Default admin user seeding (env-driven, BCrypt)
14. FR-14 Protected endpoints reject missing/invalid JWT → 401
15. FR-15 Protected endpoints reject JWT whose subject no longer exists → 401
16. FR-16 Conditional email-format validation when identifier contains `@`

### Non-Functional Requirements (13): NFR-01 … NFR-13
Performance, availability, stateless scalability, layering, testability, externalized config, observability, API consistency, Java/React version targets, build gates, dependency hygiene, CORS, sanitization.

### Validation Rules (9): V-01 … V-09
Blanks, size limits, email-format check on `@`, credential ambiguity (enumeration prevention), JWT signature+expiry+subject+issuer validation.

### Success Conditions (8 items in Section 10)
HTTP 200 shape, token shape, downstream protected-request acceptance.

### Failure Conditions (16 scenarios): F-01 … F-15 (plus CORS)
Validation failures map to HTTP 400 (with field errors), auth failures to HTTP 401 (generic), server errors to HTTP 500.

### Assumptions (8): A-1 … A-8
TLS, JWT non-revocability, no self-service sign-up, MySQL reachability, `localStorage` trade-off, single realm, password baseline length, evergreen browsers.

### Constraints (9): C-1 … C-9
Fixed tech stack, no placeholders, no hardcoded secrets, BOM-compatible deps, layer purity, password boundary rule, build gates, no duplicates, contract stability.

### Security Requirements (15): SEC-01 … SEC-15
BCrypt strength ≥10, redacted logging, clean response DTOs, enumeration prevention, HS256-only + 256-bit secret, JWT claim validation, STATELESS sessions, CORS allow-list, CSRF rationale, secret management, length caps, failed-attempt WARN logging, parameterized queries, post-logout storage clean-up, least-privilege DB user.

### Acceptance Criteria (26): AC-01 … AC-26
4 build/deploy, 6 validation, 9 authentication, 3 logout, 5 security hygiene.

### Out-of-Scope (20 explicit exclusions): Section 16
Registration, password reset, MFA, refresh tokens, OAuth2, RBAC, lockout, rate limiting, complexity rules, email dispatch, impersonation, user metadata, mobile clients, sharding, i18n, accessibility audits, analytics, containerization/CI.
