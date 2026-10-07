# Login Feature — Technical Design

**Version:** 1.0
**Date:** 2026-10-07
**Status:** Completed (Phase 5 — Verified & Demo Ready)
**Related Docs:** [Requirement-Specification.md](./Requirement-Specification.md), [API-Documentation.md](./API-Documentation.md), [Test-Case-Execution-Sheet.md](./Test-Case-Execution-Sheet.md)

---

## 1. System Architecture

### 1.1 Overview

The login feature is a two-tier **client-server** architecture over HTTP/1.1 (TLS 1.2+ in production). The backend is a **stateless** Spring Boot monolith that exposes a small REST surface; the frontend is a React 19 SPA. Authentication state is carried exclusively in a signed JWT on each HTTP request — the Spring container keeps **no HTTP session state**.

```
                   ┌───────────────────────────┐
                   │       Browser / SPA       │
                   │  React 19 + Vite + Axios  │
                   │   - localStorage for JWT  │
                   └──────────────┬────────────┘
                                  │
                          HTTPS (TLS 1.2+)
                                  │
          ┌───────────────────────▼────────────────────────┐
          │          Spring Boot 3.x (Java 17)             │
          │                                                │
          │ ┌──────────────┐  ┌────────────────────────┐  │
          │ │ Spring MVC   │  │  Spring Security       │  │
          │ │ Controllers  │  │  Filter Chain:         │  │
          │ │  - AuthCtrl  │  │   · CORS Filter        │  │
          │ │  - HealthCtrl│  │   · JwtAuthFilter      │  │
          │ └──────┬───────┘  │   · ExceptionTranslation│  │
          │        │          └───────────┬────────────┘  │
          │        ▼                      ▼               │
          │ ┌──────────────────────────────────────────┐  │
          │ │  Service Layer                            │  │
          │ │   · AuthService (business rules)          │  │
          │ │   · BCryptPasswordEncoder (delegated)     │  │
          │ │   · JwtTokenProvider (JJWT)               │  │
          │ └─────────────────────┬────────────────────┘  │
          │                       │                       │
          │                       ▼                       │
          │ ┌──────────────────────────────────────────┐  │
          │ │  Spring Data JPA / Hibernate              │  │
          │ │   · UserRepository                        │  │
          │ │   · User entity → users table             │  │
          │ └─────────────────────┬────────────────────┘  │
          └───────────────────────┼───────────────────────┘
                                  │
                          HikariCP / JDBC
                                  │
                                  ▼
                   ┌───────────────────────────┐
                   │   MySQL 8.0 — login_      │
                   │   feature_db              │
                   │   Table: users            │
                   └───────────────────────────┘
```

### 1.2 Architectural Principles Applied

| Principle | How |
|---|---|
| **Layered architecture** (NFR-04) | Controllers → Services → Repositories (inward-pointing arrows only; controllers never touch repositories directly) |
| **Clean / Hexagonal (DIP)** | DTOs cross the HTTP boundary; entities never leave the data layer; Spring `PasswordEncoder` and `UserDetailsService` are invoked via interfaces |
| **Stateless (NFR-03)** | No `JSESSIONID`, `SessionCreationPolicy=STATELESS`, JWT in `Authorization` header |
| **Defense in depth** | Client + server validation; BCrypt; JWT signature + claim validation; Global exception handler that never leaks stack traces to clients |
| **Configuration over code** | All secrets, ports, TTLs, URLs come from env vars via `AppProperties` |

---

## 2. Backend Package Structure

All backend Java code lives under the single root package `com.loginfeature` (matches the Maven `<groupId>.<artifactId>` family and the `@SpringBootApplication` component-scan base).

```
backend/src/main/java/com/loginfeature/
├── LoginFeatureApplication.java          -- Entry point, @SpringBootApplication

├── config/
│   ├── AppProperties.java                -- @ConfigurationProperties("app")
│   │                                      -- .getJwt(), .getAdmin() nested classes
│   └── SecurityConfig.java               -- WebSecurityCustomizer,
│                                           -- SecurityFilterChain, BCrypt bean,
│                                           -- CorsConfigurationSource bean

├── controller/
│   ├── AuthController.java               -- POST /api/auth/login + /logout
│   └── HealthController.java             -- GET /api/health

├── dto/
│   ├── LoginRequest.java                 -- inbound (Bean Validated)
│   ├── LoginResponse.java                -- 200 OK success envelope + UserInfo nested
│   ├── LogoutResponse.java               -- 200 OK simple message (for /logout)
│   ├── HealthResponse.java               -- 200 OK for /api/health
│   └── ApiErrorResponse.java             -- 4xx/5xx envelope + FieldError[] list

├── entity/
│   └── User.java                         -- JPA @Entity @Table(name="users")
│                                           -- fields: id, username, email,
│                                           -- passwordHash, enabled, createdAt, updatedAt

├── exception/
│   ├── InvalidCredentialsException.java  -- thrown by AuthService
│   ├── AccountDisabledException.java     -- NEW for the `enabled` flag
│   └── GlobalExceptionHandler.java       -- @RestControllerAdvice
│                                           -- handles:
│                                           --   MethodArgumentNotValid (400)
│                                           --   InvalidCredentials /
│                                           --     BadCredentials (401)
│                                           --   AccountDisabled (401 generic)
│                                           --   AccessDenied (403)
│                                           --   Exception (500 safe msg)

├── repository/
│   └── UserRepository.java               -- JpaRepository<User, Long>
│                                           -- findByUsername(String)
│                                           -- findByEmail(String)
│                                           -- findByUsernameOrEmail(String, String)
│                                           -- existsByUsername, existsByEmail

├── security/
│   ├── CustomUserDetailsService.java     -- implements UserDetailsService
│                                           -- loadUserByUsername → queries
│                                           -- findByUsernameOrEmail, checks
│                                           -- enabled flag → UserDetails
│   ├── JwtTokenProvider.java             -- JJWT util:
│                                           -- generateToken(userId,username,email)
│                                           -- validateToken(String) → Claims/throw
│                                           -- extractSubject/Issuer/Expiration
│   └── JwtAuthenticationFilter.java      -- OncePerRequestFilter
│                                           -- reads Authorization: Bearer …
│                                           -- calls JwtTokenProvider
│                                           -- populates SecurityContextHolder
│                                           -- with UsernamePasswordAuthenticationToken

└── service/
    ├── AuthService.java                  -- authenticate(LoginRequest): LoginResponse
    │                                      -- logout(HttpServletRequest): void
    └── DataInitializer.java              -- CommandLineRunner
                                            -- if users.count()==0 → seed 1 default
                                            -- admin user (env creds, BCrypt hash)
```

