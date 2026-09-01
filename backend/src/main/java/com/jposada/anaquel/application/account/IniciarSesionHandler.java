// by Jeremy Posada
package com.jposada.anaquel.application.account;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.security.JwtService;
import com.jposada.anaquel.web.dto.AuthResponse;
import com.jposada.anaquel.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IniciarSesionHandler implements UseCase<IniciarSesion, AuthResponse> {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional(readOnly = true)
    public AuthResponse handle(IniciarSesion comando) {
        // Mismo error para correo inexistente y clave incorrecta: no revelar quien tiene cuenta.
        AppUser usuario = userRepository.findByEmailIgnoreCase(comando.correo().trim())
                .orElseThrow(() -> new BadCredentialsException("Correo o contrasena incorrectos"));

        if (!passwordEncoder.matches(comando.contrasena(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Correo o contrasena incorrectos");
        }
        return new AuthResponse(jwtService.generateToken(usuario), "Bearer",
                jwtService.expirationSeconds(), UserResponse.from(usuario));
    }
}
