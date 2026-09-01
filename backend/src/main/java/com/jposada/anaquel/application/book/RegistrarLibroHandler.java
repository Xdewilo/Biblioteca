// by Jeremy Posada
package com.jposada.anaquel.application.book;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.DuplicateIsbnException;
import com.jposada.anaquel.infrastructure.openlibrary.ExternalBookData;
import com.jposada.anaquel.infrastructure.openlibrary.IsbnUtils;
import com.jposada.anaquel.infrastructure.openlibrary.LookupResult;
import com.jposada.anaquel.infrastructure.openlibrary.OpenLibraryClient;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.web.dto.BookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Si Open Library no responde, el libro se guarda igual con los datos manuales. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrarLibroHandler implements UseCase<RegistrarLibro, BookResponse> {

    private final BookRepository bookRepository;
    private final OpenLibraryClient openLibrary;

    @Override
    @Transactional
    public BookResponse handle(RegistrarLibro comando) {
        String isbn = IsbnUtils.normalize(comando.isbn());
        if (!IsbnUtils.isValid(isbn)) {
            throw new BusinessRuleException("INVALID_ISBN", HttpStatus.BAD_REQUEST,
                    "'%s' no es un ISBN valido: revisa los digitos.".formatted(comando.isbn()));
        }
        if (bookRepository.existsByIsbn(isbn)) {
            throw new DuplicateIsbnException(isbn);
        }

        String titulo = vacioANulo(comando.titulo());
        String autor = vacioANulo(comando.autor());
        Integer anio = comando.anio();
        String portada = vacioANulo(comando.portada());
        List<String> temas = comando.temas() == null ? new ArrayList<>() : new ArrayList<>(comando.temas());
        boolean enriquecido = false;

        if (comando.autocompletar()) {
            LookupResult externo = openLibrary.lookupByIsbn(isbn);
            if (externo.isFound()) {
                ExternalBookData datos = externo.data();
                // Lo que escribio la persona manda sobre lo que diga la API.
                if (titulo == null) titulo = datos.title();
                if (autor == null) autor = datos.author();
                if (anio == null) anio = datos.publicationYear();
                if (portada == null) portada = datos.coverUrl();
                if (temas.isEmpty() && datos.subjects() != null) temas.addAll(datos.subjects());
                enriquecido = true;
            } else {
                log.info("Open Library no completo el ISBN {} ({}); se guarda con datos manuales",
                        isbn, externo.status());
            }
        }

        if (titulo == null || autor == null) {
            throw new BusinessRuleException("MISSING_BOOK_DATA", HttpStatus.BAD_REQUEST,
                    "No se pudo completar el libro desde Open Library. Escribe al menos titulo y autor.");
        }

        Book guardado = bookRepository.save(Book.builder()
                .isbn(isbn).title(titulo).author(autor).publicationYear(anio)
                .coverUrl(portada).subjects(temas)
                .status(BookStatus.DISPONIBLE)
                .enrichedFromExternal(enriquecido)
                .build());

        log.info("Libro registrado: id={} isbn={} enriquecido={}",
                guardado.getId(), guardado.getIsbn(), enriquecido);
        return BookResponse.from(guardado);
    }

    private String vacioANulo(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
