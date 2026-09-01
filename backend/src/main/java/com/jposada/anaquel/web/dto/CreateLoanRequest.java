// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateLoanRequest", description = "Registro de un prestamo. Normalmente basta con el id del libro: el prestatario se toma del usuario autenticado. Solo un ADMIN puede prestar a nombre de otra persona.")
public record CreateLoanRequest(

        @Schema(description = "Id del libro. Debe estar DISPONIBLE", example = "1")
        @NotNull(message = "El id del libro es obligatorio")
        Long bookId,

        /** Opcionales: por defecto se toman del usuario autenticado. */
        @Schema(description = "Solo ADMIN. Por defecto, el nombre del usuario autenticado")
        @Size(max = 200) String borrowerName,
        @Schema(description = "Solo ADMIN. Por defecto, el correo del usuario autenticado")
        @Email(message = "El correo no tiene un formato valido") @Size(max = 200) String borrowerEmail
) {}
