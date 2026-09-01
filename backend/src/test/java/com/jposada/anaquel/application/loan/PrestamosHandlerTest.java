// by Jeremy Posada
package com.jposada.anaquel.application.loan;

import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.loan.Loan;
import com.jposada.anaquel.domain.reservation.Reservation;
import com.jposada.anaquel.domain.reservation.ReservationQueue;
import com.jposada.anaquel.domain.reservation.ReservationStatus;
import com.jposada.anaquel.domain.user.AppUser;
import com.jposada.anaquel.domain.user.Role;

import com.jposada.anaquel.infrastructure.config.AppProperties;
import com.jposada.anaquel.domain.book.*;
import com.jposada.anaquel.domain.loan.*;
import com.jposada.anaquel.domain.reservation.*;
import com.jposada.anaquel.domain.shared.*;
import com.jposada.anaquel.domain.user.*;
import com.jposada.anaquel.domain.shared.event.LoanCreatedEvent;
import com.jposada.anaquel.domain.shared.event.UserBlockedEvent;
import com.jposada.anaquel.domain.shared.BookNotAvailableException;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.UserBlockedException;
import com.jposada.anaquel.infrastructure.persistence.AppUserRepository;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import com.jposada.anaquel.application.loan.query.MisPrestamos;
import com.jposada.anaquel.application.loan.query.MisPrestamosHandler;
import com.jposada.anaquel.web.dto.LoanResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Prestamos - reglas de registro, devolucion y atrasos")
class PrestamosHandlerTest {

    @Mock private LoanRepository loanRepository;
    @Mock private BookRepository bookRepository;
    @Mock private AppUserRepository userRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ApplicationEventPublisher events;
    @Mock private ReservationQueue reservationQueue;

    private RegistrarPrestamoHandler registrarPrestamo;
    private DevolverPrestamoHandler devolverPrestamo;

    private AppUser borrower;
    private Book availableBook;

