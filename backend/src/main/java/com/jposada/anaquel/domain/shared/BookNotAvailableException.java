// by Jeremy Posada
package com.jposada.anaquel.domain.shared;

import org.springframework.http.HttpStatus;

public class BookNotAvailableException extends BusinessException {

    public BookNotAvailableException(String message) {
        super("BOOK_NOT_AVAILABLE", HttpStatus.CONFLICT, message);
    }

    public static BookNotAvailableException of(String title, String status) {
        return new BookNotAvailableException(
                "El libro '%s' no esta disponible para prestamo (estado actual: %s).".formatted(title, status));
    }
}
