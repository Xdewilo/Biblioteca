// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "BookLookupResponse", description = "Previsualizacion traida de Open Library. NO persiste nada: alimenta el boton 'Autocompletar desde ISBN' del formulario de alta.")
public record BookLookupResponse(
        String isbn,
        String title,
        String author,
        Integer publicationYear,
        String coverUrl,
        List<String> subjects,
        @Schema(description = "De donde salieron los datos", example = "openlibrary.org")
        String source,

        @Schema(description = "true si ese ISBN ya existe en el catalogo: guardarlo daria 409")
        boolean alreadyRegistered
) {}
