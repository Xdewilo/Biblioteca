// by Jeremy Posada
package com.jposada.anaquel.domain.shared;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public class UserBlockedException extends BusinessException {

    public UserBlockedException(String email, Instant until) {
        super("USER_BLOCKED", HttpStatus.FORBIDDEN,
                "La cuenta %s esta bloqueada para pedir prestamos hasta %s.".formatted(email, until));
    }
}
