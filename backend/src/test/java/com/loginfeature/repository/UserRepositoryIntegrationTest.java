package com.loginfeature.repository;

import com.loginfeature.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = new User("alice", "alice@example.com", "$2a$10$dummyHashForTest1234567890abcdefghijklmnopqrstuvwxyz");
        alice.setEnabled(Boolean.TRUE);
        alice = entityManager.persistAndFlush(alice);
    }

    @Test
    @DisplayName("findByUsername returns existing user when username matches")
    void findByUsername_returnsUser_whenUsernameExists() {
        Optional<User> found = userRepository.findByUsername("alice");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(alice.getId());
        assertThat(found.get().getUsername()).isEqualTo("alice");
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(found.get().getPasswordHash()).startsWith("$2a$10$");
        assertThat(found.get().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("findByUsername returns empty when username does not exist")
    void findByUsername_returnsEmpty_whenUsernameMissing() {
        Optional<User> found = userRepository.findByUsername("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByEmail returns existing user when email matches")
    void findByEmail_returnsUser_whenEmailExists() {
        Optional<User> found = userRepository.findByEmail("alice@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(found.get().getId()).isEqualTo(alice.getId());
    }

    @Test
    @DisplayName("findByEmail returns empty when email does not exist")
    void findByEmail_returnsEmpty_whenEmailMissing() {
        Optional<User> found = userRepository.findByEmail("missing@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByUsernameOrEmail returns user by username (first arg)")
    void findByUsernameOrEmail_returnsUser_whenUsernameMatchesFirstArg() {
        Optional<User> found = userRepository.findByUsernameOrEmail("alice", "anything@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(alice.getId());
    }

    @Test
    @DisplayName("findByUsernameOrEmail returns user by email (second arg)")
    void findByUsernameOrEmail_returnsUser_whenEmailMatchesSecondArg() {
        Optional<User> found = userRepository.findByUsernameOrEmail("unknown", "alice@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(alice.getId());
    }

    @Test
    @DisplayName("findByUsernameOrEmail returns empty when neither matches")
    void findByUsernameOrEmail_returnsEmpty_whenNoMatch() {
        Optional<User> found = userRepository.findByUsernameOrEmail("bob", "bob@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("username unique constraint throws DataIntegrityViolationException on duplicate")
    void save_throwsException_whenUsernameIsDuplicate() {
        User duplicate = new User("alice", "other@example.com", "$2a$10$hash2");

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("email unique constraint throws DataIntegrityViolationException on duplicate")
    void save_throwsException_whenEmailIsDuplicate() {
        User duplicate = new User("other", "alice@example.com", "$2a$10$hash3");

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("enabled flag defaults to true and persists correctly")
    void save_persistsEnabledFlagCorrectly() {
        User disabled = new User("carol", "carol@example.com", "$2a$10$hash4", Boolean.FALSE);
        User saved = userRepository.saveAndFlush(disabled);
        entityManager.clear();

        Optional<User> reloaded = userRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("createdAt and updatedAt are auto-populated on persist")
    void save_populatesAuditTimestamps() {
        assertThat(alice.getCreatedAt()).isNotNull();
        assertThat(alice.getUpdatedAt()).isNotNull();
        assertThat(alice.getUpdatedAt()).isAfterOrEqualTo(alice.getCreatedAt());
    }
}
