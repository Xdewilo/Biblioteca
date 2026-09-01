// by Jeremy Posada
package com.jposada.anaquel.application.admin;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LevantarBloqueoHandler implements UseCase<LevantarBloqueo, UserResponse> {

    private final AppUserRepository userRepository;

    @Override
    @Transactional
    public UserResponse handle(LevantarBloqueo comando) {
        AppUser usuario = userRepository.findById(comando.usuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", comando.usuarioId()));
        if (!usuario.isBlocked()) {
            throw new BusinessRuleException("NOT_BLOCKED",
                    "La cuenta %s no esta bloqueada.".formatted(usuario.getEmail()));
        }
        usuario.unblock();
        log.info("Bloqueo levantado manualmente para {}", usuario.getEmail());
        return UserResponse.from(usuario);
    }
}
