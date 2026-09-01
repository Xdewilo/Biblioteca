// by Jeremy Posada
package com.jposada.anaquel.integration;

import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.loan.Loan;
import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.domain.user.Role;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;

import com.jposada.anaquel.domain.book.*;
import com.jposada.anaquel.domain.loan.*;
import com.jposada.anaquel.domain.reservation.*;
import com.jposada.anaquel.domain.shared.*;
import com.jposada.anaquel.domain.user.*;
import com.jposada.anaquel.infrastructure.persistence.*;
import com.jposada.anaquel.infrastructure.scheduling.LoanReminderScheduler;
import com.jposada.anaquel.application.loan.DevolverPrestamo;
import com.jposada.anaquel.application.loan.DevolverPrestamoHandler;
import com.jposada.anaquel.application.loan.RegistrarPrestamo;
import com.jposada.anaquel.application.loan.RegistrarPrestamoHandler;
import com.jposada.anaquel.application.loan.query.MisPrestamos;
import com.jposada.anaquel.application.loan.query.MisPrestamosHandler;
import com.jposada.anaquel.application.reservation.CancelarReserva;
import com.jposada.anaquel.application.reservation.CancelarReservaHandler;
import com.jposada.anaquel.application.reservation.ConfirmarReserva;
import com.jposada.anaquel.application.reservation.ConfirmarReservaHandler;
import com.jposada.anaquel.application.reservation.EntrarEnListaDeEspera;
import com.jposada.anaquel.application.reservation.EntrarEnListaDeEsperaHandler;
import com.jposada.anaquel.application.reservation.query.MisReservas;
import com.jposada.anaquel.application.reservation.query.MisReservasHandler;
import com.jposada.anaquel.web.dto.CreateLoanRequest;
import com.jposada.anaquel.web.dto.LoanResponse;
import com.jposada.anaquel.domain.shared.BookNotAvailableException;
import com.jposada.anaquel.domain.shared.UserBlockedException;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Flujo end-to-end de prestamos, atrasos y lista de espera")
class LoanFlowIT {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup(3025, "127.0.0.1", "smtp"))
            .withPerMethodLifecycle(true);

    @Autowired private RegistrarPrestamoHandler registrarPrestamo;
    @Autowired private DevolverPrestamoHandler devolverPrestamo;
    @Autowired private MisPrestamosHandler misPrestamos;
    @Autowired private EntrarEnListaDeEsperaHandler entrarEnListaDeEspera;
    @Autowired private ConfirmarReservaHandler confirmarReserva;
    @Autowired private CancelarReservaHandler cancelarReserva;
    @Autowired private MisReservasHandler misReservas;
    @Autowired private LoanReminderScheduler scheduler;
    @Autowired private BookRepository bookRepository;
    @Autowired private LoanRepository loanRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ReservationRepository reservationRepository;

    private AppUser ana;
    private AppUser luis;
    private Book book;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        loanRepository.deleteAll();
        bookRepository.deleteAll();
        userRepository.deleteAll();

        ana = userRepository.save(AppUser.builder()
                .name("Ana Perez").email("ana@anaquel.app")
                .passwordHash("hash").role(Role.BIBLIOTECARIO).build());
        luis = userRepository.save(AppUser.builder()
                .name("Luis Gomez").email("luis@anaquel.app")
                .passwordHash("hash").role(Role.BIBLIOTECARIO).build());
        book = bookRepository.save(Book.builder()
                .title("Clean Code").author("Robert C. Martin").isbn("9780132350884")
                .status(BookStatus.DISPONIBLE).build());
    }

    @Test
    @DisplayName("prestar deja el libro PRESTADO y devolverlo lo regresa a DISPONIBLE")
    void loanAndReturnCycle() {
        LoanResponse loan = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStatus())
                .isEqualTo(BookStatus.PRESTADO);
        assertThat(loan.dueDate()).isEqualTo(LocalDate.now().plusDays(14));

        devolverPrestamo.handle(new DevolverPrestamo(loan.id(), ana));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStatus())
                .isEqualTo(BookStatus.DISPONIBLE);
    }

    @Test
    @DisplayName("dos personas no pueden tener prestado el mismo ejemplar")
    void secondLoanOnSameBookIsRejected() {
        registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));

        assertThatThrownBy(() -> registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), luis)))
                .isInstanceOf(BookNotAvailableException.class);
    }

    @Test
    @DisplayName("tres devoluciones tardias en 90 dias bloquean la cuenta y le impiden pedir mas")
    void threeLateReturnsBlockTheAccount() {
        // Dos atrasos ya cerrados en el historial
        givenLateReturnedLoan("9780134685991", 40);
        givenLateReturnedLoan("9780201633610", 20);

        // Tercer prestamo, tambien devuelto tarde
        Loan third = loanRepository.save(Loan.builder()
                .book(book).borrowerName(ana.getName()).borrowerEmail(ana.getEmail())
                .loanDate(LocalDate.now().minusDays(30)).dueDate(LocalDate.now().minusDays(16))
                .build());
        book.setStatus(BookStatus.PRESTADO);

        devolverPrestamo.handle(new DevolverPrestamo(third.getId(), ana));

        AppUser reloaded = userRepository.findByEmailIgnoreCase("ana@anaquel.app").orElseThrow();
        assertThat(reloaded.isBlocked()).isTrue();
        assertThat(reloaded.getBlockedReason()).contains("devoluciones con atraso");

        Book other = bookRepository.save(Book.builder()
                .title("Otro").author("Alguien").isbn("9780321125217")
                .status(BookStatus.DISPONIBLE).build());

        assertThatThrownBy(() -> registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(other.getId(), reloaded)))
                .isInstanceOf(UserBlockedException.class);
    }

    @Test
    @DisplayName("al devolver con lista de espera el libro queda RESERVADO para el primero de la fila")
    void returnWithWaitingListReservesForFirstInQueue() {
        LoanResponse loan = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));

        devolverPrestamo.handle(new DevolverPrestamo(loan.id(), ana));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStatus())
                .isEqualTo(BookStatus.RESERVADO);

        Reservation reservation = reservationRepository.findByRequesterEmail("luis@anaquel.app").get(0);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.NOTIFICADO);
        assertThat(reservation.getNotifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("el libro reservado solo se lo puede llevar quien esperaba; para el resto sigue no disponible")
    void reservedBookIsHeldForTheNotifiedPerson() {
        LoanResponse loan = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(loan.id(), ana));

        assertThatThrownBy(() -> registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana)))
                .isInstanceOf(BookNotAvailableException.class);

        LoanResponse luisLoan = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), luis));
        assertThat(luisLoan.borrowerEmail()).isEqualTo("luis@anaquel.app");
        assertThat(reservationRepository.findByRequesterEmail("luis@anaquel.app").get(0).getStatus())
                .isEqualTo(ReservationStatus.CUMPLIDO);
    }

    @Test
    @DisplayName("cancelar la reserva del turno se lo pasa al siguiente de la fila")
    void cancelingTheHoldPassesTurnToNextInQueue() {
        AppUser tercero = userRepository.save(AppUser.builder()
                .name("Maria").email("maria@anaquel.app")
                .passwordHash("hash").role(Role.BIBLIOTECARIO).build());

        LoanResponse loan = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, tercero));
        devolverPrestamo.handle(new DevolverPrestamo(loan.id(), ana));

        Reservation luisReservation = reservationRepository.findByRequesterEmail("luis@anaquel.app").get(0);
        cancelarReserva.handle(new CancelarReserva(luisReservation.getId(), luis));

        Reservation mariaReservation = reservationRepository.findByRequesterEmail("maria@anaquel.app").get(0);
        assertThat(mariaReservation.getStatus()).isEqualTo(ReservationStatus.NOTIFICADO);
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getStatus())
                .isEqualTo(BookStatus.RESERVADO);
    }

    @Test
    @DisplayName("la tarea programada marca reminderSentAt y no repite el recordatorio")
    void scheduledReminderMarksLoanAndDoesNotRepeat() {
        Loan dueSoon = loanRepository.save(Loan.builder()
                .book(book).borrowerName(ana.getName()).borrowerEmail(ana.getEmail())
                .loanDate(LocalDate.now().minusDays(13)).dueDate(LocalDate.now().plusDays(1))
                .build());

        scheduler.sendDueSoonReminders();
        assertThat(loanRepository.findById(dueSoon.getId()).orElseThrow().getReminderSentAt()).isNotNull();

        // Segunda corrida: ya no aparece en la consulta -> no se reenvia
        assertThat(loanRepository.findDueSoonWithoutReminder(LocalDate.now(), LocalDate.now().plusDays(2)))
                .isEmpty();
    }

    @Test
    @DisplayName("la tarea de vencidos marca overdueNoticeSentAt una sola vez")
    void scheduledOverdueNoticeIsSentOnce() {
        Loan overdue = loanRepository.save(Loan.builder()
                .book(book).borrowerName(ana.getName()).borrowerEmail(ana.getEmail())
                .loanDate(LocalDate.now().minusDays(20)).dueDate(LocalDate.now().minusDays(6))
                .build());

        scheduler.sendOverdueNotices();

        assertThat(loanRepository.findById(overdue.getId()).orElseThrow().getOverdueNoticeSentAt()).isNotNull();
        assertThat(loanRepository.findOverdueWithoutNotice(LocalDate.now())).isEmpty();
    }

    @Test
    @DisplayName("GET /api/loans/mine solo trae los prestamos de quien pregunta")
    void mineReturnsOnlyOwnLoans() {
        registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));

        assertThat(misPrestamos.handle(new MisPrestamos(ana))).hasSize(1);
        assertThat(misPrestamos.handle(new MisPrestamos(luis))).isEmpty();
    }

    private void givenLateReturnedLoan(String isbn, int daysAgo) {
        Book other = bookRepository.save(Book.builder()
                .title("Historico " + isbn).author("Autor").isbn(isbn)
                .status(BookStatus.DISPONIBLE).build());

        loanRepository.save(Loan.builder()
                .book(other).borrowerName(ana.getName()).borrowerEmail(ana.getEmail())
                .loanDate(LocalDate.now().minusDays(daysAgo + 20))
                .dueDate(LocalDate.now().minusDays(daysAgo + 6))
                .returnDate(LocalDate.now().minusDays(daysAgo))   // devuelto tarde
                .build());
    }
}