### 2.1 Layer Dependency Rules

- `controller` → may import from: `dto`, `service`, `config` (properties only). **May NOT** import `entity` or `repository`.
- `service` → may import from: `dto`, `entity`, `repository`, `security` (JwtTokenProvider), `exception`, `org.springframework.security.crypto.password.PasswordEncoder`.
- `security` → may import from: `entity`, `repository`, `config` (AppProperties).
- `entity` / `repository` → never depend on any layer above them.
- `dto` → zero Spring dependencies (except `jakarta.validation.*`); pure POJOs + getters/setters.

---

## 3. Frontend Structure

Frontend is a React 19 SPA built with Vite 5+ using **JavaScript** (not TypeScript — per stack requirement). State is kept lean; JWT is stored in `localStorage`. Axios interceptors handle the auth header and 401 auto-logout.

```
frontend/
├── package.json                          -- deps: react@19, react-dom@19,
│                                           react-router-dom@latest,
│                                           axios@latest, vite@latest,
│                                           @vitejs/plugin-react@latest
├── vite.config.js                        -- server.port=5173,
│                                           proxy /api → http://localhost:8080
├── .env.example                          -- VITE_API_BASE_URL=http://localhost:8080
│
├── index.html                            -- <div id="root"></div>
└── src/
    ├── main.jsx                          -- React 19 createRoot() → <App/>
    ├── App.jsx                           -- BrowserRouter + routes
    │
    ├── api/
    │   └── axiosInstance.js              -- axios.create()
    │                                      -- req interceptor: +Bearer from localStorage
    │                                      -- res interceptor: on 401 → logout()
    │
    ├── auth/
    │   ├── authStorage.js                -- tokenKey="auth.token", userKey="auth.user"
    │   │                                  -- save/clear/get, read on app boot
    │   └── authActions.js                -- login(usernameOrEmail, password), logout()
    │                                      -- calls axios, saves/clears, returns Promise
    │
    ├── components/
    │   ├── LoginForm.jsx                 -- controlled fields, onSubmit → authActions.login
    │   │                                  -- displays inline per-field errors from both
    │   │                                  --  client validation AND 400 fieldErrors[]
    │   ├── LogoutButton.jsx              -- onClick → authActions.logout → navigate("/login")
    │   └── Navbar.jsx                    -- shows username + LogoutButton if logged in
    │
    ├── pages/
    │   ├── LoginPage.jsx                 -- renders <LoginForm/>
    │   └── DashboardPage.jsx             -- protected; shows basic welcome message
    │
    ├── router/
    │   └── PrivateRoute.jsx              -- wrapper: !token → <Navigate to="/login"/>
    │
    ├── validation/
    │   └── loginSchema.js                -- pure JS validate({usernameOrEmail,password})
    │                                      -- mirrors backend rules V-01…V-06
    │                                      -- returns {field:msg} errors object or {}
    │
    └── styles/
        └── global.css                    -- minimal responsive styling for training
```

### 3.1 Frontend Key Design Decisions

1. **JWT storage:** `localStorage` (per Assumption A-5). This is the simplest option for a training demo. Interns are taught the alternative (`HttpOnly`, `Secure` cookie via Spring Session) as a production upgrade path.
2. **Routing:** `/login` (public), `/dashboard` (protected via `PrivateRoute`), fallback `/` → redirects to `/dashboard` when authenticated, else `/login`.
3. **Validation (client):** runs first before Axios call; server 400 errors are merged into the same form-error state so UX is uniform.
4. **Unauthorized auto-logout:** Axios response interceptor treats any 401 from any call as "session gone" and flushes storage + redirects to `/login`.

---

## 4. Database Design

Full DDL lives in `database/schema.sql`. This section restates the logical design for quick reference.

