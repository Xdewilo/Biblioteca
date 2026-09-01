// by Jeremy Posada
package com.jposada.anaquel.infrastructure.config;

import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cuentas de prueba por codigo y no en SQL: asi no se versiona ningun hash de contrasena. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private static final String LOCAL_DEV_PASSWORD = "Admin123*";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled:true}")
    private boolean enabled;

    @Value("${app.seed.admin-email:admin@anaquel.app}")
    private String adminEmail;

    @Value("${app.seed.admin-password:" + LOCAL_DEV_PASSWORD + "}")
    private String adminPassword;

    @Value("${app.seed.librarian-email:lectura@anaquel.app}")
    private String librarianEmail;

    @Value("${app.seed.librarian-password:" + LOCAL_DEV_PASSWORD + "}")
    private String librarianPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Seeding de cuentas desactivado (app.seed.enabled=false)");
            return;
        }

        seed(adminEmail, "Administrador", adminPassword, Role.ADMIN);
        seed(librarianEmail, "Bibliotecario", librarianPassword, Role.BIBLIOTECARIO);

        if (LOCAL_DEV_PASSWORD.equals(adminPassword)) {
            log.warn("""
                    ================================================================
                     La cuenta ADMIN se creo con la contrasena por defecto de
                     DESARROLLO. Define SEED_ADMIN_PASSWORD antes de exponer
                     esta aplicacion fuera de tu maquina.
                    ================================================================""");
        }
    }

    private void seed(String email, String name, String rawPassword, Role role) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        userRepository.save(AppUser.builder()
                .name(name)
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .build());
        log.info("Cuenta de prueba creada: {} ({})", email, role);
    }
}
