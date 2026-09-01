// by Jeremy Posada
package com.jposada.anaquel.application.admin.query;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultarCuentasHandler implements UseCase<ConsultarCuentas, List<UserResponse>> {

    private final AppUserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> handle(ConsultarCuentas consulta) {
        var cuentas = consulta.soloBloqueadas()
                ? userRepository.findCurrentlyBlocked(Instant.now())
                : userRepository.findAll();
        return cuentas.stream().map(UserResponse::from).toList();
    }
}
