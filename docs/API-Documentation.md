# Login Feature — API Documentation

**Version:** 1.0
**Date:** 2026-10-07
**Status:** Completed (Phase 5 — Verified & Demo Ready)
**Base URL (dev):** `http://localhost:8080`
**Base URL (frontend proxy):** `/api` (via Vite `proxy` config)
**Content-Type:** All request bodies are `application/json`. All response bodies are `application/json;charset=UTF-8`.
**Related Docs:** [Requirement-Specification.md](./Requirement-Specification.md), [Technical-Design.md](./Technical-Design.md)

---

## Overview

Four HTTP endpoints are exposed by the backend for the login feature.

| # | Method | Path | Authentication | Purpose |
|---|---|---|---|---|
| 1 | `GET`  | `/api/health`      | None | Liveness check. Always returns 200 unless the JVM itself is dead. |
| 2 | `POST` | `/api/auth/login`  | None | Authenticate a user. On success returns a signed JWT. |
| 3 | `POST` | `/api/auth/logout` | Optional | Server-side logout audit hook. Actual revocation is client-side. |
| 4 | `GET`  | `/api/auth/me`     | Bearer JWT | Retrieve authenticated user profile (`id`, `username`, `email`). |

### Standard Status Codes Used

| Status | Standard Phrase | Used for |
|---|---|---|
| **200** | `OK` | Successful login / logout / health. |
| **400** | `Bad Request` | Request body fails validation (missing fields, wrong lengths, bad email format). Response includes per-field details. |
| **401** | `Unauthorized` | Bad credentials, unknown identifier, disabled account, missing/invalid/expired JWT on protected endpoints. Generic message only. |
| **403** | `Forbidden` | CORS rejected; or valid credentials but missing authority (future RBAC). Body has a single top-level `message`. |
| **404** | `Not Found` | Unknown route. Same error envelope shape as 4xx. |
| **405** | `Method Not Allowed` | e.g. `GET /api/auth/login`. Same error envelope. |
| **500** | `Internal Server Error` | Unhandled server-side exception. Stack trace is logged server-side only. |
| **503** | `Service Unavailable` | (Future) — health endpoint can return this once DB checks are added. Current implementation always returns 200. |

### Authentication Scheme (Protected Endpoints)

For any endpoint **not** listed in the table above (future APIs such as `GET /api/me`), the client MUST send:

```
Authorization: Bearer <token>
```

Where `<token>` is the exact string returned in the login response's `token` field. If the header is missing, malformed, the token is expired, signature is wrong, or the user no longer exists in the database → HTTP **401** with generic body (Section 4).

---

## 1. `GET /api/health`

Lightweight liveness endpoint. Does **not** query the database (that would be a *readiness* check; out of scope per "no unnecessary dependencies" rule — no Actuator).

### Request

- No body
- No query parameters
- No headers required

**Example:**
```http
GET /api/health HTTP/1.1
Host: localhost:8080
Accept: application/json
```

### Response — HTTP 200 OK

```json
{
  "status": "UP",
  "timestamp": "2026-10-07T10:15:30.123456Z"
}
```

| Field | Type | Description |
|---|---|---|
| `status` | string | Always literal `"UP"`. |
| `timestamp` | ISO-8601 UTC string | Server wall-clock instant the response was generated. |

---

## 2. `POST /api/auth/login`

Authenticate a user via username/email + password.

### Request

```
POST /api/auth/login
Content-Type: application/json
```

| Field | Type | Required | Validation rules |
|---|---|---|---|
| `usernameOrEmail` | string | **Yes** | Not blank. Max 100 chars. If contains `@`, must be a valid email (RFC-5322 subset — matches Jakarta `@Email`). |
| `password` | string | **Yes** | Not blank. Min 6 chars. Max 128 chars. |

**Example (email login):**
```json
{
  "usernameOrEmail": "user@example.com",
  "password": "Password123"
}
```

**Example (username login):**
```json
{
  "usernameOrEmail": "admin",
  "password": "Admin@123"
}
```

### Response — HTTP 200 OK (success)

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

| Field | Type | Description |
|---|---|---|
| `success` | boolean | Always `true` on 200. Symmetric with error UX patterns. |
| `message` | string | Human-friendly one-line confirmation. Constant English literal. |
| `token` | string | Signed JWT. Pass as `Authorization: Bearer <token>` on subsequent protected requests. See **Appendix A** for JWT claim definitions. |
| `tokenType` | string | Always literal `"Bearer"`. Header scheme. |
| `expiresIn` | integer (seconds) | Valid lifetime of the token, from `iat` to `exp`. Used by the frontend to show warning banners / auto-logout before expiry. |
| `user` | object | Public profile for the authenticated user. This object will **never** include `password`, `passwordHash`, or any other credential field. |
| `user.id` | integer | Primary key of the row in `users` table. |
| `user.username` | string | Stored username. |
| `user.email` | string | Stored email. |

