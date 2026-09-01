// by Jeremy Posada
package com.jposada.anaquel.application.admin.query;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.infrastructure.config.AppProperties;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import com.jposada.anaquel.web.dto.StatsResponse;
import com.jposada.anaquel.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class EstadisticasHandler implements UseCase<Estadisticas, StatsResponse> {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final AppUserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final AppProperties rules;

    @Override
    @Transactional(readOnly = true)
    public StatsResponse handle(Estadisticas consulta) {
        LocalDate hoy = LocalDate.now();
        Instant ahora = Instant.now();

        return new StatsResponse(
                bookRepository.count(),
                bookRepository.countByStatus(BookStatus.DISPONIBLE),
                bookRepository.countByStatus(BookStatus.PRESTADO),
                bookRepository.countByStatus(BookStatus.RESERVADO),
                loanRepository.countActive(),
                loanRepository.countOverdue(hoy),
                loanRepository.countDueSoon(hoy, hoy.plusDays(rules.reminderDaysBefore())),
                userRepository.count(),
                userRepository.countCurrentlyBlocked(ahora),
                reservationRepository.countByStatus(ReservationStatus.PENDIENTE),
                userRepository.findCurrentlyBlocked(ahora).stream().map(UserResponse::from).toList());
    }
}
