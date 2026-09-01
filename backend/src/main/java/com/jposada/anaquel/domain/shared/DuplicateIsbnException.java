// by Jeremy Posada
package com.jposada.anaquel.domain.shared;

import org.springframework.http.HttpStatus;

public class DuplicateIsbnException extends BusinessException {

    public DuplicateIsbnException(String isbn) {
        super("DUPLICATE_ISBN", HttpStatus.CONFLICT,
                "Ya existe un libro registrado con el ISBN %s.".formatted(isbn));
    }
}