### 4.1 `users` Table

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | PK, `AUTO_INCREMENT` | Surrogate key. |
| `username` | `VARCHAR(50)` | `NOT NULL`, `UNIQUE(uk_users_username)` | Human-readable login handle. Case-sensitive by default (collation-dependent). |
| `email` | `VARCHAR(100)` | `NOT NULL`, `UNIQUE(uk_users_email)` | RFC 5322 email; alternate login identifier. |
| `password_hash` | `VARCHAR(255)` | `NOT NULL` | BCrypt hash only (prefix `$2a$` / `$2b$` / `$2y$`). Note: the column is **named `password_hash` not `password`** per SEC-02/AC-26 to prevent accidental leaks. |
| `enabled` | `TINYINT(1)` | `NOT NULL DEFAULT 1` | Auth gate: only rows with `enabled=1` may complete the login flow. A disabled account returns the same generic 401 as bad credentials (via `AccountDisabledException`) to avoid account enumeration. |
| `created_at` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Auto-set on insert. |
| `updated_at` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | Auto-set on insert + any UPDATE. |

Engine: `InnoDB`, Charset: `utf8mb4`, Collation: `utf8mb4_unicode_ci`.

### 4.2 Indexing

- `PRIMARY (id)` — B-Tree (auto).
- `uk_users_username (username)` — UNIQUE B-Tree.
- `uk_users_email (email)` — UNIQUE B-Tree.
- No additional indexes are required for the login feature. The two UNIQUE indexes cover both lookup paths used by `findByUsernameOrEmail`.

### 4.3 Mapping to JPA Entity

Entity `User.java` will map:
- `enabled` → `private Boolean enabled;` with `@Column(columnDefinition = "TINYINT(1)")` if needed, otherwise Hibernate auto-translates `Boolean` <-> `TINYINT(1)` on MySQL8 dialect.
- Timestamps → `LocalDateTime createdAt / updatedAt` + `@PrePersist / @PreUpdate` callbacks keep values in sync with schema defaults.

---

## 5. API Contract

Three public HTTP endpoints are shipped in Phase 2:

| Method | Path | Auth Required? | Purpose |
|---|---|---|---|
| `GET` | `/api/health` | No | Liveness / readiness check for the JVM. Returns JSON `{ status, timestamp }`. |
| `POST` | `/api/auth/login` | No | Primary login endpoint. Validates credentials, returns JWT. |
| `POST` | `/api/auth/logout` | Optional (Authorization header accepted but not strictly required) | Audits logout on the server and returns a success message. Actual invalidation is client-side token removal. |

Additionally, any future protected endpoint (e.g., `GET /api/me`) must accept `Authorization: Bearer <token>` and reject without it (401).

### 5.1 General API Rules

- Content-Type: requests and responses use `application/json;charset=UTF-8`.
- Dates in JSON: ISO-8601 UTC strings with millis (Jackson default when `jackson-datatype-jsr310` is on classpath via `spring-boot-starter-web`).
- CORS: `Access-Control-Allow-Origin: http://localhost:5173` in dev; production allow-list via env variable.
- Every non-2xx response body is exactly the `ApiErrorResponse` envelope (see Section 8 / API-Documentation.md).

---

## 6. Request JSON

### 6.1 `POST /api/auth/login`

| Field | Type | Required | Rules |
|---|---|---|---|
| `usernameOrEmail` | `string` | Yes | Not blank. Max 100 chars. If it contains `@` it must be a syntactically valid email. |
| `password` | `string` | Yes | Not blank. Min 6 chars. Max 128 chars. |

**Example (valid):**
```json
{
  "usernameOrEmail": "user@example.com",
  "password": "Password123"
}
```

**Alternative (username, not email — both forms must work):**
```json
{
  "usernameOrEmail": "admin",
  "password": "Admin@123"
}
```

### 6.2 `POST /api/auth/logout`

**Request body:** empty JSON object `{}` is permitted but **not required**. A body-less `POST` is also valid. The server does not read any fields from the logout request body (server-side invalidation is out of scope — Section 10).

### 6.3 `GET /api/health`

No request body. No query parameters.

---

## 7. Response JSON

### 7.1 `POST /api/auth/login` — HTTP 200 OK

Per the Phase 2 brief the success envelope contains top-level `success`, `message`, `token`, `tokenType`, plus a `user` object for the frontend.

| Field | Type | Always present? | Notes |
|---|---|---|---|
| `success` | `boolean` | Yes | Always `true` on 200. Makes the envelope symmetric with error flows. |
| `message` | `string` | Yes | Human-friendly success summary. Example: `"Login successful"`. |
| `token` | `string` | Yes | 3-segment base64url JWT signed with HS256. |
| `tokenType` | `string` | Yes | Always `"Bearer"` (case-sensitive header scheme). |
| `expiresIn` | `number (integer)` | Yes | Seconds until the JWT expires. Equals `app.jwt.expiration-ms / 1000`. Frontend can pre-emptively log out just before this. |
| `user` | `object` | Yes | Non-null user info object. MUST NOT include password or password hash. |
| `user.id` | `number (integer)` | Yes | `users.id` primary key. |
| `user.username` | `string` | Yes | `users.username` as stored. |
| `user.email` | `string` | Yes | `users.email` as stored. |

**Full Example:**
```json
{
  "success": true,
  "message": "Login successful",
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJsb2dpbi1mZWF0dXJlLWJhY2tlbmQiLCJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsImVtYWlsIjoiYWRtaW5AZXhhbXBsZS5jb20iLCJpYXQiOjE3NjAwMDAwMDAsImV4cCI6MTc2MDA4NjQwMH0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "admin",
    "email": "admin@example.com"
  }
}
```

