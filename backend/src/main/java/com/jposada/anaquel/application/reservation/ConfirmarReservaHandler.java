// by Jeremy Posada
package com.jposada.anaquel.application.reservation;

import com.jposada.anaquel.application.loan.RegistrarPrestamo;
import com.jposada.anaquel.application.loan.RegistrarPrestamoHandler;
import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationQueue;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import com.jposada.anaquel.web.dto.LoanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfirmarReservaHandler implements UseCase<ConfirmarReserva, LoanResponse> {

    private final ReservationRepository reservationRepository;
    private final AppUserRepository userRepository;
    private final ReservationQueue queue;
    private final RegistrarPrestamoHandler registrarPrestamo;

    @Override
    @Transactional
    public LoanResponse handle(ConfirmarReserva comando) {
        Reservation reserva = reservationRepository.findById(comando.reservaId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva", comando.reservaId()));
        var quien = comando.quienConfirma();

        boolean esSuya = reserva.getRequesterEmail().equalsIgnoreCase(quien.getEmail());
        if (!esSuya && quien.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("FORBIDDEN_RESERVATION", HttpStatus.FORBIDDEN,
                    "Solo puedes confirmar tus propias reservas.");
        }
        if (reserva.getStatus() != ReservationStatus.NOTIFICADO) {
            throw new BusinessRuleException("RESERVATION_NOT_READY",
                    "Esa reserva todavia no tiene el turno (estado actual: %s)."
                            .formatted(reserva.getStatus()));
        }
        // El plazo se comprueba aqui y no solo en el cron horario.
        if (reserva.isHoldExpired(queue.holdHours())) {
            throw new BusinessRuleException("RESERVATION_EXPIRED",
                    "Tu turno para '%s' vencio el %s. Vuelve a entrar en la lista de espera."
                            .formatted(reserva.getBook().getTitle(), reserva.holdExpiresAt(queue.holdHours())));
        }

        // El prestamo queda a nombre de quien reservo, aunque lo confirme un ADMIN.
        String nombre = userRepository.findByEmailIgnoreCase(reserva.getRequesterEmail())
                .map(AppUser::getName)
                .orElse(quien.getName());

        return registrarPrestamo.handle(new RegistrarPrestamo(
                reserva.getBook().getId(), nombre, reserva.getRequesterEmail(), quien));
    }
}
