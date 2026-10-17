package com.shbean.user;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@Order(1)
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DemoProperties demo;

    public DemoUserSeeder(AppUserRepository users, PasswordEncoder passwordEncoder, DemoProperties demo) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demo.email() == null || demo.email().isBlank() || demo.password() == null || demo.password().isBlank()) {
            log.warn("DEMO_USER_EMAIL/DEMO_USER_PASSWORD not set; skipping demo user seeding");
            return;
        }
        String email = demo.email().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            return;
        }
        users.save(new AppUser(email, passwordEncoder.encode(demo.password())));
        log.info("Seeded demo user {}", email);
    }
}
