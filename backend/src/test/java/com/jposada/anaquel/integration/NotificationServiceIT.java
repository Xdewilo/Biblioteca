// by Jeremy Posada
package com.jposada.anaquel.integration;

import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.loan.Loan;
import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.domain.user.Role;

import com.jposada.anaquel.domain.book.*;
import com.jposada.anaquel.domain.loan.*;
import com.jposada.anaquel.domain.reservation.*;
import com.jposada.anaquel.domain.shared.*;
import com.jposada.anaquel.domain.user.*;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import com.jposada.anaquel.infrastructure.mail.NotificationService;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("NotificationService - envio real por SMTP (GreenMail)")
class NotificationServiceIT {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup(3025, "127.0.0.1", "smtp"))
            .withPerMethodLifecycle(true);

    @Autowired private NotificationService notificationService;
    @Autowired private BookRepository bookRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;

    /** ISBN unico por test: nada de aleatorio, que provoca colisiones intermitentes. */
    private static final AtomicInteger ISBN_SEQ = new AtomicInteger(1);

    @Test
    @DisplayName("la confirmacion de prestamo llega con asunto, destinatario y fecha limite")
    void sendsLoanConfirmation() throws Exception {
        Loan loan = givenActiveLoan("ana@anaquel.app");

        notificationService.sendLoanConfirmation(loan.getId());

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);

        MimeMessage message = received[0];
        assertThat(message.getSubject()).isEqualTo("Prestamo confirmado: Clean Code");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ana@anaquel.app");
        assertThat(message.getFrom()[0].toString()).contains("hola@anaquel.app");

        String body = GreenMailUtil.getBody(message);
        assertThat(body).contains("Clean Code");
        assertThat(body).contains("Robert C. Martin");
        assertThat(body).contains("Fecha limite de devolucion");
    }

    @Test
    @DisplayName("el recordatorio de vencimiento indica cuantos dias faltan")
    void sendsDueSoonReminder() throws Exception {
        Loan loan = givenActiveLoan("ana@anaquel.app");
        loan.setDueDate(LocalDate.now().plusDays(2));
        loanRepository.save(loan);

        notificationService.sendDueSoonReminder(loan.getId());

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage message = greenMail.getReceivedMessages()[0];
        assertThat(message.getSubject()).isEqualTo("Tu prestamo vence pronto: Clean Code");
        assertThat(GreenMailUtil.getBody(message)).contains("Fecha limite");
    }

    @Test
    @DisplayName("el aviso de bloqueo dice cuantos atrasos hubo y hasta cuando dura")
    void sendsAccountBlockedNotice() throws Exception {
        AppUser user = userRepository.save(AppUser.builder()
                .name("Ana Perez").email("bloqueada@anaquel.app")
                .passwordHash("hash").role(Role.BIBLIOTECARIO).build());

        notificationService.sendAccountBlockedNotice(user.getId(),
                Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS), 3);

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage message = greenMail.getReceivedMessages()[0];
        assertThat(message.getSubject()).contains("bloqueada temporalmente");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("bloqueada@anaquel.app");
        assertThat(GreenMailUtil.getBody(message)).contains("Ana Perez");
    }

    @Test
    @DisplayName("el aviso de libro disponible llega al primero de la lista de espera")
    void sendsBookAvailableNotice() throws Exception {
        Book book = bookRepository.save(Book.builder()
                .title("Refactoring").author("Martin Fowler").isbn("9780134757599")
                .status(BookStatus.RESERVADO).build());

        Reservation reservation = reservationRepository.save(Reservation.builder()
                .book(book).requesterEmail("luis@anaquel.app")
                .requestedAt(Instant.now()).status(ReservationStatus.NOTIFICADO)
                .notifiedAt(Instant.now()).build());

        notificationService.sendBookAvailableNotice(reservation.getId());

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage message = greenMail.getReceivedMessages()[0];
        assertThat(message.getSubject()).isEqualTo("Ya esta disponible: Refactoring");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("luis@anaquel.app");
        assertThat(GreenMailUtil.getBody(message)).contains("primera persona de la lista de espera");
    }

    @Test
    @DisplayName("un id inexistente no manda correo ni lanza excepcion")
    void unknownLoanDoesNotSendAnything() {
        notificationService.sendLoanConfirmation(999_999L);

        assertThat(greenMail.waitForIncomingEmail(1500, 1)).isFalse();
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    private Loan givenActiveLoan(String email) {
        Book book = bookRepository.save(Book.builder()
                .title("Clean Code").author("Robert C. Martin")
                .isbn("978%010d".formatted(ISBN_SEQ.getAndIncrement()))
                .status(BookStatus.PRESTADO).build());

        return loanRepository.save(Loan.builder()
                .book(book).borrowerName("Ana Perez").borrowerEmail(email)
                .loanDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(14))
                .build());
    }
}