### Response — HTTP 400 Bad Request (validation failed)

See Section 4, Error Response Envelope. Example:

```json
{
  "timestamp": "2026-10-07T10:15:30.123456Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/login",
  "fieldErrors": [
    { "field": "password", "message": "Password must be between 6 and 128 characters" },
    { "field": "usernameOrEmail", "message": "Username or email is required" }
  ]
}
```

Common `fieldErrors` for this endpoint:

| `field` | `message` | Trigger |
|---|---|---|
| `usernameOrEmail` | `"Username or email is required"` | Blank / whitespace only. |
| `usernameOrEmail` | `"Username or email must not exceed 100 characters"` | Longer than 100. |
| `usernameOrEmail` | `"Must be a valid email address"` | Contains `@` but fails email check. |
| `password` | `"Password is required"` | Blank / whitespace only. |
| `password` | `"Password must be between 6 and 128 characters"` | Shorter than 6 or longer than 128. |

### Response — HTTP 401 Unauthorized (authentication failed)

See Section 4. Example:

```json
{
  "timestamp": "2026-10-07T10:15:30.123456Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username/email or password",
  "path": "/api/auth/login"
}
```

**Crucial security note (user enumeration prevention):** This exact body, status, and message string are returned for all the following cases. Client code MUST NOT attempt to distinguish them.

1. `usernameOrEmail` value is not found in the `users` table at all.
2. `usernameOrEmail` matches a row, but `BCryptPasswordEncoder.matches(password, password_hash)` returns `false`.
3. `usernameOrEmail` matches a row, but the user's `enabled` column is `0` (account disabled).

### Response — HTTP 500 Internal Server Error

Unrecoverable server error (e.g., DB connectivity lost, unexpected NPE). See envelope in Section 4. `message` is always safe; stacktrace is in server logs only.

---

## 3. `POST /api/auth/logout`

Client-initiated logout. Server contract: always acknowledges. Actual token invalidation is client-side token removal (refresh tokens / token blacklists are out of scope).

### Request

```
POST /api/auth/logout
```

- Request body is **optional** and **ignored** if present. A body of `{}` is accepted.
- `Authorization` header is **optional**:
  - If present and valid → server logs `LOGOUT userId=<id>` at INFO.
  - If missing, malformed, or expired → server still returns HTTP 200 (so the frontend never gets "failed to log you out" UX). A DEBUG log line records an anonymous logout.

### Response — HTTP 200 OK