**JWT Claims (inside `token`):**

| Claim | Type | Meaning |
|---|---|---|
| `iss` | string | `app.jwt.issuer` (default `"login-feature-backend"`) — verified on every read. |
| `sub` | string | Stringified `User.id` (e.g. `"1"`). |
| `iat` | number (Unix seconds) | Token issued-at time. |
| `exp` | number (Unix seconds) | Token expires (`iat + app.jwt.expiration-ms/1000`). |
| `username` | string | Username of the subject (convenience claim — NOT used for trust; `sub` is the source of truth). |
| `email` | string | Email of the subject (convenience claim). |

### 7.2 `POST /api/auth/logout` — HTTP 200 OK

```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

### 7.3 `GET /api/health` — HTTP 200 OK

```json
{
  "status": "UP",
  "timestamp": "2026-10-07T10:15:30.123456Z"
}
```

This is intentionally **not** Spring Actuator — the project's stack explicitly excludes Actuator per the "no unnecessary dependencies" rule. The health endpoint is a 5-line custom controller that simply returns the current instant.

---

## 8. Error Response Structure

All 4xx/5xx responses share one envelope shape:

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

| Field | Type | Present |
|---|---|---|
| `timestamp` | ISO-8601 UTC string | Always |
| `status` | integer | Always; equals the HTTP status code |
| `error` | string | Always; equals `HttpStatus.valueOf(status).getReasonPhrase()` |
| `message` | string | Always; safe, user-facing message in English |
| `path` | string | Always; equals `request.getRequestURI()` |
| `fieldErrors` | array of `{ field, message }` | **Only on 400 validation failures.** Omitted / null otherwise (Jackson `NON_NULL`). |

### 8.1 Mapping of Error Scenario → HTTP Code + `message`

| Scenario | HTTP | `message` | `fieldErrors` present? |
|---|---|---|---|
| Missing or blank `usernameOrEmail` | 400 | `"Validation failed"` | YES (1 row: field=`usernameOrEmail`) |
| Missing or blank `password` | 400 | `"Validation failed"` | YES |
| Password < 6 or > 128 chars | 400 | `"Validation failed"` | YES |
| Value contains `@` but fails email regex | 400 | `"Validation failed"` | YES (field=`usernameOrEmail`) |
| Unknown identifier OR wrong password OR disabled account | 401 | **Exact same string:** `"Invalid username/email or password"` | NO (prevents enumeration) |
| Malformed JWT / bad signature / expired JWT on protected endpoint | 401 | `"Invalid or expired token"` | NO |
| Valid token but missing required authority (for future RBAC) | 403 | `"Access denied"` | NO |
| DB down / NullPointerException / any uncaught Throwable | 500 | `"An unexpected error occurred. Please try again later."` | NO |
| CORS preflight disallowed | 403 (browser) | N/A (handled at browser / CORS filter) | — |

---

## 9. Authentication Flow

End-to-end happy path, numbered so QA can match it to AC-11.

1. User fills `<LoginForm>` and submits.
2. `validation/loginSchema.js` runs client-side validations:
   - If `errors` object is non-empty → render inline errors; **goto 1**; no HTTP request.
3. `auth/authActions.js` calls `axios.post("/api/auth/login", { usernameOrEmail, password })` → request dispatched to backend.
4. Spring Servlet receives the POST:
   - CORS filter allows origin (dev: `localhost:5173`).
   - `CsrfFilter` skipped because CSRF is disabled in `SecurityConfig` (per SEC-09, stateless JWT).
   - `JwtAuthenticationFilter` runs but **skips setting context** because `/api/auth/login` is in `permitAll`.
   - ExceptionTranslationFilter registered further down the chain.
5. Spring MVC routes to `AuthController.login(@RequestBody @Valid LoginRequest req)`.
6. `@Valid` triggers Hibernate Validator:
   - If invalid → `MethodArgumentNotValidException` → `GlobalExceptionHandler` → **HTTP 400** response per Section 8.1. Frontend merges `fieldErrors[]` into form errors; goto 1.
7. Controller delegates to `AuthService.authenticate(req)`.
8. `AuthService` performs steps in order:
   - (a) Optional semantic validation (if value contains `@` → run `jakarta.validation.Email` check manually; throw `MethodArgumentNotValidException` equivalent **400** if malformed — mirrors FR-16/V-03).
   - (b) `UserRepository.findByUsernameOrEmail(req.getUsernameOrEmail(), req.getUsernameOrEmail())` → `Optional<User>`.
   - (c) If `Optional.isEmpty()` → short-circuit with `InvalidCredentialsException`; we still call `passwordEncoder.matches("dummy", "$2a$…")` once to keep timing roughly uniform with step (e) (mitigates V-08 timing leak).
   - (d) If user found: check `Boolean.TRUE.equals(user.getEnabled())`. If `false` → throw `AccountDisabledException` → caught by same handler → generic 401 body (same text as step c/e).
   - (e) If enabled: `passwordEncoder.matches(req.getPassword(), user.getPasswordHash())`. If `false` → throw `InvalidCredentialsException` → **HTTP 401**.
   - (f) If BCrypt passes → `JwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getEmail())` → `String token`.
   - (g) Build `LoginResponse.UserInfo(id, username, email)` → build full `LoginResponse(token, "Bearer", TTL_SECONDS, userInfo)`.
9. Controller wraps into `ResponseEntity<LoginResponse>` with HTTP **200 OK** and returns.
10. `authActions.js` resolves the promise:
    - Saves `token` to `localStorage["auth.token"]`
    - Saves `user` to `localStorage["auth.user"]`
    - Notifies app state (React Router)
11. Router navigates to `/dashboard`; `PrivateRoute` allows passage because token is now present.

---

## 10. Logout Flow

Per Requirement-Spec OoS-6 — this feature intentionally uses **client-side-only revocation** because token blacklists are out of scope. The server endpoint exists for:
* Audit logging (logout timestamp per user)
* Frontend UX symmetry (matching `POST` with the login `POST`)
* Future extensibility (OoS-6 can be added later without breaking the frontend contract)

### 10.1 Server Side (`POST /api/auth/logout`)

1. Controller receives request.
2. If an `Authorization: Bearer <token>` header is present:
   - Filter will already have populated the `SecurityContext` if the token is valid.
   - Service logs `LOGOUT user=…` at INFO level.
3. If no token (user already flushed token before calling logout):
   - Service logs `LOGOUT anonymous` at DEBUG level — no error.
4. Service returns immediately with HTTP 200 + `LogoutResponse(message="Logged out successfully")`.
5. No DB write occurs; no token is blacklisted.

### 10.2 Client Side

1. User clicks `<LogoutButton/>` in Navbar.
2. `authActions.logout()` fires:
   - (a) Fire-and-forget `axios.post("/api/auth/logout", {})` (ignore network errors here; worst case the server never logs it, but we still clear storage).
   - (b) `localStorage.removeItem("auth.token")`
   - (c) `localStorage.removeItem("auth.user")`
   - (d) Clear in-memory auth state if any.
3. `navigate("/login", { replace: true })`.
4. Any in-flight protected endpoint calls that had a valid token will still succeed (this is acceptable; future RBAC phase would address this by shrinking TTL + refresh tokens).
5. Re-opening the browser tab and navigating to `/dashboard` will redirect back to `/login` because `PrivateRoute` checks `localStorage` on every render.

---

## 11. Validation Flow

Validation is **redundant** across two tiers. Errors surface in the same form-error UI slot to keep UX consistent.

```
         ┌──────────────────────────────────────┐
         │ User clicks Login with credentials   │
         └──────────────────┬───────────────────┘
                            │
                            ▼
        ┌────────────────────────────────────┐   NO
        │ loginSchema.validate()             ├───► render inline errors, stop
        │ (frontend pure-JS rules V01-V06)   │
        └──────────────────┬─────────────────┘
                           │ YES
                           ▼
        ┌────────────────────────────────────┐
        │ Axios POST /api/auth/login         │
        └──────────────────┬─────────────────┘
                           │
                           ▼
        ┌────────────────────────────────────┐   NO
        │ @Valid on LoginRequest             ├───► 400 fieldErrors[] → merged into
        │ (jakarta Bean Validation V01-V06)  │       same form-error state, stop
        └──────────────────┬─────────────────┘
                           │ YES
                           ▼
        ┌────────────────────────────────────┐   NO
        │ AuthService semantic validation    ├───► throws wrapped 400 (email
        │ (FR-16: if '@' → email format)     │       malformed on '@' values)
        └──────────────────┬─────────────────┘
                           │ YES
                           ▼
                    (continue to auth —
                     Section 9 step 8b)