    @BeforeEach
    void setUp() {
        AppProperties rules = new AppProperties(14, 3, 90, 7, 2, 48);
        registrarPrestamo = new RegistrarPrestamoHandler(
                loanRepository, bookRepository, userRepository, reservationQueue, events, rules);
        devolverPrestamo = new DevolverPrestamoHandler(
                loanRepository, userRepository, reservationQueue, events, rules);

        borrower = AppUser.builder()
                .id(1L).name("Ana Perez").email("ana@anaquel.app")
                .passwordHash("hash").role(Role.BIBLIOTECARIO)
                .build();

        availableBook = Book.builder()
                .id(10L).title("Clean Code").author("Robert C. Martin")
                .isbn("9780132350884").status(BookStatus.DISPONIBLE)
                .build();

        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> {
            Loan loan = invocation.getArgument(0);
            loan.setId(100L);
            return loan;
        });
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(borrower));
    }

    // ------------------------------------------------------------- registrar

    @Nested
    @DisplayName("Al registrar un prestamo")
    class Create {

        @Test
        @DisplayName("presta el libro, lo marca PRESTADO y fija la fecha limite a 14 dias")
        void registersLoanAndSetsDueDateIn14Days() {
            when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(availableBook));
            when(reservationRepository.findFirstInQueue(anyLong())).thenReturn(Optional.empty());

            LoanResponse response = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(10L, borrower));

            assertThat(availableBook.getStatus()).isEqualTo(BookStatus.PRESTADO);
            assertThat(response.dueDate()).isEqualTo(LocalDate.now().plusDays(14));
            assertThat(response.loanDate()).isEqualTo(LocalDate.now());
            assertThat(response.borrowerEmail()).isEqualTo("ana@anaquel.app");
            assertThat(response.returned()).isFalse();
        }

        @Test
        @DisplayName("publica LoanCreatedEvent para que el correo salga fuera de la peticion HTTP")
        void publishesLoanCreatedEvent() {
            when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(availableBook));

            registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(10L, borrower));

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(events).publishEvent(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(LoanCreatedEvent.class);
            assertThat(((LoanCreatedEvent) captor.getValue()).loanId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("rechaza el prestamo si el libro ya esta PRESTADO")
        void rejectsWhenBookIsAlreadyLoaned() {
            availableBook.setStatus(BookStatus.PRESTADO);
            when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(availableBook));

            assertThatThrownBy(() -> registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(10L, borrower)))
                    .isInstanceOf(BookNotAvailableException.class)
                    .hasMessageContaining("no esta disponible");

            verify(loanRepository, never()).save(any());
            verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("rechaza el prestamo si la cuenta esta bloqueada")
        void rejectsWhenBorrowerIsBlocked() {
            borrower.block(Instant.now().plus(3, ChronoUnit.DAYS), "3 atrasos");
            when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(availableBook));

            assertThatThrownBy(() -> registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(10L, borrower)))
                    .isInstanceOf(UserBlockedException.class);

            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("un libro RESERVADO solo se lo lleva quien fue notificado de la lista de espera")
        void reservedBookOnlyGoesToTheNotifiedPerson() {
            availableBook.setStatus(BookStatus.RESERVADO);
            when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(availableBook));
            when(reservationQueue.currentHold(10L, "ana@anaquel.app")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(10L, borrower)))
                    .isInstanceOf(BookNotAvailableException.class)
                    .hasMessageContaining("reservado para otra persona");
        }

        @Test
        @DisplayName("quien fue notificado si puede llevarse el libro RESERVADO, y su reserva queda CUMPLIDO")
        void notifiedPersonCanTakeTheReservedBook() {
            availableBook.setStatus(BookStatus.RESERVADO);
            Reservation reservation = Reservation.builder()
                    .id(5L).book(availableBook).requesterEmail("ana@anaquel.app")
                    .requestedAt(Instant.now()).status(ReservationStatus.NOTIFICADO)
                    .build();

            when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(availableBook));
            when(reservationQueue.currentHold(10L, "ana@anaquel.app")).thenReturn(Optional.of(reservation));

            registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(10L, borrower));

            assertThat(availableBook.getStatus()).isEqualTo(BookStatus.PRESTADO);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CUMPLIDO);
        }

        @Test
        @DisplayName("un BIBLIOTECARIO no puede registrar prestamos a nombre de otra persona")
        void librarianCannotLendOnBehalfOfSomeoneElse() {
            when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(availableBook));

            assertThatThrownBy(() -> registrarPrestamo.handle(new RegistrarPrestamo(10L, "Otro", "otro@anaquel.app", borrower)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Solo un ADMIN");
        }
    }

    // -------------------------------------------------------------- devolver

    @Nested
    @DisplayName("Al devolver un prestamo")
    class Return {

        @Test
        @DisplayName("a tiempo: el libro vuelve a DISPONIBLE y no hay atraso")
        void onTimeReturnMakesBookAvailableAgain() {
            Loan loan = activeLoan(LocalDate.now().minusDays(3), LocalDate.now().plusDays(11));
            when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));
            when(reservationRepository.findFirstInQueue(10L)).thenReturn(Optional.empty());

            LoanResponse response = devolverPrestamo.handle(new DevolverPrestamo(100L, borrower));

            assertThat(response.returned()).isTrue();
            assertThat(response.returnedLate()).isFalse();
            // El destino del libro lo decide ReservationQueue (ver ReservationQueueIT).
            verify(reservationQueue).assignNextTurn(availableBook);
            verify(events, never()).publishEvent(any(UserBlockedEvent.class));
        }

        @Test
        @DisplayName("tarde con menos de 3 atrasos: suma el atraso pero NO bloquea la cuenta")
        void lateReturnBelowThresholdDoesNotBlock() {
            Loan loan = activeLoan(LocalDate.now().minusDays(20), LocalDate.now().minusDays(6));
            when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));
            when(loanRepository.countLateReturnsSince(eq("ana@anaquel.app"), any(LocalDate.class))).thenReturn(2L);

            LoanResponse response = devolverPrestamo.handle(new DevolverPrestamo(100L, borrower));

            assertThat(response.returnedLate()).isTrue();
            assertThat(borrower.isBlocked()).isFalse();
            verify(events, never()).publishEvent(any(UserBlockedEvent.class));
        }

        @Test
        @DisplayName("al tercer atraso en 90 dias bloquea la cuenta una semana y avisa por correo")
        void thirdLateReturnBlocksAccountForOneWeek() {
            Loan loan = activeLoan(LocalDate.now().minusDays(20), LocalDate.now().minusDays(6));
            when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));
            when(loanRepository.countLateReturnsSince(eq("ana@anaquel.app"), any(LocalDate.class))).thenReturn(3L);

            devolverPrestamo.handle(new DevolverPrestamo(100L, borrower));

            assertThat(borrower.isBlocked()).isTrue();
            assertThat(borrower.getBlockedUntil()).isBetween(
                    Instant.now().plus(6, ChronoUnit.DAYS),
                    Instant.now().plus(8, ChronoUnit.DAYS));
            assertThat(borrower.getBlockedReason()).contains("3 devoluciones con atraso");

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(events, atLeastOnce()).publishEvent(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(UserBlockedEvent.class::isInstance);
        }

        @Test
        @DisplayName("siempre le pasa el turno a la fila, tanto si hay alguien esperando como si no")
        void siempreDelegaElTurnoALaFila() {
            Loan loan = activeLoan(LocalDate.now().minusDays(3), LocalDate.now().plusDays(11));
            when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

            devolverPrestamo.handle(new DevolverPrestamo(100L, borrower));

            verify(reservationQueue).assignNextTurn(availableBook);
        }

        @Test
        @DisplayName("no se puede devolver dos veces el mismo prestamo")
        void cannotReturnTwice() {
            Loan loan = activeLoan(LocalDate.now().minusDays(3), LocalDate.now().plusDays(11));
            loan.setReturnDate(LocalDate.now().minusDays(1));
            when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> devolverPrestamo.handle(new DevolverPrestamo(100L, borrower)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ya fue devuelto");
        }

        @Test
        @DisplayName("no se puede devolver el prestamo de otra persona salvo que seas ADMIN")
        void cannotReturnSomeoneElsesLoan() {
            Loan loan = activeLoan(LocalDate.now().minusDays(3), LocalDate.now().plusDays(11));
            AppUser otherUser = AppUser.builder()
                    .id(2L).name("Luis").email("luis@anaquel.app")
                    .passwordHash("hash").role(Role.BIBLIOTECARIO).build();
            when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> devolverPrestamo.handle(new DevolverPrestamo(100L, otherUser)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("tus propios prestamos");
        }

        @Test
        @DisplayName("un ADMIN si puede devolver el prestamo de otra persona")
        void adminCanReturnAnyLoan() {
            Loan loan = activeLoan(LocalDate.now().minusDays(3), LocalDate.now().plusDays(11));
            AppUser admin = AppUser.builder()
                    .id(3L).name("Admin").email("admin@anaquel.app")
                    .passwordHash("hash").role(Role.ADMIN).build();
            when(loanRepository.findById(100L)).thenReturn(Optional.of(loan));
            when(reservationRepository.findFirstInQueue(10L)).thenReturn(Optional.empty());

            LoanResponse response = devolverPrestamo.handle(new DevolverPrestamo(100L, admin));

            assertThat(response.returned()).isTrue();
        }
    }

    private Loan activeLoan(LocalDate loanDate, LocalDate dueDate) {
        availableBook.setStatus(BookStatus.PRESTADO);
        return Loan.builder()
                .id(100L).book(availableBook)
                .borrowerName("Ana Perez").borrowerEmail("ana@anaquel.app")
                .loanDate(loanDate).dueDate(dueDate)
                .build();
    }

}
