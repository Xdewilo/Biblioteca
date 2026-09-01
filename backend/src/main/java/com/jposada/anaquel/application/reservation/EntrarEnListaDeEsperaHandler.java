// by Jeremy Posada
package com.jposada.anaquel.application.reservation;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationQueue;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.domain.shared.UserBlockedException;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import com.jposada.anaquel.web.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntrarEnListaDeEsperaHandler
        implements UseCase<EntrarEnListaDeEspera, ReservationResponse> {

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final AppUserRepository userRepository;
    private final ReservationQueue queue;

    @Override
    @Transactional
    public ReservationResponse handle(EntrarEnListaDeEspera comando) {
        var quienPide = comando.quienPide();
        String correo = (comando.correoSolicitante() != null && !comando.correoSolicitante().isBlank()
                ? comando.correoSolicitante()
                : quienPide.getEmail()).trim().toLowerCase();

        if (quienPide.getRole() != Role.ADMIN && !correo.equalsIgnoreCase(quienPide.getEmail())) {
            throw new BusinessRuleException("FORBIDDEN_REQUESTER", HttpStatus.FORBIDDEN,
                    "Solo un ADMIN puede reservar a nombre de otra persona.");
        }

        Book libro = bookRepository.findById(comando.bookId())
                .orElseThrow(() -> new ResourceNotFoundException("Libro", comando.bookId()));

        if (libro.getStatus() == BookStatus.DISPONIBLE) {
            throw new BusinessRuleException("BOOK_ALREADY_AVAILABLE",
                    "'%s' esta disponible ahora mismo: pidelo prestado en vez de reservarlo."
                            .formatted(libro.getTitle()));
        }
        // Quien no puede pedir prestamos tampoco puede guardar puesto en la fila.
        userRepository.findByEmailIgnoreCase(correo).ifPresent(cuenta -> {
            if (cuenta.isBlocked()) {
                throw new UserBlockedException(cuenta.getEmail(), cuenta.getBlockedUntil());
            }
        });
        // Reservar el libro que uno mismo tiene prestado seria una renovacion encubierta.
        if (loanRepository.existsByBookIdAndBorrowerEmailIgnoreCaseAndReturnDateIsNull(libro.getId(), correo)) {
            throw new BusinessRuleException("ALREADY_BORROWED",
                    "Ya tienes '%s' en tus manos: devuelvelo antes de volver a pedirlo."
                            .formatted(libro.getTitle()));
        }
        if (reservationRepository.existsByBookIdAndRequesterEmailIgnoreCaseAndStatusIn(
                libro.getId(), correo, List.of(ReservationStatus.PENDIENTE, ReservationStatus.NOTIFICADO))) {
            throw new BusinessRuleException("DUPLICATE_RESERVATION",
                    "Ya estas en la lista de espera de '%s'.".formatted(libro.getTitle()));
        }

        Reservation guardada = reservationRepository.save(Reservation.builder()
                .book(libro)
                .requesterEmail(correo)
                .requestedAt(Instant.now())
                .status(ReservationStatus.PENDIENTE)
                .build());

        log.info("Reserva {} creada: libro={} para={}", guardada.getId(), libro.getIsbn(), correo);
        return ReservationResponse.from(guardada, queue.queuePosition(guardada), queue.holdHours());
    }
}
