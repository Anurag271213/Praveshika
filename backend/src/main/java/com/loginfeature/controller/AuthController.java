package com.loginfeature.controller;

import com.loginfeature.dto.LoginRequest;
import com.loginfeature.dto.LoginResponse;
import com.loginfeature.dto.LogoutResponse;
import com.loginfeature.dto.UserResponse;
import com.loginfeature.exception.InvalidCredentialsException;
import com.loginfeature.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<LogoutResponse> logout() {
        LogoutResponse response = authService.logout();
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/auth/me", "/me"})
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new InvalidCredentialsException("Full authentication is required to access this resource");
        }
        UserResponse response = authService.getCurrentUserByUsername(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
