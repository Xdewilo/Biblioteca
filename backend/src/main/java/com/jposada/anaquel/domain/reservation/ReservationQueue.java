// by Jeremy Posada
package com.jposada.anaquel.domain.reservation;

import com.jposada.anaquel.infrastructure.config.AppProperties;
import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.shared.event.BookAvailableEvent;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Unico sitio que decide a quien le toca un libro: el primero de la fila que pueda llevarselo; los bloqueados se saltan sin perder puesto. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationQueue {

    private final ReservationRepository reservationRepository;
    private final LoanRepository loanRepository;
    private final AppUserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final AppProperties rules;

    public Optional<Reservation> assignNextTurn(Book book) {
        // Un libro aun prestado no se reparte (protege contra un turno caducado sobre un libro ya vuelto a prestar).
        if (loanRepository.existsByBookIdAndReturnDateIsNull(book.getId())) {
            log.warn("'{}' tiene un prestamo activo: no se reasigna el turno hasta que lo devuelvan",
                    book.getTitle());
            return Optional.empty();
        }
        List<Reservation> fila = reservationRepository.findQueueForBook(book.getId());

        for (Reservation candidata : fila) {
            if (!puedeTomarlo(candidata.getRequesterEmail())) {
                log.info("Se salta a {} en la fila de '{}': su cuenta esta bloqueada",
                        candidata.getRequesterEmail(), book.getTitle());
                continue;   // conserva su puesto para la proxima vuelta
            }

            candidata.setStatus(ReservationStatus.NOTIFICADO);
            candidata.setNotifiedAt(Instant.now());
            book.setStatus(BookStatus.RESERVADO);

            log.info("'{}' queda reservado para {} durante {} horas",
                    book.getTitle(), candidata.getRequesterEmail(), rules.reservationHoldHours());
            events.publishEvent(new BookAvailableEvent(candidata.getId()));
            return Optional.of(candidata);
        }

        // Nadie en la fila puede llevarselo ahora mismo: vuelve al catalogo.
        book.setStatus(BookStatus.DISPONIBLE);
        if (!fila.isEmpty()) {
            log.info("Nadie de la fila de '{}' puede tomarlo ahora; el libro vuelve a DISPONIBLE",
                    book.getTitle());
        }
        return Optional.empty();
    }

    /** Una cuenta bloqueada por atrasos no puede recibir el turno: se le saltaria igual al pedirlo. */
    private boolean puedeTomarlo(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(user -> !user.isBlocked())
                .orElse(true);   // correo sin cuenta registrada: se le deja el turno
    }

    /** Puesto en la fila (1 = el siguiente); null si ya no espera. */
    public Integer queuePosition(Reservation reserva) {
        if (!reserva.isWaiting()) {
            return null;
        }
        if (reserva.getStatus() == ReservationStatus.NOTIFICADO) {
            return 1;
        }
        List<Reservation> fila = reservationRepository.findQueueForBook(reserva.getBook().getId());
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).getId().equals(reserva.getId())) {
                return i + 1;
            }
        }
        return null;
    }

    /** Quien fue notificado tiene este plazo para confirmar el prestamo. */
    public int holdHours() {
        return rules.reservationHoldHours();
    }

    public Optional<Reservation> currentHold(Long bookId, String email) {
        return reservationRepository
                .findFirstByBookIdAndRequesterEmailIgnoreCaseAndStatusOrderByRequestedAtAsc(
                        bookId, email, ReservationStatus.NOTIFICADO);
    }
}
