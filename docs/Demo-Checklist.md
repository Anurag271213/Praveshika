# Login Feature — Demo & Presentation Checklist

**Audience:** Intern Developers, Engineering Mentors, Technical Reviewers  
**Purpose:** Standard operating procedure for demonstrating the working Login Subsystem end-to-end.  

---

## Pre-Demo Verification Checklist

- [x] Java 17/21 installed and verified (`java -version`).
- [x] Backend automated test suite passing (`38/38` tests pass via `mvn test`).
- [x] Frontend builds without warnings or errors (`npm run build`).
- [x] MySQL database created (`login_feature_db`) and service active.
- [x] Application default admin user seeded (`admin` / `Admin@123`).

---

## Live Demonstration Flow

### 1. Backend Liveness & Architecture
1. **Show Backend Terminal:**
   - Demonstrate `mvn test` output showing all 38 test cases passing cleanly.
   - Start backend server on `http://localhost:8080`.
2. **Execute Health Check:**
   - Call `GET http://localhost:8080/api/health` via browser or terminal.
   - Highlight: HTTP 200 `{"status": "UP"}`.

### 2. Frontend Launch & Initial Presentation
1. Start frontend development server via `npm run dev` (`http://localhost:5173`).
2. Open browser to `http://localhost:5173`.
3. Highlight UI Aesthetics:
   - Polished dark-mode theme, typography, subtle gradients, and glassmorphic card.
   - Responsive layout on desktop, tablet, and mobile viewport simulations.

### 3. Client-Side Input Validation Demonstration
1. Leave both "Username or Email" and "Password" fields empty.
2. Click "Sign In".
3. **Observe:**
   - Inline validation errors appear:
     - *"Username or email is required"*
     - *"Password is required"*
   - Red highlight borders on invalid inputs.
   - Zero network requests sent to backend (client validation stops submission).
4. Enter invalid email format (e.g., `invalid@`):
   - Notice instant format feedback: *"Please enter a valid email address"*.

### 4. Security & Failed Authentication Flow
1. Enter an unknown username (e.g. `nonexistent_user`) and password `Password123`.
2. Click "Sign In":
   - Button enters loading state (*"Authenticating..."* with spinner).
   - Server returns HTTP 401 Unauthorized.
   - **Observe:** Alert banner displays generic message: *"Invalid username/email or password"*.
   - **Point out security principle:** The application never reveals whether the account exists or if the password was incorrect (preventing user enumeration).

### 5. Successful Login Flow (Username & Email)
1. Enter valid seeded credentials:
   - Username: `admin`
   - Password: `Admin@123`
2. Test "Show/Hide Password" toggle button:
   - Input switches between masked `password` and plaintext `text`.
3. Click "Sign In":
   - System authenticates via BCrypt matching and JJWT.
   - Redirects to `/dashboard`.
4. Demonstrate alternate identifier:
   - Log out, enter `admin@example.com` with `Admin@123`.
   - Successful login proceeds symmetrically.

### 6. Protected Dashboard & Session Persistence
1. **Examine Dashboard Page:**
   - Welcome banner displays authenticated username (`admin`).
   - Profile card displays User ID (`#1`), Username (`admin`), and Email (`admin@example.com`).
   - Authentication token card displays truncated Bearer JWT string and scheme.
   - Live backend health indicator shows `UP`.
2. **Demonstrate Session Persistence on Page Refresh:**
   - Press `F5` / reload browser page.
   - App calls `GET /api/auth/me` on startup.
   - User remains seamlessly on `/dashboard` without unwanted redirect loops.

### 7. Route Guarding & Protected Route Demonstration
1. Open a new private/incognito browser tab.
2. Navigate directly to `http://localhost:5173/dashboard`.
3. **Observe:** `PrivateRoute` intercepts unauthenticated request and immediately redirects to `/login`.

### 8. Logout Demonstration
1. On the active dashboard, click the "Logout" button in the Navbar or dashboard card.
2. **Observe:**
   - Backend audit endpoint `POST /api/auth/logout` called.
   - Token and profile flushed from client `localStorage`.
   - Browser navigates to `/login`.
3. Press the browser "Back" button or manually type `/dashboard`:
   - Route guard redirects back to `/login`.
   - Protected dashboard is completely inaccessible after logout.

---

## Code & Security Architecture Review Points

During the presentation, walk mentors through these specific architectural implementations:

1. **Defense in Depth:** Dual-layer validation (client-side form validation + Jakarta Bean Validation on `LoginRequest`).
2. **Password Security:**
   - `BCryptPasswordEncoder` with strength 10.
   - User entity column is named `password_hash`, not `password`.
   - No credential fields appear in DTOs (`UserResponse`, `LoginResponse`).
3. **Stateless JWT Security:**
   - `SessionCreationPolicy.STATELESS` in Spring Security.
   - `JwtTokenProvider` using JJWT 0.12.6 with HS256 and verified claims.
   - `JwtAuthenticationFilter` populates `SecurityContextHolder` per request.
4. **Global Exception Handling:**
   - `GlobalExceptionHandler` and `AuthenticationEntryPoint` ensure consistent JSON error envelope for 400, 401, 403, and 500 without leaking server stack traces.
