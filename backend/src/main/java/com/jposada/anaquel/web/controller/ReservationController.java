// by Jeremy Posada
package com.jposada.anaquel.web.controller;

import com.jposada.anaquel.infrastructure.security.CurrentUser;
import com.jposada.anaquel.application.reservation.CancelarReserva;
import com.jposada.anaquel.application.reservation.CancelarReservaHandler;
import com.jposada.anaquel.application.reservation.ConfirmarReserva;
import com.jposada.anaquel.application.reservation.ConfirmarReservaHandler;
import com.jposada.anaquel.application.reservation.EntrarEnListaDeEspera;
import com.jposada.anaquel.application.reservation.EntrarEnListaDeEsperaHandler;
import com.jposada.anaquel.application.reservation.query.MisReservas;
import com.jposada.anaquel.application.reservation.query.MisReservasHandler;
import com.jposada.anaquel.web.dto.CreateReservationRequest;
import com.jposada.anaquel.web.dto.LoanResponse;
import com.jposada.anaquel.web.dto.ReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.jposada.anaquel.web.dto.ApiError;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Lista de espera", description = "Reservas sobre libros prestados")
public class ReservationController {

    private final EntrarEnListaDeEsperaHandler entrarEnListaDeEspera;
    private final ConfirmarReservaHandler confirmarReserva;
    private final CancelarReservaHandler cancelarReserva;
    private final MisReservasHandler misReservas;

    @PostMapping
    @Operation(summary = "Entra a la lista de espera de un libro prestado")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Estas en la fila. queuePosition indica tu puesto; te llegara un correo cuando lo devuelvan",
                content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Falta el bookId",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN_REQUESTER - solo un ADMIN reserva a nombre de otra persona",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - no existe un libro con ese id",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "BOOK_ALREADY_AVAILABLE (esta libre: pidelo prestado) o DUPLICATE_RESERVATION (ya estabas en la fila)",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(
                        request.bookId(), request.requesterEmail(), CurrentUser.require())));
    }

    @GetMapping("/mine")
    @Operation(summary = "Reservas del usuario autenticado, con su posicion en la fila")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tus reservas con su estado y tu puesto en la fila"),
        @ApiResponse(responseCode = "401", description = "Falta el token",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<ReservationResponse> mine() {
        return misReservas.handle(new MisReservas(CurrentUser.require()));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirma en un solo paso el prestamo del libro que te estaban guardando",
            description = "Solo funciona si la reserva esta NOTIFICADO, es decir, si ya te toca el turno.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Prestamo creado a tu nombre: el libro pasa a PRESTADO y vence en 14 dias",
                content = @Content(schema = @Schema(implementation = LoanResponse.class))),
        @ApiResponse(responseCode = "403", description = "USER_BLOCKED (tu cuenta esta bloqueada) o FORBIDDEN_RESERVATION (no es tu reserva)",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - no existe una reserva con ese id",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "RESERVATION_NOT_READY - todavia no te toca el turno",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<LoanResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(confirmarReserva.handle(new ConfirmarReserva(id, CurrentUser.require())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancela una reserva. Si tenia el turno, pasa al siguiente de la fila")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Reserva cancelada. Si tenias el turno, pasa al siguiente de la fila; si no queda nadie, el libro vuelve a DISPONIBLE"),
        @ApiResponse(responseCode = "403", description = "FORBIDDEN_RESERVATION - solo puedes cancelar tus propias reservas",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "NOT_FOUND - no existe una reserva con ese id",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "RESERVATION_NOT_CANCELABLE - ya estaba CANCELADO o CUMPLIDO",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        cancelarReserva.handle(new CancelarReserva(id, CurrentUser.require()));
        return ResponseEntity.noContent().build();
    }
}
