// by Jeremy Posada
package com.jposada.anaquel.application.loan;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.loan.Loan;
import com.jposada.anaquel.domain.reservation.ReservationQueue;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.domain.shared.event.UserBlockedEvent;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.config.AppProperties;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.web.dto.LoanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class DevolverPrestamoHandler implements UseCase<DevolverPrestamo, LoanResponse> {

    private final LoanRepository loanRepository;
    private final AppUserRepository userRepository;
    private final ReservationQueue reservationQueue;
    private final ApplicationEventPublisher events;
    private final AppProperties rules;

    @Override
    @Transactional
    public LoanResponse handle(DevolverPrestamo comando) {
        Loan prestamo = loanRepository.findById(comando.prestamoId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestamo", comando.prestamoId()));
        var quienDevuelve = comando.quienDevuelve();

        boolean esSuyo = prestamo.getBorrowerEmail().equalsIgnoreCase(quienDevuelve.getEmail());
        if (!esSuyo && quienDevuelve.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("FORBIDDEN_LOAN", HttpStatus.FORBIDDEN,
                    "Solo puedes devolver tus propios prestamos.");
        }
        if (prestamo.isReturned()) {
            throw new BusinessRuleException("LOAN_ALREADY_RETURNED",
                    "El prestamo %d ya fue devuelto el %s."
                            .formatted(comando.prestamoId(), prestamo.getReturnDate()));
        }

        prestamo.setReturnDate(LocalDate.now());

        if (prestamo.isReturnedLate()) {
            registrarAtraso(prestamo);
        }

        // El libro no vuelve sin mas al catalogo: si alguien lo esperaba, le toca a el.
        reservationQueue.assignNextTurn(prestamo.getBook());

        log.info("Prestamo {} devuelto el {} (tarde={})",
                comando.prestamoId(), prestamo.getReturnDate(), prestamo.isReturnedLate());
        return LoanResponse.from(prestamo);
    }

    /** Al llegar al umbral dentro de la ventana se bloquea; si ya estaba bloqueada no se reinicia el reloj. */
    private void registrarAtraso(Loan prestamo) {
        LocalDate desde = LocalDate.now().minusDays(rules.lateReturnsWindowDays());
        long atrasos = loanRepository.countLateReturnsSince(prestamo.getBorrowerEmail(), desde);

        log.info("Devolucion tardia de {}: {} atrasos en los ultimos {} dias",
                prestamo.getBorrowerEmail(), atrasos, rules.lateReturnsWindowDays());

        if (atrasos < rules.maxLateReturns()) {
            return;
        }

        userRepository.findByEmailIgnoreCase(prestamo.getBorrowerEmail()).ifPresent(usuario -> {
            if (usuario.isBlocked()) {
                return;
            }
            Instant hasta = Instant.now().plus(Duration.ofDays(rules.blockDurationDays()));
            usuario.block(hasta, "%d devoluciones con atraso en los ultimos %d dias"
                    .formatted(atrasos, rules.lateReturnsWindowDays()));
            log.warn("Cuenta {} bloqueada hasta {} por {} atrasos", usuario.getEmail(), hasta, atrasos);
            events.publishEvent(new UserBlockedEvent(usuario.getId(), hasta, atrasos));
        });
    }
}
