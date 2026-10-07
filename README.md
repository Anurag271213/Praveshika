# Secure Login Feature — Intern Training Reference Implementation

[![Java](https://img.shields.io/badge/Java-17%20%2F%2021-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19.0.0-blue.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-5.4.x-purple.svg)](https://vitejs.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Tests](https://img.shields.io/badge/Tests-38%2F38%20Passed-success.svg)]()

A complete, production-quality, industry-standard authentication subsystem developed as a teaching reference implementation for the Intern Training Program.

---

## 🌟 Key Features

1. **Dual-Identifier Authentication:** Log in with either **Username** or **Email** in the same field.
2. **Robust Password Security:** Passwords hashed exclusively using **BCrypt** (strength cost factor 10); raw passwords never persisted or logged.
3. **Stateless JWT Authorization:** High-entropy HMAC-SHA256 signed JSON Web Tokens with configurable TTL.
4. **Defense-in-Depth Validation:** Client-side form validation paired with Jakarta Bean Validation on the REST layer.
5. **Generic Error Responses:** Prevents account enumeration by returning unified `"Invalid username/email or password"` on all credential and account status failures.
6. **Protected Endpoints & Routes:** `GET /api/auth/me` guarded by Spring Security filter chain; `/dashboard` protected by React `PrivateRoute` guard.
7. **Clean Layered Architecture:** Controllers, Services, Repositories, Entities, DTOs, Security Filters, and Exception Handlers strictly separated.
8. **Automated Admin Seeding:** Configurable, secure default administrator seed upon first startup (`admin` / `Admin@123`).
9. **Responsive UI:** Dark-mode glassmorphic interface optimized for desktop, tablet, and mobile browsers.
10. **Comprehensive Test Suite:** 38 automated test cases covering happy paths, edge cases, validation, and security vulnerabilities (TC-001 through TC-020).

---

## 🛠️ Technology Stack

| Layer | Technologies |
|---|---|
| **Backend** | Java 17/21, Spring Boot 3.3.4, Spring Security 6, Spring Data JPA, Hibernate 6, JJWT 0.12.6, Maven 3.9 |
| **Frontend** | React 19, Vite, Axios, React Router 6, Vanilla CSS Design System |
| **Database** | MySQL 8.0 (Production) / H2 in MySQL Mode (Automated Testing) |

---

## 🚀 Quick Start Guide

### 1. Database Setup
```sql
CREATE DATABASE IF NOT EXISTS login_feature_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE login_feature_db;
SOURCE database/schema.sql;
```

### 2. Backend Startup
```powershell
cd backend
.\mvnw.cmd clean test       # Run all 38 tests
.\mvnw.cmd spring-boot:run  # Starts on http://localhost:8080
```

### 3. Frontend Startup
```powershell
cd frontend
npm.cmd install             # Install dependencies
npm.cmd run dev             # Starts on http://localhost:5173
```

Open `http://localhost:5173` in your browser.  
Log in using the default admin credentials:
- **Username / Email:** `admin` or `admin@example.com`
- **Password:** `Admin@123`

---

## 📡 REST API Specifications

| Method | Endpoint | Auth Required | Description |
|---|---|---|---|
| `GET` | `/api/health` | No | Public liveness probe returning `{"status": "UP"}` |
| `POST` | `/api/auth/login` | No | Authenticates user; returns JWT and user profile |
| `POST` | `/api/auth/logout` | Optional | Logout audit hook; client discards token |
| `GET` | `/api/auth/me` | **Bearer JWT** | Returns current user profile (`id`, `username`, `email`) |

### Example Login Request
```http
POST /api/auth/login
Content-Type: application/json

{
  "usernameOrEmail": "admin",
  "password": "Admin@123"
}
```

### Example Success Response (HTTP 200)
```json
{
  "success": true,
  "message": "Login successful",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "admin",
    "email": "admin@example.com"
  }
}
```

---

## 🧪 Automated Testing

Run the test suite with:
```powershell
cd backend
.\mvnw.cmd clean test
```

### Test Coverage Highlights (38 Tests Total):
- **TC-001 / TC-002:** Valid username & valid email authentication
- **TC-003 / TC-004:** Non-existent username & email rejection
- **TC-005:** Bad password rejection
- **TC-006 / TC-007 / TC-008:** Input validation for blank fields (HTTP 400 with field errors)
- **TC-009:** Disabled account rejection (HTTP 401 generic message)
- **TC-010 / TC-011 / TC-012:** Invalid, expired, and missing JWT rejection (HTTP 401)
- **TC-013 / TC-014:** Protected `/api/auth/me` access verification
- **TC-015:** Logout API verification
- **TC-016:** BCrypt strength 10 hashing & match verification
- **TC-017:** Credential non-exposure verification in response JSON
- **TC-018 / TC-019:** Database uniqueness constraints on username & email
- **TC-020:** Health probe verification

---

## 📚 Project Documentation

Detailed project phase documents are located in `docs/`:
- [Requirement Specification](docs/Requirement-Specification.md)
- [Technical Design Document](docs/Technical-Design.md)
- [API Documentation](docs/API-Documentation.md)
- [Test Case & Execution Sheet](docs/Test-Case-Execution-Sheet.md)
- [Deployment & Operations Guide](docs/Deployment-Guide.md)
- [Live Demo Checklist](docs/Demo-Checklist.md)
