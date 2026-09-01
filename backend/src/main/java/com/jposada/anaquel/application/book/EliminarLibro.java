// by Jeremy Posada
package com.jposada.anaquel.application.book;

import com.jposada.anaquel.application.shared.Command;

public record EliminarLibro(Long bookId) implements Command {
}
