package com.loginfeature.service;

import com.loginfeature.config.AppProperties;
import com.loginfeature.entity.User;
import com.loginfeature.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            AppProperties.Admin admin = appProperties.getAdmin();
            User defaultAdmin = new User(
                    admin.getUsername(),
                    admin.getEmail(),
                    passwordEncoder.encode(admin.getPassword()),
                    Boolean.TRUE
            );
            User saved = userRepository.save(defaultAdmin);
            log.info("Default admin user created: id={}, username={}, email={}",
                    saved.getId(), saved.getUsername(), saved.getEmail());
        } else {
            log.debug("Users table is not empty; skipping default admin seed. Total users: {}",
                    userRepository.count());
        }
    }
}