```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

| Field | Type | Description |
|---|---|---|
| `success` | boolean | Always `true` (logout POST is always acknowledged). |
| `message` | string | Human-friendly confirmation. |

No error paths produce non-200 responses from this endpoint by design (client UX always succeeds; diagnostics go to logs only).

---

## 4. `GET /api/auth/me` (Protected Resource)

Retrieve the public profile of the currently authenticated user.

### Request

```http
GET /api/auth/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer <token>
Accept: application/json
```

- **Headers:** `Authorization: Bearer <token>` (**Required**)
- **Body:** None

### Response — HTTP 200 OK (Authenticated)

```json
{
  "id": 1,
  "username": "admin",
  "email": "admin@example.com"
}
```

| Field | Type | Description |
|---|---|---|
| `id` | integer | Primary key identifier in database. |
| `username` | string | User's unique login username. |
| `email` | string | User's unique registered email address. |

### Response — HTTP 401 Unauthorized (Missing / Invalid Token)

```json
{
  "success": false,
  "timestamp": "2026-10-07T10:15:30.123456Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/auth/me"
}
```

---

## 5. Common Error Response Envelope

All 4xx/5xx responses across every endpoint return the same JSON shape:

```json
{
  "timestamp": "2026-10-07T10:15:30.123456Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username/email or password",
  "path": "/api/auth/login"
}
```

### Field Definitions

| Field | Type | Always present? | Notes |
|---|---|---|---|
| `timestamp` | ISO-8601 UTC string | Yes | Server instant when the error was handled. |
| `status` | integer | Yes | Mirrors HTTP response status code. |
| `error` | string | Yes | `HttpStatus.valueOf(status).getReasonPhrase()` (e.g., `"Bad Request"`, `"Unauthorized"`, `"Internal Server Error"`). |
| `message` | string | Yes | User-facing message in English. Never contains a Java stack-trace line, DB error code, or credential fragment. |
| `path` | string | Yes | Request URI (without query string). |
| `fieldErrors` | array of objects | **Only on HTTP 400 validation failures.** | Each entry is `{ field: string, message: string }` — see Section 2's 400 example. Omitted (JSON null or absent) for all other status codes. |

### Quick Reference — Common `(status, message)` Pairs

| Scenario | status | message | fieldErrors? |
|---|---|---|---|
| Any 400 validation failure on `/api/auth/login` | 400 | `"Validation failed"` | YES |
| Bad JSON (cannot deserialize) | 400 | `"Malformed JSON payload"` or Spring default message | NO / maybe |
| Wrong identifier OR wrong password OR disabled account on login | 401 | `"Invalid username/email or password"` | NO |
| Missing, malformed, expired, or bad-sig JWT on protected endpoint | 401 | `"Invalid or expired token"` | NO |
| CORS blocked origin (browser-level; server-side fallback) | 403 | `"Access denied"` | NO |
| Valid JWT but not allowed (future RBAC) | 403 | `"Access denied"` | NO |
| Unknown path | 404 | `"Not Found"` | NO |
| Wrong HTTP method | 405 | `"Method Not Allowed"` | NO |
| DB down / NPE / any uncaught | 500 | `"An unexpected error occurred. Please try again later."` | NO |

---

## Appendix A — JWT Claim Dictionary

The `token` returned by `/api/auth/login` is a JWT signed with HS256 (HMAC-SHA-256) using the server's `app.jwt.secret`. Decoding it (e.g. at jwt.io) yields the following header + payload:

### JWT Header

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

- `alg` is **always** `"HS256"`. The backend parser rejects tokens whose `alg` header is missing or different.
- `none` algorithm is explicitly rejected by the JJWT parser configuration.

### JWT Payload (Claims)

```json
{
  "iss": "login-feature-backend",
  "sub": "1",
  "username": "admin",
  "email": "admin@example.com",
  "iat": 1760000000,
  "exp": 1760086400
}
```

| Claim | Type | Presence | Description |
|---|---|---|---|
| `iss` | string | Required | Issuer. Must equal the runtime value of `app.jwt.issuer`. Parser hard-requires this match. |
| `sub` | string (numeric) | Required | Subject. Stringified `users.id`. **This is the trust anchor** — when verifying protected endpoints the server loads the user row by `Long.parseLong(sub)`. The `username`/`email` claims are only convenience copies. |
| `username` | string | Required (custom convenience) | Copy of `users.username` at token-issue time. |
| `email` | string | Required (custom convenience) | Copy of `users.email` at token-issue time. |
| `iat` | number (Unix epoch seconds) | Required | Issued-at timestamp. |
| `exp` | number (Unix epoch seconds) | Required | Expires-at timestamp. `exp - iat === expiresIn` from the login response JSON, in seconds. Token is rejected if current time > `exp`. |

### JWT Security Notes

- Tokens are **stateless**: there is no server-side token store, and no "revocation" mechanism short of rotating the signing secret (which invalidates all tokens). See Requirement-Spec §16 OoS-6.
- Tokens MUST be transmitted over HTTPS in production (Assumption A-1).
- Tokens MUST be stored only on the client (`localStorage` for this project). The server never returns cookies or stores tokens.

---

## Appendix B — cURL Examples

Use these in a terminal to smoke-test each endpoint.

### B.1 Health

```bash
curl -i -X GET http://localhost:8080/api/health \
     -H "Accept: application/json"
```

Expected: HTTP/1.1 200 + JSON with `status:"UP"`.

### B.2 Login (valid — seeded admin)

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -H "Accept: application/json" \
     -d '{ "usernameOrEmail": "admin@example.com", "password": "Admin@123" }'
```

Expected: HTTP/1.1 200 + `success:true`, `token:"..."`.

### B.3 Login (invalid — wrong password)

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{ "usernameOrEmail": "admin", "password": "WrongPass" }'
```

Expected: HTTP/1.1 401, `message:"Invalid username/email or password"`.

### B.4 Login (validation failure — blank password)

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{ "usernameOrEmail": "admin", "password": "" }'
```

Expected: HTTP/1.1 400, `fieldErrors` array with `field="password"`.

### B.5 Logout (authenticated)

Replace the `<token>` with a real JWT from B.2.

```bash
curl -i -X POST http://localhost:8080/api/auth/logout \
     -H "Authorization: Bearer <token>"
```

Expected: HTTP/1.1 200 + `{ "success": true, "message": "Logged out successfully" }`.

---

*End of API Documentation v1.0.*
