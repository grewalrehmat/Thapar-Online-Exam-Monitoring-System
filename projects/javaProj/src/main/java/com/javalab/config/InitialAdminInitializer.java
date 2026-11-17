package com.javalab.config;

import com.javalab.account.Role;
import com.javalab.account.UserAccount;
import com.javalab.account.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
public class InitialAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);
    private final Environment environment;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;

    public InitialAdminInitializer(Environment environment, UserAccountRepository users, PasswordEncoder encoder) {
        this.environment = environment;
        this.users = users;
        this.encoder = encoder;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        String email = environment.getProperty("JAVALAB_INITIAL_ADMIN_EMAIL", "").trim();
        String name = environment.getProperty("JAVALAB_INITIAL_ADMIN_NAME", "").trim();
        String password = environment.getProperty("JAVALAB_INITIAL_ADMIN_PASSWORD", "");
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || name.isBlank() || password.length() < 12) {
            log.error("Initial admin was not created: provide JAVALAB_INITIAL_ADMIN_NAME, JAVALAB_INITIAL_ADMIN_EMAIL, and a password of at least 12 characters.");
            return;
        }
        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) log.error("Initial admin email already belongs to a non-admin account; no account was changed.");
            return;
        }
        users.save(new UserAccount(name, email, encoder.encode(password), Role.ADMIN, null));
        log.info("Initial administrator account created from environment configuration.");
    }
}
