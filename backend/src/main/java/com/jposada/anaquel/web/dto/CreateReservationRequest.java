// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record CreateReservationRequest(
        @NotNull(message = "El id del libro es obligatorio") Long bookId,
        /** Opcional: por defecto el correo del usuario autenticado. */
        @Email String requesterEmail
) {}
