// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ReservationResponse", description =
        "Un puesto en la lista de espera. Cuando el estado pasa a NOTIFICADO el libro queda "
        + "guardado a tu nombre hasta holdExpiresAt; pasado ese plazo el turno pasa al siguiente.")
public record ReservationResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String requesterEmail,
        Instant requestedAt,

        @Schema(description = "PENDIENTE -> NOTIFICADO (te toca) -> CUMPLIDO o CANCELADO")
        ReservationStatus status,

        @Schema(description = "Cuando se te aviso de que el libro quedo libre")
        Instant notifiedAt,

        @Schema(description = "Hasta cuando se te guarda el libro. null si aun no te toca")
        Instant holdExpiresAt,

        @Schema(description = "true si ya puedes confirmar el prestamo en un solo paso")
        boolean readyToConfirm,

        @Schema(description = "Puesto en la fila: 1 es el siguiente. null si ya no esta en espera",
                example = "1")
        Integer queuePosition
) {
    public static ReservationResponse from(Reservation r, Integer queuePosition, int holdHours) {
        return new ReservationResponse(
                r.getId(), r.getBook().getId(), r.getBook().getTitle(), r.getRequesterEmail(),
                r.getRequestedAt(), r.getStatus(), r.getNotifiedAt(),
                r.holdExpiresAt(holdHours),
                r.getStatus() == ReservationStatus.NOTIFICADO,
                queuePosition);
    }
}
