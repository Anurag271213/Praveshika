package com.loginfeature.security;

import com.loginfeature.entity.User;
import com.loginfeature.exception.AccountDisabledException;
import com.loginfeature.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        Optional<User> optional = userRepository.findByUsernameOrEmail(identifier, identifier);
        User user = optional.orElseThrow(() ->
                new UsernameNotFoundException("User not found: " + redact(identifier)));

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new AccountDisabledException("Account is disabled: " + redact(identifier));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getEnabled()),
                true,
                true,
                true,
                new ArrayList<>()
        );
    }

    public UserDetails loadUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User id not found: " + userId));

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new AccountDisabledException("Account is disabled for user id: " + userId);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                Boolean.TRUE.equals(user.getEnabled()),
                true,
                true,
                true,
                new ArrayList<>()
        );
    }

    private static String redact(String identifier) {
        if (identifier == null || identifier.length() <= 2) return "***";
        return identifier.charAt(0) + "***" + identifier.charAt(identifier.length() - 1);
    }
}
