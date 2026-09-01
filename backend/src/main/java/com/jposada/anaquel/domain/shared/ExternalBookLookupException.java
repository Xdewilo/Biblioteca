// by Jeremy Posada
package com.jposada.anaquel.domain.shared;

import org.springframework.http.HttpStatus;

/** Solo se lanza en la previsualizacion; al registrar un libro nunca se propaga. */
public class ExternalBookLookupException extends BusinessException {

    public ExternalBookLookupException(String isbn) {
        super("EXTERNAL_LOOKUP_FAILED", HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo consultar Open Library para el ISBN %s. Puedes registrar el libro a mano."
                        .formatted(isbn));
    }

    public ExternalBookLookupException(String isbn, Throwable cause) {
        this(isbn);
        initCause(cause);
    }
}
