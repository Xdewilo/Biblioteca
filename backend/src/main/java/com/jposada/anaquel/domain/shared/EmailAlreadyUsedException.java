// by Jeremy Posada
package com.jposada.anaquel.domain.shared;

import org.springframework.http.HttpStatus;

public class EmailAlreadyUsedException extends BusinessException {

    public EmailAlreadyUsedException(String email) {
        super("EMAIL_ALREADY_USED", HttpStatus.CONFLICT,
                "Ya existe una cuenta registrada con el correo %s.".formatted(email));
    }
}
