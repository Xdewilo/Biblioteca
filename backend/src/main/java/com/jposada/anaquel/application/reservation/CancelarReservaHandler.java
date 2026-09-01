// by Jeremy Posada
package com.jposada.anaquel.application.reservation;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationQueue;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CancelarReservaHandler implements UseCase<CancelarReserva, Void> {

    private final ReservationRepository reservationRepository;
    private final ReservationQueue queue;

    @Override
    @Transactional
    public Void handle(CancelarReserva comando) {
        Reservation reserva = reservationRepository.findById(comando.reservaId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", comando.reservaId()));
        var quien = comando.quienCancela();

        boolean esSuya = reserva.getRequesterEmail().equalsIgnoreCase(quien.getEmail());
        if (!esSuya && quien.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("FORBIDDEN_RESERVATION", HttpStatus.FORBIDDEN,
                    "Solo puedes cancelar tus propias reservas.");
        }
        if (reserva.getStatus() == ReservationStatus.CANCELADO
                || reserva.getStatus() == ReservationStatus.CUMPLIDO) {
            throw new BusinessRuleException("RESERVATION_NOT_CANCELABLE",
                    "La reserva %d ya esta en estado %s."
                            .formatted(comando.reservaId(), reserva.getStatus()));
        }

        boolean teniaElTurno = reserva.getStatus() == ReservationStatus.NOTIFICADO;
        reserva.setStatus(ReservationStatus.CANCELADO);

        // Si tenia el libro apartado, el turno pasa al siguiente en vez de quedarse atascado.
        if (teniaElTurno && reserva.getBook().getStatus() == BookStatus.RESERVADO) {
            queue.assignNextTurn(reserva.getBook());
        }
        log.info("Reserva {} cancelada por {}", comando.reservaId(), quien.getEmail());
        return null;
    }
}