```

### 11.1 Validation Rules Restatement (from Requirement Spec)

Both client and server apply **identical rules** for fields:

| Field | Rule | HTTP 400 `field` key |
|---|---|---|
| `usernameOrEmail` | Not blank | `usernameOrEmail` |
| `usernameOrEmail` | ≤ 100 chars | `usernameOrEmail` |
| `usernameOrEmail` | If contains `@` → well-formed email | `usernameOrEmail` |
| `password` | Not blank | `password` |
| `password` | ≥ 6 chars | `password` |
| `password` | ≤ 128 chars | `password` |

---

## 12. Exception Handling Strategy

A single `@RestControllerAdvice` class (`GlobalExceptionHandler`) is the single sink for all exceptions thrown from controller, service, or filter layers. Security filter exceptions are bridged into the same envelope via `AuthenticationEntryPoint` and `AccessDeniedHandler` beans registered in `SecurityConfig`.

```
Exception type                         Handled by                         HTTP    JSON envelope
────────────────────────────────────── ──────────────────────────────── ────── ──────────────
MethodArgumentNotValidException        GlobalExceptionHandler           400     +fieldErrors[]
BindException / ConversionException    GlobalExceptionHandler (400)     400     +fieldErrors[] / msg
InvalidCredentialsException            GlobalExceptionHandler           401     message = "Invalid username/email or password"
BadCredentialsException (Spring Sec)   GlobalExceptionHandler           401     (same msg as above)
AccountDisabledException               GlobalExceptionHandler           401     (same msg — enumeration safe)
ExpiredJwtException /                  JwtAuthenticationFilter →        401     message = "Invalid or
MalformedJwtException /                AuthenticationEntryPoint                 expired token"
SignatureException /
UnsupportedJwtException
AccessDeniedException (403)            GlobalExceptionHandler           403     message = "Access denied"
UsernameNotFoundFromSubPostValidation  JwtAuthenticationFilter          401     message = "Invalid or expired token"
HttpRequestMethodNotSupportedException Spring default → advice 405      405     same envelope shape
NoHandlerFoundException                Spring default → advice 404      404     same envelope shape
Any other Throwable (Runtime)          GlobalExceptionHandler           500     message = "An unexpected error occurred. Please try again later."
                                                                            └── stack trace logged server-side ONLY
