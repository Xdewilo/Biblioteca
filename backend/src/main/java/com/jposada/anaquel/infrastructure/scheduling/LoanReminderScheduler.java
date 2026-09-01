// by Jeremy Posada
package com.jposada.anaquel.infrastructure.scheduling;

import com.jposada.anaquel.infrastructure.config.AppProperties;
import com.jposada.anaquel.domain.loan.Loan;
import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import com.jposada.anaquel.domain.reservation.ReservationQueue;
import com.jposada.anaquel.infrastructure.mail.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Los avisos se marcan como enviados solo si el SMTP acepto el mensaje; si no, se reintentan en la siguiente ejecucion. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoanReminderScheduler {

    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationQueue reservationQueue;
    private final NotificationService notificationService;
    private final AppProperties rules;

    /** Todos los dias a las 8:00: prestamos que vencen dentro de 1 o 2 dias. */
    @Scheduled(cron = "${app.scheduling.reminder-cron:0 0 8 * * *}")
    @Transactional
    public void sendDueSoonReminders() {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(rules.reminderDaysBefore());

        List<Loan> loans = loanRepository.findDueSoonWithoutReminder(today, until);
        if (loans.isEmpty()) {
            log.debug("Recordatorios: no hay prestamos por vencer entre {} y {}", today, until);
            return;
        }

        log.info("Recordatorios: {} prestamo(s) por vencer entre {} y {}", loans.size(), today, until);
        for (Loan loan : loans) {
            if (notificationService.sendDueSoonReminder(loan.getId())) {
                loan.setReminderSentAt(Instant.now());
            } else {
                log.warn("Recordatorio del prestamo {} no enviado; se reintentara manana", loan.getId());
            }
        }
    }

    /** Todos los dias a las 8:30: prestamos ya vencidos que aun no se avisaron. */
    @Scheduled(cron = "${app.scheduling.overdue-cron:0 30 8 * * *}")
    @Transactional
    public void sendOverdueNotices() {
        LocalDate today = LocalDate.now();

        List<Loan> loans = loanRepository.findOverdueWithoutNotice(today);
        if (loans.isEmpty()) {
            log.debug("Vencidos: no hay prestamos vencidos sin avisar");
            return;
        }

        log.info("Vencidos: {} prestamo(s) vencidos sin avisar", loans.size());
        for (Loan loan : loans) {
            if (notificationService.sendOverdueNotice(loan.getId())) {
                loan.setOverdueNoticeSentAt(Instant.now());
            } else {
                log.warn("Aviso de vencido del prestamo {} no enviado; se reintentara manana", loan.getId());
            }
        }
    }

    /** Cada hora: libera los libros guardados para alguien que nunca confirmo el prestamo. */
    @Scheduled(cron = "${app.scheduling.expire-holds-cron:0 5 * * * *}")
    @Transactional
    public void expireStaleHolds() {
        Instant limite = Instant.now().minus(Duration.ofHours(rules.reservationHoldHours()));
        List<Reservation> caducadas = reservationRepository.findExpiredHolds(limite);

        if (caducadas.isEmpty()) {
            return;
        }
        log.info("Turnos caducados: {} reserva(s) llevan mas de {} h sin confirmarse",
                caducadas.size(), rules.reservationHoldHours());

        for (Reservation caducada : caducadas) {
            caducada.setStatus(ReservationStatus.CANCELADO);
            log.info("Caduca el turno de {} sobre '{}'",
                    caducada.getRequesterEmail(), caducada.getBook().getTitle());
            reservationQueue.assignNextTurn(caducada.getBook());
        }
    }
}
