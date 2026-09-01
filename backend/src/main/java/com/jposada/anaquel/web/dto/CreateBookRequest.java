// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Solo el ISBN es obligatorio; el resto se completa desde Open Library o a mano. */
@Schema(name = "CreateBookRequest", description = "Alta de un libro. Lo unico obligatorio es el ISBN: si dejas titulo, autor o anio vacios se completan desde Open Library. Si la API externa falla, el libro se guarda igual con lo que hayas escrito a mano.")
public record CreateBookRequest(

        @Schema(description = "Con o sin guiones; se normaliza al guardar", example = "978-0-13-235088-4")
        @NotBlank(message = "El ISBN es obligatorio")
        @Pattern(regexp = "^[0-9Xx\\- ]{10,20}$", message = "El ISBN debe tener 10 o 13 digitos")
        String isbn,

        @Schema(description = "Opcional: si va vacio se toma de Open Library", example = "Clean Code")
        @Size(max = 300)
        String title,

        @Schema(description = "Opcional: si va vacio se toma de Open Library", example = "Robert C. Martin")
        @Size(max = 300)
        String author,

        @Schema(description = "Opcional: si va vacio se toma de Open Library", example = "2008")
        @Min(value = 1450, message = "El anio de publicacion no es valido")
        @Max(value = 2100, message = "El anio de publicacion no es valido")
        Integer publicationYear,

        @Schema(description = "Opcional: normalmente lo aporta Open Library")
        String coverUrl,

        @Schema(description = "Temas o materias. Si va vacio se toman de Open Library")
        List<String> subjects,

        @Schema(description = "false = no consultar Open Library aunque falten datos. Por defecto true", example = "true", defaultValue = "true")
        Boolean autocomplete
) {
    public boolean autocompleteEnabled() {
        return autocomplete == null || autocomplete;
    }
}
