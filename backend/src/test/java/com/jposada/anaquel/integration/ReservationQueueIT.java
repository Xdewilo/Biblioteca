// by Jeremy Posada
package com.jposada.anaquel.integration;

import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
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
import com.jposada.anaquel.web.dto.ReservationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Lista de espera - el turno siempre avanza")
class ReservationQueueIT {

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

    private AppUser ana, luis, maria;
    private Book book;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        loanRepository.deleteAll();
        bookRepository.deleteAll();
        userRepository.deleteAll();

        ana = user("Ana", "ana@x.co");
        luis = user("Luis", "luis@x.co");
        maria = user("Maria", "maria@x.co");
        book = bookRepository.save(Book.builder()
                .title("Clean Code").author("Martin").isbn("9780132350884")
                .status(BookStatus.DISPONIBLE).build());
    }

    private AppUser user(String nombre, String correo) {
        return userRepository.save(AppUser.builder()
                .name(nombre).email(correo).passwordHash("hash").role(Role.BIBLIOTECARIO).build());
    }

    private Reservation reservaDe(String correo) {
        return reservationRepository.findByRequesterEmail(correo).get(0);
    }

    private BookStatus estadoDelLibro() {
        return bookRepository.findById(book.getId()).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("el turno se salta a las cuentas bloqueadas sin quitarles el puesto")
    void elTurnoSaltaLasCuentasBloqueadas() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));   // 1o
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, maria));  // 2a

        luis.block(Instant.now().plus(Duration.ofDays(7)), "3 atrasos");
        userRepository.save(luis);

        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        // Antes el libro quedaba atrapado para Luis; ahora el turno pasa a Maria.
        assertThat(estadoDelLibro()).isEqualTo(BookStatus.RESERVADO);
        assertThat(reservaDe("maria@x.co").getStatus()).isEqualTo(ReservationStatus.NOTIFICADO);
        // Luis no pierde su puesto: sigue esperando para la proxima vuelta
        assertThat(reservaDe("luis@x.co").getStatus()).isEqualTo(ReservationStatus.PENDIENTE);

        LoanResponse suyo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), maria));
        assertThat(suyo.borrowerEmail()).isEqualTo("maria@x.co");
    }

    @Test
    @DisplayName("si toda la fila esta bloqueada, el libro vuelve a DISPONIBLE en vez de atascarse")
    void siNadiePuedeTomarloElLibroVuelveAlCatalogo() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));

        luis.block(Instant.now().plus(Duration.ofDays(7)), "3 atrasos");
        userRepository.save(luis);

        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        assertThat(estadoDelLibro()).isEqualTo(BookStatus.DISPONIBLE);
        // y cualquiera puede pedirlo
        assertThat(registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), maria)).borrowerEmail())
                .isEqualTo("maria@x.co");
    }

    @Test
    @DisplayName("un turno que nadie confirma caduca y pasa al siguiente de la fila")
    void elTurnoAbandonadoCaducaYPasaAlSiguiente() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, maria));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        assertThat(reservaDe("luis@x.co").getStatus()).isEqualTo(ReservationStatus.NOTIFICADO);

        // Luis fue avisado hace tres dias y nunca aparecio
        Reservation deLuis = reservaDe("luis@x.co");
        deLuis.setNotifiedAt(Instant.now().minus(Duration.ofDays(3)));
        reservationRepository.saveAndFlush(deLuis);

        scheduler.expireStaleHolds();

        assertThat(reservaDe("luis@x.co").getStatus()).isEqualTo(ReservationStatus.CANCELADO);
        assertThat(reservaDe("maria@x.co").getStatus()).isEqualTo(ReservationStatus.NOTIFICADO);
        assertThat(estadoDelLibro()).isEqualTo(BookStatus.RESERVADO);
    }

    @Test
    @DisplayName("si caduca el turno y no queda nadie esperando, el libro vuelve al catalogo")
    void turnoCaducadoSinFilaLiberaElLibro() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        Reservation deLuis = reservaDe("luis@x.co");
        deLuis.setNotifiedAt(Instant.now().minus(Duration.ofDays(3)));
        reservationRepository.saveAndFlush(deLuis);

        scheduler.expireStaleHolds();

        assertThat(estadoDelLibro()).isEqualTo(BookStatus.DISPONIBLE);
    }

    @Test
    @DisplayName("un turno reciente NO caduca")
    void elTurnoRecienteNoCaduca() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        scheduler.expireStaleHolds();

        assertThat(reservaDe("luis@x.co").getStatus()).isEqualTo(ReservationStatus.NOTIFICADO);
        assertThat(estadoDelLibro()).isEqualTo(BookStatus.RESERVADO);
    }

    @Test
    @DisplayName("confirmar la reserva crea el prestamo en un solo paso")
    void confirmarCreaElPrestamoDeUnaVez() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        Long reservaId = reservaDe("luis@x.co").getId();
        LoanResponse suyo = confirmarReserva.handle(new ConfirmarReserva(reservaId, luis));

        assertThat(suyo.borrowerEmail()).isEqualTo("luis@x.co");
        assertThat(suyo.dueDate()).isEqualTo(java.time.LocalDate.now().plusDays(14));
        assertThat(estadoDelLibro()).isEqualTo(BookStatus.PRESTADO);
        assertThat(reservaDe("luis@x.co").getStatus()).isEqualTo(ReservationStatus.CUMPLIDO);
    }

    @Test
    @DisplayName("no se puede confirmar una reserva a la que aun no le toca el turno")
    void noSePuedeConfirmarSinTurno() {
        registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        ReservationResponse enEspera =
                entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));

        assertThatThrownBy(() -> confirmarReserva.handle(new ConfirmarReserva(enEspera.id(), luis)))
                .hasMessageContaining("todavia no tiene el turno");
    }

    @Test
    @DisplayName("no se puede confirmar la reserva de otra persona")
    void noSePuedeConfirmarLaReservaAjena() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        Long reservaId = reservaDe("luis@x.co").getId();
        assertThatThrownBy(() -> confirmarReserva.handle(new ConfirmarReserva(reservaId, maria)))
                .hasMessageContaining("tus propias reservas");
    }

    @Test
    @DisplayName("la respuesta dice hasta cuando se guarda el libro y si ya se puede confirmar")
    void laRespuestaExponeElPlazo() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        ReservationResponse suya = misReservas.handle(new MisReservas(luis)).get(0);

        assertThat(suya.readyToConfirm()).isTrue();
        assertThat(suya.holdExpiresAt()).isAfter(Instant.now());
        assertThat(suya.queuePosition()).isEqualTo(1);
    }

    // ------------------------------------------------------------- endurecimiento

    @Test
    @DisplayName("no puedes reservar el libro que tu mismo tienes prestado (seria una renovacion encubierta)")
    void noSePuedeReservarElLibroQueUnoMismoTiene() {
        registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));

        assertThatThrownBy(() -> entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, ana)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya tienes");
    }

    @Test
    @DisplayName("una cuenta bloqueada no puede entrar en la lista de espera")
    void unaCuentaBloqueadaNoEntraEnLaFila() {
        registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        luis.block(Instant.now().plus(Duration.ofDays(7)), "3 atrasos");
        userRepository.save(luis);

        assertThatThrownBy(() -> entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis)))
                .isInstanceOf(UserBlockedException.class);
    }

    @Test
    @DisplayName("un turno vencido no se puede confirmar aunque el cron aun no lo haya caducado")
    void unTurnoVencidoNoSeConfirma() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        Reservation deLuis = reservaDe("luis@x.co");
        deLuis.setNotifiedAt(Instant.now().minus(Duration.ofDays(3)));
        reservationRepository.saveAndFlush(deLuis);

        assertThatThrownBy(() -> confirmarReserva.handle(new ConfirmarReserva(deLuis.getId(), luis)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vencio");
    }

    @Test
    @DisplayName("si un ADMIN confirma la reserva de otra persona, el prestamo queda a nombre de esa persona")
    void elAdminConfirmaANombreDeQuienReservo() {
        AppUser admin = userRepository.save(AppUser.builder()
                .name("Administrador").email("admin@x.co").passwordHash("hash").role(Role.ADMIN).build());
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        LoanResponse suyo = confirmarReserva.handle(new ConfirmarReserva(reservaDe("luis@x.co").getId(), admin));

        assertThat(suyo.borrowerEmail()).isEqualTo("luis@x.co");
        assertThat(suyo.borrowerName()).isEqualTo("Luis");
    }

    @Test
    @DisplayName("un turno caducado sobre un libro que ya se volvio a prestar no lo pone DISPONIBLE")
    void unTurnoCaducadoNoLiberaUnLibroPrestado() {
        LoanResponse prestamo = registrarPrestamo.handle(RegistrarPrestamo.paraSiMismo(book.getId(), ana));
        entrarEnListaDeEspera.handle(new EntrarEnListaDeEspera(book.getId(), null, luis));
        devolverPrestamo.handle(new DevolverPrestamo(prestamo.id(), ana));

        // Estado inconsistente simulado: la reserva sigue NOTIFICADO pero el libro ya esta PRESTADO.
        Reservation deLuis = reservaDe("luis@x.co");
        deLuis.setNotifiedAt(Instant.now().minus(Duration.ofDays(3)));
        reservationRepository.saveAndFlush(deLuis);
        Book libro = bookRepository.findById(book.getId()).orElseThrow();
        libro.setStatus(BookStatus.PRESTADO);
        bookRepository.saveAndFlush(libro);
        loanRepository.saveAndFlush(Loan.builder()
                .book(libro).borrowerName("Maria").borrowerEmail("maria@x.co")
                .loanDate(java.time.LocalDate.now()).dueDate(java.time.LocalDate.now().plusDays(14))
                .build());

        scheduler.expireStaleHolds();

        assertThat(reservaDe("luis@x.co").getStatus()).isEqualTo(ReservationStatus.CANCELADO);
        assertThat(estadoDelLibro()).isEqualTo(BookStatus.PRESTADO);
    }
}
