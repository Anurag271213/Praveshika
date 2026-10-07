package com.loginfeature;

import com.loginfeature.config.AppProperties;
import com.loginfeature.repository.UserRepository;
import com.loginfeature.service.DataInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoginFeatureApplicationContextTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties appProperties;

    @Autowired(required = false)
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("Context loads: all required beans for DB layer are present")
    void contextLoads_dbLayerBeansPresent() {
        assertThat(userRepository).isNotNull();
        assertThat(passwordEncoder).isNotNull();
        assertThat(appProperties).isNotNull();
        assertThat(dataInitializer).isNotNull();
    }

    @Test
    @DisplayName("BCrypt PasswordEncoder is wired and can hash + match")
    void passwordEncoder_hashesAndMatches() {
        String raw = "StrongP@ss1";
        String hash = passwordEncoder.encode(raw);
        assertThat(hash).startsWith("$2a$10$");
        assertThat(passwordEncoder.matches(raw, hash)).isTrue();
        assertThat(passwordEncoder.matches("wrong", hash)).isFalse();
    }

    @Test
    @DisplayName("AppProperties bind correctly: admin + jwt + cors defaults")
    void appProperties_bindDefaults() {
        assertThat(appProperties.getAdmin().getUsername()).isNotBlank();
        assertThat(appProperties.getAdmin().getEmail()).isNotBlank();
        assertThat(appProperties.getAdmin().getPassword()).isNotBlank();
        assertThat(appProperties.getJwt().getSecret()).isNotBlank();
        assertThat(appProperties.getJwt().getExpirationMs()).isPositive();
        assertThat(appProperties.getCors().getAllowedOrigins()).isNotEmpty();
    }

    @Test
    @DisplayName("DataInitializer seeded a user (test admin) and password hash is BCrypt")
    void dataInitializer_seededUserWithBcryptHash() {
        long count = userRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(1L);

        String seededUsername = appProperties.getAdmin().getUsername();
        var seededUser = userRepository.findByUsername(seededUsername)
                .orElseThrow(() -> new AssertionError("Seeded user not found: " + seededUsername));

        assertThat(seededUser.getEnabled()).isTrue();
        assertThat(seededUser.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches(appProperties.getAdmin().getPassword(),
                seededUser.getPasswordHash())).isTrue();
    }
}
