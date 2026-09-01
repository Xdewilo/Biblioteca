// by Jeremy Posada
package com.jposada.anaquel.infrastructure.mail;

import com.jposada.anaquel.domain.shared.event.BookAvailableEvent;
import com.jposada.anaquel.domain.shared.event.LoanCreatedEvent;
import com.jposada.anaquel.domain.shared.event.UserBlockedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Los correos salen despues del commit y en otro hilo; nunca por una transaccion con rollback. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoanCreated(LoanCreatedEvent event) {
        log.debug("Procesando LoanCreatedEvent para el prestamo {}", event.loanId());
        notificationService.sendLoanConfirmation(event.loanId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserBlocked(UserBlockedEvent event) {
        log.debug("Procesando UserBlockedEvent para el usuario {}", event.userId());
        notificationService.sendAccountBlockedNotice(event.userId(), event.blockedUntil(), event.lateReturns());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookAvailable(BookAvailableEvent event) {
        log.debug("Procesando BookAvailableEvent para la reserva {}", event.reservationId());
        notificationService.sendBookAvailableNotice(event.reservationId());
    }
}
