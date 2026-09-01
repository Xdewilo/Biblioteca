// by Jeremy Posada
package com.jposada.anaquel.application.account;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.shared.EmailAlreadyUsedException;
import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.security.JwtService;
import com.jposada.anaquel.web.dto.AuthResponse;
import com.jposada.anaquel.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrearCuentaHandler implements UseCase<CrearCuenta, AuthResponse> {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse handle(CrearCuenta comando) {
        String correo = comando.correo().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(correo)) {
            throw new EmailAlreadyUsedException(correo);
        }

        AppUser guardado = userRepository.save(AppUser.builder()
                .name(comando.nombre().trim())
                .email(correo)
                .passwordHash(passwordEncoder.encode(comando.contrasena()))
                // El registro publico siempre crea BIBLIOTECARIO; los ADMIN salen del seeding.
                .role(Role.BIBLIOTECARIO)
                .build());

        log.info("Cuenta creada: {} ({})", guardado.getEmail(), guardado.getRole());
        return new AuthResponse(jwtService.generateToken(guardado), "Bearer",
                jwtService.expirationSeconds(), UserResponse.from(guardado));
    }
}
