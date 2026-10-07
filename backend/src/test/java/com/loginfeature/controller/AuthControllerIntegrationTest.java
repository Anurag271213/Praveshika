package com.loginfeature.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loginfeature.config.AppProperties;
import com.loginfeature.dto.LoginRequest;
import com.loginfeature.entity.User;
import com.loginfeature.repository.UserRepository;
import com.loginfeature.security.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AppProperties appProperties;

    private User activeUser;
    private User disabledUser;

    @BeforeEach
    void setUp() {
        // Clear non-admin test users or re-create
        userRepository.findByUsername("testuser").ifPresent(userRepository::delete);
        userRepository.findByUsername("disableduser").ifPresent(userRepository::delete);

        activeUser = new User(
                "testuser",
                "user@example.com",
                passwordEncoder.encode("Password123"),
                Boolean.TRUE
        );
        activeUser = userRepository.save(activeUser);

        disabledUser = new User(
                "disableduser",
                "disabled@example.com",
                passwordEncoder.encode("Password123"),
                Boolean.FALSE
        );
        disabledUser = userRepository.save(disabledUser);
    }

    @Test
    @DisplayName("TC-001: Valid username + password authentication succeeds")
    void tc001_validUsernameAndPassword() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Login successful")))
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.id", is(activeUser.getId().intValue())))
                .andExpect(jsonPath("$.user.username", is("testuser")))
                .andExpect(jsonPath("$.user.email", is("user@example.com")));
    }

    @Test
    @DisplayName("TC-002: Valid email + password authentication succeeds")
    void tc002_validEmailAndPassword() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Login successful")))
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.username", is("testuser")))
                .andExpect(jsonPath("$.user.email", is("user@example.com")));
    }

    @Test
    @DisplayName("TC-003: Invalid username returns generic 401 error")
    void tc003_invalidUsername() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent_user", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid username/email or password")));
    }

    @Test
    @DisplayName("TC-004: Invalid email returns generic 401 error")
    void tc004_invalidEmail() throws Exception {
        LoginRequest request = new LoginRequest("unknown@example.com", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid username/email or password")));
    }

    @Test
    @DisplayName("TC-005: Invalid password returns generic 401 error")
    void tc005_invalidPassword() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "WrongPassword123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid username/email or password")));
    }

    @Test
    @DisplayName("TC-006: Blank username/email returns 400 Bad Request with field error")
    void tc006_blankUsernameOrEmail() throws Exception {
        LoginRequest request = new LoginRequest("", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("usernameOrEmail")));
    }

    @Test
    @DisplayName("TC-007: Blank password returns 400 Bad Request with field error")
    void tc007_blankPassword() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));
    }

    @Test
    @DisplayName("TC-008: Both fields blank returns 400 Bad Request with multiple field errors")
    void tc008_bothFieldsBlank() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("usernameOrEmail", "password")));
    }

    @Test
    @DisplayName("TC-009: Disabled user authentication is rejected with 401")
    void tc009_disabledUser() throws Exception {
        LoginRequest request = new LoginRequest("disableduser", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Invalid username/email or password")));
    }

    @Test
    @DisplayName("TC-010: Invalid JWT accessing protected endpoint returns 401")
    void tc010_invalidJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("TC-011: Expired JWT accessing protected endpoint returns 401")
    void tc011_expiredJwt() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject(String.valueOf(activeUser.getId()))
                .issuer(appProperties.getJwt().getIssuer())
                .issuedAt(new Date(System.currentTimeMillis() - 200000))
                .expiration(new Date(System.currentTimeMillis() - 100000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("TC-012: Missing JWT accessing protected endpoint returns 401")
    void tc012_missingJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("TC-013: Valid JWT accessing /api/auth/me returns user profile")
    void tc013_validJwtAccessingMe() throws Exception {
        String token = jwtTokenProvider.generateToken(activeUser.getId());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(activeUser.getId().intValue())))
                .andExpect(jsonPath("$.username", is("testuser")))
                .andExpect(jsonPath("$.email", is("user@example.com")));
    }

    @Test
    @DisplayName("TC-014: Unauthenticated access to /api/auth/me returns 401")
    void tc014_unauthenticatedAccessToMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-015: Logout endpoint returns 200 with success message")
    void tc015_logout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Logout successful")));
    }

    @Test
    @DisplayName("TC-016: BCrypt password hashing verification")
    void tc016_bcryptPasswordHashing() {
        String rawPassword = "SecurePassword@123";
        String hashed = passwordEncoder.encode(rawPassword);

        assertThat(hashed).isNotEqualTo(rawPassword);
        assertThat(hashed).startsWith("$2a$10$");
        assertThat(passwordEncoder.matches(rawPassword, hashed)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword", hashed)).isFalse();
    }

    @Test
    @DisplayName("TC-017: Password or hash is never returned in API responses")
    void tc017_passwordNotReturnedInResponse() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "Password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        String token = jwtTokenProvider.generateToken(activeUser.getId());
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("TC-018: Duplicate username rejected at database level")
    void tc018_duplicateUsername() {
        User duplicate = new User(
                "testuser",
                "different@example.com",
                passwordEncoder.encode("Password123")
        );

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("TC-019: Duplicate email rejected at database level")
    void tc019_duplicateEmail() {
        User duplicate = new User(
                "differentuser",
                "user@example.com",
                passwordEncoder.encode("Password123")
        );

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("TC-020: Health endpoint returns 200 with status UP")
    void tc020_healthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.timestamp", not(emptyOrNullString())));
    }
}
