// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Formato unico de error de toda la API. Lo produce el @RestControllerAdvice, incluidos los 401 y 403 que Spring Security genera antes de llegar al controlador.")
public record ApiError(

        @Schema(description = "Momento del error", example = "2026-08-18T00:56:54.974Z")
        Instant timestamp,
        @Schema(description = "Codigo HTTP", example = "409")
        int status,

        @Schema(description = "Codigo estable de negocio. El frontend reacciona a ESTO, no al texto del mensaje", example = "BOOK_NOT_AVAILABLE")
        String code,

        @Schema(description = "Mensaje legible para la persona", example = "El libro '1984' no esta disponible para prestamo (estado actual: PRESTADO).")
        String message,

        @Schema(description = "Ruta que se invoco", example = "/api/loans")
        String path,

        @Schema(description = "Solo en errores de validacion: el detalle campo por campo")
        List<FieldViolation> errors
) {
    public record FieldViolation(String field, String message) {}

    public static ApiError of(int status, String code, String message, String path) {
        return new ApiError(Instant.now(), status, code, message, path, null);
    }

    public static ApiError of(int status, String code, String message, String path, List<FieldViolation> errors) {
        return new ApiError(Instant.now(), status, code, message, path, errors);
    }
}
