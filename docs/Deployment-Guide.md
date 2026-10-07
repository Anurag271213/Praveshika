# Login Feature — Deployment & Run Guide

**Project:** Login Feature Development (Intern Training Reference Implementation)  
**Version:** 1.0.0  
**Stack:** Java 17/21, Spring Boot 3.3.4, React 19, Vite, MySQL 8.0  

---

## 1. Prerequisites & Environment Setup

Ensure the following tools are installed on your host system:

| Component | Minimum Version | Recommended Version | Verification Command |
|---|---|---|---|
| **Java Development Kit (JDK)** | Java 17 LTS | Java 17 or 21 LTS | `java -version` |
| **Apache Maven** | 3.6.3 | 3.9.x (or `./mvnw`) | `mvn -version` or `.\mvnw.cmd -version` |
| **Node.js** | 18.0.0 | 20.x or 22.x LTS | `node -v` |
| **npm** | 9.0.0 | 10.x | `npm -v` |
| **MySQL Server** | 8.0 | 8.0+ | `mysql --version` |

---

## 2. Database Setup (MySQL 8)

### Step 2.1 — Start MySQL Server
Make sure the MySQL service is running on your machine:
- **Windows (PowerShell as Admin):**
  ```powershell
  Start-Service MySQL80
  ```
- **Linux/macOS:**
  ```bash
  sudo systemctl start mysql
  ```

### Step 2.2 — Create Database and Execute Schema
Log into the MySQL CLI:
```bash
mysql -u root -p
```

Create the application database:
```sql
CREATE DATABASE IF NOT EXISTS login_feature_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE login_feature_db;
```

Run the schema script located at `database/schema.sql`:
```sql
SOURCE database/schema.sql;
```

Verify table creation:
```sql
SHOW TABLES;
DESCRIBE users;
```

---

## 3. Configuration & Environment Variables

### 3.1 Backend Environment Variables
Create a local `.env` file in `backend/` or configure the following environment variables:

| Variable | Description | Development Default |
|---|---|---|
| `SERVER_PORT` | Backend HTTP port | `8080` |
| `DATABASE_URL` | JDBC Connection String | `jdbc:mysql://localhost:3306/login_feature_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DATABASE_USERNAME` | MySQL Username | `root` |
| `DATABASE_PASSWORD` | MySQL Password | `root` |
| `JPA_DDL_AUTO` | Hibernate Schema Management | `update` |
| `JWT_SECRET` | HMAC-SHA256 Signing Secret (min 256 bits) | (32+ character high-entropy string) |
| `JWT_EXPIRATION_MS` | Token lifetime in milliseconds | `86400000` (24 hours) |
| `JWT_ISSUER` | JWT Issuer claim (`iss`) | `login-feature-backend` |
| `ADMIN_USERNAME` | Auto-seeded Administrator Username | `admin` |
| `ADMIN_EMAIL` | Auto-seeded Administrator Email | `admin@example.com` |
| `ADMIN_PASSWORD` | Auto-seeded Administrator Password | `Admin@123` |
| `CORS_ALLOWED_ORIGINS` | Permitted Frontend Origins | `http://localhost:5173` |

### 3.2 Frontend Environment Variables
In `frontend/.env`:

| Variable | Description | Development Default |
|---|---|---|
| `VITE_API_BASE_URL` | Base URL of backend API | `http://localhost:8080` (or empty string when using Vite proxy) |

---

## 4. Building and Running the Backend

### Step 4.1 — Execute Automated Tests
From the `backend/` directory:
```powershell
.\mvnw.cmd clean test
# or if maven is in PATH:
mvn clean test
```
All 38 automated test cases covering TC-001 through TC-020 must pass.

### Step 4.2 — Build Production JAR
```powershell
.\mvnw.cmd clean package
```
Generates `target/login-feature-backend-1.0.0.jar`.

### Step 4.3 — Run the Backend Server
**Option A — Direct Maven Run:**
```powershell
.\mvnw.cmd spring-boot:run
```

**Option B — Execute Packaged JAR:**
```powershell
java -jar target/login-feature-backend-1.0.0.jar
```

The server starts on port `8080`. On initial boot, `DataInitializer` detects an empty user table and seeds the default administrator:
- Username: `admin`
- Email: `admin@example.com`
- Password: `Admin@123`

---

## 5. Building and Running the Frontend

### Step 5.1 — Install Dependencies
From the `frontend/` directory:
```bash
npm install
```

### Step 5.2 — Run in Development Mode
```bash
npm run dev
```
The Vite development server runs on `http://localhost:5173`. Any requests to `/api/*` are automatically proxied to `http://localhost:8080`.

### Step 5.3 — Build for Production
```bash
npm run build
```
Creates production-optimized assets in `frontend/dist/`.

To preview the production bundle locally:
```bash
npm run preview
```

---

## 6. End-to-End Verification & API Smoke Testing

### 6.1 Liveness Health Check
```bash
curl -i -X GET http://localhost:8080/api/health
```
Response: HTTP 200 with `{"status":"UP","timestamp":"..."}`.

### 6.2 Administrator Login (Username)
```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"admin","password":"Admin@123"}'
```
Response: HTTP 200 with `success: true`, signed `token`, and user profile.

### 6.3 Administrator Login (Email)
```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"admin@example.com","password":"Admin@123"}'
```
Response: HTTP 200 with `success: true`.

### 6.4 Access Protected Resource (`/api/auth/me`)
```bash
curl -i -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <TOKEN_FROM_STEP_6.2>"
```
Response: HTTP 200 with `{ "id": 1, "username": "admin", "email": "admin@example.com" }`.

### 6.5 Logout
```bash
curl -i -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <TOKEN_FROM_STEP_6.2>"
```
Response: HTTP 200 with `{"success":true,"message":"Logout successful"}`.

---

## 7. Troubleshooting Guide

| Issue | Root Cause | Solution |
|---|---|---|
| `CommunicationsException: Communications link failure` | MySQL Server service is stopped | Start MySQL service via `Start-Service MySQL80` or ensure MySQL is running on port 3306. |
| `Access denied for user 'root'@'localhost'` | DB password mismatch | Update `DATABASE_PASSWORD` environment variable to match your local MySQL root password. |
| `401 Unauthorized` on `/api/auth/login` with default credentials | Password was changed or table already populated with different credentials | Verify DB contents in `users` table or start with clean `login_feature_db`. |
| `CORS Error: Origin not allowed` | Frontend origin not permitted | Set `CORS_ALLOWED_ORIGINS=http://localhost:5173` in backend properties or environment. |
| `npm.ps1 cannot be loaded because running scripts is disabled` | PowerShell ExecutionPolicy | Invoke via `npm.cmd` directly: `npm.cmd install`, `npm.cmd run build`. |
