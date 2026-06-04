package com.nt.config;

import com.nt.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Runs once on every startup.
 * Any user whose stored password is plain-text (does not start with the BCrypt
 * prefix "$2a$" or "$2b$") gets their password hashed in-place.
 * This handles: the data.sql seed user, manually created users, and any
 * installation upgraded from the plain-text era.
 */
@Component
@Order(1)
public class PasswordMigrationRunner implements CommandLineRunner {

    @Autowired private UserRepository userRepo;
    @Autowired private PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        userRepo.findAll().forEach(user -> {
            String pwd = user.getPassword();
            if (pwd != null && !pwd.startsWith("$2a$") && !pwd.startsWith("$2b$")) {
                user.setPassword(encoder.encode(pwd));
                userRepo.save(user);
            }
        });
    }
}
