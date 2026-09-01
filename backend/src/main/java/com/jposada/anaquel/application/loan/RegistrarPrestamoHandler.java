// by Jeremy Posada
package com.jposada.anaquel.application.loan;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.loan.Loan;
import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationQueue;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.domain.shared.BookNotAvailableException;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.domain.shared.UserBlockedException;
import com.jposada.anaquel.domain.shared.event.LoanCreatedEvent;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.config.AppProperties;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.web.dto.LoanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrarPrestamoHandler implements UseCase<RegistrarPrestamo, LoanResponse> {

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final AppUserRepository userRepository;
    private final ReservationQueue reservationQueue;
    private final ApplicationEventPublisher events;
    private final AppProperties rules;

    @Override
    @Transactional
    public LoanResponse handle(RegistrarPrestamo comando) {
        var quienPide = comando.quienPide();

        String correo = normalizar(comando.prestatarioCorreo() != null && !comando.prestatarioCorreo().isBlank()
                ? comando.prestatarioCorreo()
                : quienPide.getEmail());
        String nombre = comando.prestatarioNombre() != null && !comando.prestatarioNombre().isBlank()
                ? comando.prestatarioNombre().trim()
                : quienPide.getName();

        // Solo un ADMIN puede prestar a nombre ajeno.
        if (quienPide.getRole() != Role.ADMIN && !correo.equalsIgnoreCase(quienPide.getEmail())) {
            throw new BusinessRuleException("FORBIDDEN_BORROWER", HttpStatus.FORBIDDEN,
                    "Solo un ADMIN puede registrar prestamos a nombre de otra persona.");
        }

        exigirCuentaHabilitada(correo);

        // Lock pesimista: dos peticiones simultaneas no pueden llevarse el mismo ejemplar.
        Book libro = bookRepository.findByIdForUpdate(comando.bookId())
                .orElseThrow(() -> new ResourceNotFoundException("Libro", comando.bookId()));

        Optional<Reservation> turnoUsado = exigirLibroPrestable(libro, correo);

        LocalDate hoy = LocalDate.now();
        Loan prestamo = Loan.builder()
                .book(libro)
                .borrowerName(nombre)
                .borrowerEmail(correo)
                .loanDate(hoy)
                .dueDate(hoy.plusDays(rules.loanPeriodDays()))
                .build();

        libro.setStatus(BookStatus.PRESTADO);
        // Si tenia el turno, la reserva se cierra aqui (el libro puede estar ya DISPONIBLE por carrera con el scheduler).
        turnoUsado.or(() -> reservationQueue.currentHold(libro.getId(), correo))
                .ifPresent(reserva -> reserva.setStatus(ReservationStatus.CUMPLIDO));

        Loan guardado = loanRepository.save(prestamo);
        log.info("Prestamo {} registrado: libro={} para={} vence={}",
                guardado.getId(), libro.getIsbn(), correo, guardado.getDueDate());

        // El correo se envia tras el commit y en otro hilo: la peticion HTTP no espera al SMTP.
        events.publishEvent(new LoanCreatedEvent(guardado.getId()));

        return LoanResponse.from(guardado);
    }

    private void exigirCuentaHabilitada(String correo) {
        userRepository.findByEmailIgnoreCase(correo).ifPresent(usuario -> {
            if (usuario.isBlocked()) {
                throw new UserBlockedException(usuario.getEmail(), usuario.getBlockedUntil());
            }
        });
    }

    /** DISPONIBLE lo puede tomar cualquiera; RESERVADO, solo quien tiene el turno. */
    private Optional<Reservation> exigirLibroPrestable(Book libro, String correo) {
        return switch (libro.getStatus()) {
            case DISPONIBLE -> Optional.empty();
            case RESERVADO -> Optional.of(reservationQueue.currentHold(libro.getId(), correo)
                    .orElseThrow(() -> new BookNotAvailableException(
                            "El libro '%s' esta reservado para otra persona de la lista de espera."
                                    .formatted(libro.getTitle()))));
            case PRESTADO -> throw BookNotAvailableException.of(libro.getTitle(), libro.getStatus().name());
        };
    }

    private String normalizar(String correo) {
        return correo == null ? null : correo.trim().toLowerCase();
    }
}
