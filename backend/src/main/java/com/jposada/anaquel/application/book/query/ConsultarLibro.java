// by Jeremy Posada
package com.jposada.anaquel.application.book.query;

import com.jposada.anaquel.application.shared.Query;

public record ConsultarLibro(Long bookId) implements Query {
}
