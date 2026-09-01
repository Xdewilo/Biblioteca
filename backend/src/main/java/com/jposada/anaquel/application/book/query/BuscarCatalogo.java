// by Jeremy Posada
package com.jposada.anaquel.application.book.query;

import com.jposada.anaquel.application.shared.Query;
import com.jposada.anaquel.domain.book.BookStatus;
import org.springframework.data.domain.Pageable;

public record BuscarCatalogo(String texto, BookStatus estado, Pageable paginacion) implements Query {
}
