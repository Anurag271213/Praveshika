package com.loginfeature.service;

import com.loginfeature.config.AppProperties;
import com.loginfeature.dto.LoginRequest;
import com.loginfeature.dto.LoginResponse;
import com.loginfeature.dto.LogoutResponse;
import com.loginfeature.dto.UserResponse;
import com.loginfeature.entity.User;
import com.loginfeature.exception.AccountDisabledException;
import com.loginfeature.exception.InvalidCredentialsException;
import com.loginfeature.repository.UserRepository;
import com.loginfeature.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppProperties appProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String identifier = request.getUsernameOrEmail() != null ? request.getUsernameOrEmail().trim() : "";
        String password = request.getPassword();

        User user = userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> {
                    log.warn("Authentication failed: user identifier not found: {}", redact(identifier));
                    return new InvalidCredentialsException("Invalid username/email or password");
                });

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("Authentication failed: account is disabled for user id: {}", user.getId());
            throw new AccountDisabledException("Invalid username/email or password");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Authentication failed: password mismatch for user id: {}", user.getId());
            throw new InvalidCredentialsException("Invalid username/email or password");
        }

        String token = jwtTokenProvider.generateToken(user.getId());
        long expirationMs = appProperties.getJwt().getExpirationMs();
        long expiresInSeconds = expirationMs > 0 ? expirationMs / 1000 : 86400;

        UserResponse userResponse = new UserResponse(user.getId(), user.getUsername(), user.getEmail());

        log.info("User authenticated successfully: id={}, username={}", user.getId(), user.getUsername());

        return new LoginResponse(
                Boolean.TRUE,
                "Login successful",
                token,
                expiresInSeconds,
                userResponse
        );
    }

    public LogoutResponse logout() {
        log.info("User logout requested");
        return new LogoutResponse(Boolean.TRUE, "Logout successful");
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new AccountDisabledException("Account is disabled");
        }

        return new UserResponse(user.getId(), user.getUsername(), user.getEmail());
    }

    private static String redact(String identifier) {
        if (identifier == null || identifier.length() <= 2) {
            return "***";
        }
        return identifier.charAt(0) + "***" + identifier.charAt(identifier.length() - 1);
    }
}