```

### 12.1 Security Filter → JSON Envelope Bridge

Because JWT exceptions originate **before** the DispatcherServlet (in a servlet filter), `@ExceptionHandler` methods never see them. To keep error bodies consistent we declare two Spring Security beans:

1. `AuthenticationEntryPoint` bean — invoked when a request is rejected due to missing/invalid credentials. Its `commence()` method writes a JSON `ApiErrorResponse` with HTTP 401 directly to `HttpServletResponse.getWriter()` via an `ObjectMapper`.
2. `AccessDeniedHandler` bean — invoked when credentials are valid but authority is missing (future RBAC). Writes JSON `ApiErrorResponse` with HTTP 403.

Both beans reuse the same `ApiErrorResponse` class so the frontend sees exactly one shape for every failure path (Section 8).

---

## 13. Security Design

Summarized here; full set of controls in Requirement-Specification §14 (SEC-01…SEC-15). Phase-2–specific design choices:

### 13.1 Spring Security Filter Chain Order

```
1. DisableEncodeUrlFilter
2. WebAsyncManagerIntegrationFilter
3. SecurityContextHolderFilter (MODE_THREADLOCAL, cleared at end)
4. HeaderWriterFilter (X-Content-Type-Options, X-Frame-Options=DENY, etc.)
5. CorsFilter (explicit allow-list, NO global wildcard credentials)
       └─ CSRF is DISABLED entirely (SEC-09 rationale: JWT in Authorization header,
          no session cookie; CSRF requires token-bearing cookie to be relevant).
6. JwtAuthenticationFilter ←———————— OUR CUSTOM FILTER (OncePerRequestFilter)
       └─ doFilterInternal:
            - if no Authorization: Bearer → chain.doFilter(req, resp)
            - else parse JwtTokenProvider.validateToken → Claims
            - load UserDetails via CustomUserDetailsService (checks enabled + exists)
            - wrap in UsernamePasswordAuthenticationToken
            - attach to SecurityContextHolder.getContext().setAuthentication(auth)
