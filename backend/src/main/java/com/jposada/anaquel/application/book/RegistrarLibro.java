// by Jeremy Posada
package com.jposada.anaquel.application.book;

import com.jposada.anaquel.application.shared.Command;

import java.util.List;

public record RegistrarLibro(
        String isbn,
        String titulo,
        String autor,
        Integer anio,
        String portada,
        List<String> temas,
        boolean autocompletar
) implements Command {
}
