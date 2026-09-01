// by Jeremy Posada
package com.jposada.anaquel.application.book;

import com.jposada.anaquel.infrastructure.openlibrary.ExternalBookData;
import com.jposada.anaquel.infrastructure.openlibrary.LookupResult;
import com.jposada.anaquel.infrastructure.openlibrary.OpenLibraryClient;

import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.DuplicateIsbnException;
import com.jposada.anaquel.domain.shared.ExternalBookLookupException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.web.dto.BookLookupResponse;
import com.jposada.anaquel.application.book.EliminarLibro;
import com.jposada.anaquel.application.book.EliminarLibroHandler;
import com.jposada.anaquel.application.book.RegistrarLibro;
import com.jposada.anaquel.application.book.RegistrarLibroHandler;
import com.jposada.anaquel.application.book.query.PrevisualizarIsbn;
import com.jposada.anaquel.application.book.query.PrevisualizarIsbnHandler;
import com.jposada.anaquel.web.dto.BookResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
@DisplayName("Catalogo - ISBN unico y degradacion cuando Open Library falla")
class RegistrarLibroHandlerTest {

    @Mock private BookRepository bookRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private OpenLibraryClient openLibraryClient;

    private RegistrarLibroHandler registrarLibro;
    private EliminarLibroHandler eliminarLibro;
    private PrevisualizarIsbnHandler previsualizarIsbn;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        registrarLibro = new RegistrarLibroHandler(bookRepository, openLibraryClient);
        eliminarLibro = new EliminarLibroHandler(bookRepository, loanRepository);
        previsualizarIsbn = new PrevisualizarIsbnHandler(openLibraryClient, bookRepository);
    }

    private void savesWhatItReceives() {
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
            Book book = inv.getArgument(0);
            book.setId(1L);
            return book;
        });
    }

    @Test
    @DisplayName("rechaza registrar un libro con un ISBN que ya existe")
    void rejectsDuplicateIsbn() {
        when(bookRepository.existsByIsbn("9780132350884")).thenReturn(true);

        RegistrarLibro request = new RegistrarLibro(
                "978-0-13-235088-4", "Clean Code", "Robert C. Martin", 2008, null, null, false);

        assertThatThrownBy(() -> registrarLibro.handle(request))
                .isInstanceOf(DuplicateIsbnException.class)
                .hasMessageContaining("9780132350884");

        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("normaliza el ISBN: '978-0-13-235088-4' y '9780132350884' son el mismo libro")
    void normalizesIsbnBeforeChecking() {
        savesWhatItReceives();
        when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
        when(openLibraryClient.lookupByIsbn(anyString())).thenReturn(LookupResult.notFound());

        BookResponse response = registrarLibro.handle(new RegistrarLibro(
                "978-0-13-235088-4", "Clean Code", "Robert C. Martin", 2008, null, null, true));

        assertThat(response.isbn()).isEqualTo("9780132350884");
    }

    @Test
    @DisplayName("completa titulo, autor, anio y portada desde Open Library cuando el formulario viene vacio")
    void autocompletesFromOpenLibrary() {
        savesWhatItReceives();
        when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
        when(openLibraryClient.lookupByIsbn("9780134685991")).thenReturn(LookupResult.found(
                new ExternalBookData("9780134685991", "Effective Java", "Joshua Bloch", 2018,
                        "https://covers.openlibrary.org/b/isbn/9780134685991-L.jpg", List.of("Java"))));

        BookResponse response = registrarLibro.handle(new RegistrarLibro("9780134685991", null, null, null, null, null, true));

        assertThat(response.title()).isEqualTo("Effective Java");
        assertThat(response.author()).isEqualTo("Joshua Bloch");
        assertThat(response.publicationYear()).isEqualTo(2018);
        assertThat(response.coverUrl()).contains("covers.openlibrary.org");
        assertThat(response.subjects()).containsExactly("Java");
        assertThat(response.enrichedFromExternal()).isTrue();
        assertThat(response.status()).isEqualTo(BookStatus.DISPONIBLE);
    }

    @Test
    @DisplayName("lo que escribio la persona tiene prioridad sobre lo que devuelve la API")
    void manualDataWinsOverExternalData() {
        savesWhatItReceives();
        when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
        when(openLibraryClient.lookupByIsbn(anyString())).thenReturn(LookupResult.found(
                new ExternalBookData("9780134685991", "Effective Java", "Joshua Bloch", 2018, null, List.of())));

        BookResponse response = registrarLibro.handle(new RegistrarLibro(
                "9780134685991", "Java Efectivo (3ra ed.)", "J. Bloch", 2019, null, null, true));

        assertThat(response.title()).isEqualTo("Java Efectivo (3ra ed.)");
        assertThat(response.author()).isEqualTo("J. Bloch");
        assertThat(response.publicationYear()).isEqualTo(2019);
    }

    @Test
    @DisplayName("si Open Library falla, el libro se guarda igual con los datos manuales")
    void savesBookEvenIfExternalApiFails() {
        savesWhatItReceives();
        when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
        when(openLibraryClient.lookupByIsbn(anyString())).thenReturn(LookupResult.unavailable());

        BookResponse response = registrarLibro.handle(new RegistrarLibro(
                "9780132350884", "Clean Code", "Robert C. Martin", 2008, null, null, true));

        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(response.enrichedFromExternal()).isFalse();
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    @DisplayName("si Open Library falla y no hay datos manuales, se pide llenarlos (400, no 500)")
    void asksForManualDataWhenNothingIsAvailable() {
        when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
        when(openLibraryClient.lookupByIsbn(anyString())).thenReturn(LookupResult.unavailable());

        assertThatThrownBy(() -> registrarLibro.handle(new RegistrarLibro("9780132350884", null, null, null, null, null, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Escribe al menos titulo y autor");
    }

    @Test
    @DisplayName("la previsualizacion devuelve 503 cuando la API externa no responde")
    void previewFailsWith503WhenApiIsDown() {
        when(openLibraryClient.lookupByIsbn(anyString())).thenReturn(LookupResult.unavailable());

        assertThatThrownBy(() -> previsualizarIsbn.handle(new PrevisualizarIsbn("9780132350884")))
                .isInstanceOf(ExternalBookLookupException.class);
    }

    @Test
    @DisplayName("la previsualizacion devuelve 404 cuando el ISBN no existe en Open Library")
    void previewFailsWith404WhenIsbnIsUnknown() {
        when(openLibraryClient.lookupByIsbn(anyString())).thenReturn(LookupResult.notFound());

        assertThatThrownBy(() -> previsualizarIsbn.handle(new PrevisualizarIsbn("9780000000002")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("la previsualizacion avisa si el ISBN ya esta en el catalogo")
    void previewFlagsAlreadyRegisteredBooks() {
        when(bookRepository.existsByIsbn("9780132350884")).thenReturn(true);
        when(openLibraryClient.lookupByIsbn("9780132350884")).thenReturn(LookupResult.found(
                new ExternalBookData("9780132350884", "Clean Code", "Robert C. Martin", 2008, null, List.of())));

        BookLookupResponse response = previsualizarIsbn.handle(new PrevisualizarIsbn("978-0-13-235088-4"));

        assertThat(response.alreadyRegistered()).isTrue();
        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(response.source()).isEqualTo("openlibrary.org");
    }

    @Test
    @DisplayName("no se puede eliminar un libro que esta PRESTADO")
    void cannotDeleteLoanedBook() {
        Book book = Book.builder().id(1L).title("Clean Code").author("Martin")
                .isbn("9780132350884").status(BookStatus.PRESTADO).build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> eliminarLibro.handle(new EliminarLibro(1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DISPONIBLES");

        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    @DisplayName("no se puede eliminar un libro DISPONIBLE que aun tiene un prestamo activo")
    void cannotDeleteBookWithActiveLoan() {
        Book book = Book.builder().id(1L).title("Clean Code").author("Martin")
                .isbn("9780132350884").status(BookStatus.DISPONIBLE).build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIdAndReturnDateIsNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> eliminarLibro.handle(new EliminarLibro(1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("prestamo activo");
    }

    @Test
    @DisplayName("elimina un libro DISPONIBLE sin prestamos")
    void deletesAvailableBook() {
        Book book = Book.builder().id(1L).title("Clean Code").author("Martin")
                .isbn("9780132350884").status(BookStatus.DISPONIBLE).build();
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIdAndReturnDateIsNull(anyLong())).thenReturn(false);

        eliminarLibro.handle(new EliminarLibro(1L));

        verify(bookRepository).delete(book);
    }
}