7. UsernamePasswordAuthenticationFilter (never used; overridden by Jwt flow)
8. DefaultLoginPageGeneratingFilter — disabled via formLogin.disable()
9. BasicAuthenticationFilter — disabled via httpBasic.disable()
10. RequestCacheAwareFilter
11. SecurityContextHolderAwareRequestFilter
12. AnonymousAuthenticationFilter
13. SessionManagementFilter — SessionCreationPolicy.STATELESS → bypass
14. ExceptionTranslationFilter — uses our custom AuthenticationEntryPoint + AccessDeniedHandler
15. FilterSecurityInterceptor — matchers:
        /api/health      → permitAll
        /api/auth/**     → permitAll
        any other request → authenticated
```

### 13.2 BCrypt Encoding

- Single bean: `@Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }`
- Strength `10` (SEC-01 minimum). Raising to 12 only requires changing the bean constructor arg; existing hashes continue to verify via `PasswordEncoder.upgradeEncoding(…)` pattern.
- `DataInitializer` ALWAYS calls `passwordEncoder.encode(adminPassword)` BEFORE saving. NEVER store the raw string.
- `User` entity field name: `passwordHash` (never `password`) per AC-26.

### 13.3 JWT Design (HS256 only — SEC-05)

- `JwtTokenProvider` wraps JJWT 0.12.6 API.
- Secret sourced exclusively from `AppProperties.getJwt().getSecret()` → which in turn comes from `${JWT_SECRET:…}` env placeholder.
- Minimum 256-bit (32 byte) secret length. On startup `DataInitializer` (or `JwtTokenProvider` `@PostConstruct`) asserts length ≥ 32 and fails app startup **fast** if the secret is too short.
- Parser configuration explicitly sets: `.requireIssuer(issuer)`, builds `JwtParser` via JJWT's modern builder, and `build()` will reject `alg=none` automatically (JJWT 0.12.x default — no legacy unsecured parser).
- Tokens carry 6 claims (Section 7.1 table). No private claim contains a password.
- TTL: `app.jwt.expiration-ms` (default `86400000` ms = 24 hours). For test scenarios (AC-18 expired token) we can override with a `JWT_EXPIRATION_MS=5000` env var to create an immediately expiring token.

### 13.4 The `enabled` Flag

- New entity field this phase (brief mandate).
- Validation semantics: **A disabled account MUST NOT authenticate, but it also MUST NOT reveal itself as disabled** (account enumeration vector).
- Therefore `AccountDisabledException` caught by `GlobalExceptionHandler` → **exact same** HTTP 401 body as `InvalidCredentialsException`:
  ```json
  {"timestamp":…,"status":401,"error":"Unauthorized","message":"Invalid username/email or password","path":"/api/auth/login"}
  ```
- The log on the server-side may record "disabled account attempted login" at WARN (SEC-12 baseline).

### 13.5 Logging and Secrets

- Lombok (if used) or hand-authored SLF4J loggers. **Never** do `log.info("login: user={}, pw={}", req.getUsernameOrEmail(), req.getPassword())`. The project's code review checklist bans any log statement containing the substring `password` except explicit `passwordEncoder` invocations.
- If request-body tracing is added (`CommonsRequestLoggingFilter`), a custom wrapper must redact any JSON key named `password`/`passwordHash`/`token` before emitting to the log.

---

## 14. Configuration Design

All configuration is externalized. The file `backend/src/main/resources/application.properties` is the only on-disk source; every sensitive or environment-specific key uses `${ENV_VAR:dev-default}` placeholder syntax.

### 14.1 Key Categories

| Prefix | Source | Design |
|---|---|---|
| `server.*` | Spring Boot built-in | `server.port=${SERVER_PORT:8080}` |
| `spring.datasource.*` | Spring Boot built-in | URL, UN, PW — all env overridden |
| `spring.jpa.*` | Spring Boot built-in | `ddl-auto=${JPA_DDL_AUTO:update}`, MySQL dialect, show SQL dev-only |
| `spring.jackson.*` | Spring Boot built-in | `default-property-inclusion=non_null` (omits `fieldErrors` when empty) |
| `app.jwt.*` | `AppProperties.Jwt` | `secret`, `expiration-ms`, `issuer` — all env-var driven |
| `app.admin.*` | `AppProperties.Admin` | `username`, `email`, `password` — seed values, must be redefined in prod |
| `app.cors.*` (NEW this phase) | `AppProperties.Cors` (nested class) | comma-separated `allowed-origins` (default `http://localhost:5173`). `SecurityConfig` reads them |
| `logging.level.*` | Spring Boot built-in | Root level INFO, `com.loginfeature` DEBUG in dev |

### 14.2 `AppProperties` Shape

```
AppProperties (@ConfigurationProperties(prefix="app"))
├── Jwt
│   ├── secret: String          (from JWT_SECRET)
│   ├── expirationMs: long      (from JWT_EXPIRATION_MS)
│   └── issuer: String          (from JWT_ISSUER)
├── Admin
│   ├── username: String        (from ADMIN_USERNAME)
│   ├── email: String           (from ADMIN_EMAIL)
│   └── password: String        (from ADMIN_PASSWORD)
└── Cors (NEW)
    └── allowedOrigins: List<String>   (from CORS_ALLOWED_ORIGINS, comma-split)
```

### 14.3 Secrets Discipline

- `application.properties` **never** contains a real production secret.
- A `.gitignore` must include:
  - `*.env`, `*.p12`, `*.jks`
  - IDE secrets dirs
- A template file `.env.example` should be provided (not committed with real values) listing keys only:
  ```
  SERVER_PORT=8080
  DATABASE_URL=jdbc:mysql://localhost:3306/login_feature_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
  DATABASE_USERNAME=root
  DATABASE_PASSWORD=change_me
  JWT_SECRET=please-change-me-to-a-64-char-random-hex
  JWT_EXPIRATION_MS=86400000
  ADMIN_USERNAME=admin
  ADMIN_EMAIL=admin@example.com
  ADMIN_PASSWORD=change_me
  CORS_ALLOWED_ORIGINS=http://localhost:5173
  ```

---

## 15. Sequence Diagram — Login

Text-based Mermaid-style sequence for quick reference. Interns can paste this into mermaid.live to render.

```mermaid
sequenceDiagram
    actor User
    participant UI as React LoginForm
    participant AX as Axios/API
    participant FLT as JwtAuthenticationFilter
    participant CTR as AuthController
    participant SVC as AuthService
    participant PE as BCryptPasswordEncoder
    participant REPO as UserRepository
    participant DB as MySQL users table
    participant JWT as JwtTokenProvider

    User->>UI: Fill form + Submit
    UI->>UI: validateClientSide()
    alt invalid (client)
        UI-->>User: Render inline errors
    else valid
        UI->>AX: POST /api/auth/login { uoe, pw }
        AX->>FLT: HTTP request
        FLT->>FLT: permitAll (/api/auth/**) → skip setContext
        FLT->>CTR: continue chain
        CTR->>CTR: @Valid LoginRequest
        alt invalid (BeanValidation)
            CTR-->>AX: 400 fieldErrors[]
            AX-->>UI: reject(err)
            UI-->>User: Merge + render errors
        else valid
            CTR->>SVC: authenticate(req)
            SVC->>SVC: semantic validate (FR-16 '@' email)
            alt email malformed
                SVC-->>CTR: throw → 400
                CTR-->>AX: 400
            else
                SVC->>REPO: findByUsernameOrEmail(uoe, uoe)
                REPO->>DB: SELECT * FROM users WHERE username=? OR email=?
                DB-->>REPO: row (or empty)
                REPO-->>SVC: Optional<User>
                alt user not found
                    SVC->>PE: matches(dummy, knownHash) [timing pad]
                    SVC-->>CTR: throw InvalidCredentialsException
                    CTR-->>AX: 401 generic
                else user found
                    SVC->>SVC: check user.enabled == true
                    alt disabled
                        SVC-->>CTR: throw AccountDisabledException
                        CTR-->>AX: 401 generic
                    else enabled
                        SVC->>PE: matches(rawPw, user.passwordHash)
                        alt bcrypt mismatch
                            SVC-->>CTR: throw InvalidCredentialsException
                            CTR-->>AX: 401 generic
                        else bcrypt OK
                            SVC->>JWT: generateToken(id, un, email)
                            JWT-->>SVC: String token
                            SVC-->>CTR: LoginResponse(token, Bearer, ttl, userInfo)
                            CTR-->>AX: 200 OK JSON
                            AX-->>UI: resolve(response)
                            UI->>UI: save token + user to localStorage
                            UI->>User: navigate("/dashboard")
                        end
                    end
                end
            end
        end
    end
```

---

## 16. Login Flow Diagram (High-Level State Transition)

Top-level state-chart view that complements the sequence.

```mermaid
stateDiagram-v2
    [*] --> LoginScreen
    LoginScreen --> ClientValidation: User clicks Login
    ClientValidation --> LoginScreen: Inline errors (FAIL)
    ClientValidation --> ServerValidation: POST /api/auth/login

    ServerValidation --> LoginScreen: HTTP 400 (fieldErrors → inline)
    ServerValidation --> AccountLookup: 202 dispatch

    AccountLookup --> CredentialCheck: user found + enabled
    AccountLookup --> Generic401: missing or disabled

    CredentialCheck --> Generic401: BCrypt fails
    CredentialCheck --> IssueToken: BCrypt matches

    Generic401 --> LoginScreen: "Invalid username/email or password"

    IssueToken --> StoreJwt: HTTP 200 + token
    StoreJwt --> Dashboard: 200 + localStorage save

    Dashboard --> LogoutInitiated: User clicks Logout
    LogoutInitiated --> ClearStorage: rm auth.token + auth.user
    ClearStorage --> LogoutEndpoint: fire-and-forget POST /api/auth/logout
    LogoutEndpoint --> LoginScreen: navigate("/login")
    Dashboard --> LoginScreen: 401 from any API (interceptor)
```

---

## Inconsistency Check (Pre-Submit)

Before signing off we explicitly verify each Phase-2 brief mandate maps to exactly one design element and that no two elements disagree:

| Brief mandate → Design element | Consistency notes |
|---|---|
| `users.id` → `schema.sql BIGINT PK AUTO_INCREMENT` + entity `Long id` | ✅ Match |
| `users.username (unique)` → `uk_users_username` UNIQUE, DTO loginRequest max 50 stored; max 100 accepted (extra 50 chars allowed on input, DB truncation not possible because Bean Validation checks are before entity flush) | ✅ Match |
| `users.email (unique)` → `uk_users_email` UNIQUE, max 100 input length, FR-16 semantic validation | ✅ Match |
| `users.password` → brief says field name is `password`; **design renamed to `password_hash`** per SEC-01/FR-09. Brief mandate is semantic ("store BCrypt") — the safer column name is a **design improvement** over the literal schema brief. Cross-checked with Requirement-Specification SEC-02/AC-26 which require the entity NOT have a `password` field. | ✅ Justified rename |
| `users.enabled` → column `TINYINT(1) DEFAULT 1`; entity `Boolean enabled`; `AccountDisabledException` → generic 401 body (safe enumeration) | ✅ Match + added security |
| `created_at / updated_at` → schema defaults + JPA PrePersist/PreUpdate double-write | ✅ Match (JPA overrides are idempotent with DB defaults) |
| `POST /api/auth/login` request `{usernameOrEmail, password}` → Section 6, exactly matches brief JSON sample | ✅ Match |
| Success response: `success`, `message`, `token`, `tokenType`, `user` → Section 7.1 table lists all five plus extra convenience `expiresIn`. Requirement spec FR-07 requires expiry seconds so the extra field is justified. | ✅ Match + AC compatible |
| Do NOT return password → `LoginResponse.UserInfo` has only `id,username,email`; jackson NON_NULL; entity never serialized | ✅ Match |
| Logout POST + frontend client invalidation → Section 10, server-side blacklist explicitly OoS | ✅ Match |
| `GET /api/health` → Section 7.3, lightweight custom controller, Actuator excluded per "no unnecessary dependencies" | ✅ Match |
| Standard HTTP status codes → Section 8.1: 200 / 400 / 401 / 403 / 500 exactly | ✅ Match |
| Requirement-Specification FR-16 (email-format when identifier contains `@`) → Section 9 step 8a "semantic validation" service-layer check | ✅ Match |
| Section 7.1 success envelope (`success: true`) was not in Requirement-Spec; Section 8 error envelope never had `success:false`. — **Cross-checked:** Brief Phase 2 explicitly adds `success`/`message` for login success. Error envelope intentionally omits `success` field (no client expects it — envelope shape is fixed per Section 8). No contradiction. | ✅ Match |
| User entity from Phase 1 skeleton did NOT have `enabled` field. — **Resolved:** Technical design adds it; Phase 3 code phase will add the field to `User.java` + `schema.sql` already declares it. `DataInitializer` sets enabled=true when seeding. | ✅ Resolved |

**Result:** Design is internally consistent. Single rename `password → password_hash` is explicitly justified by security requirements; all other brief mandates are satisfied verbatim.

---

*End of Technical Design v1.0.*
