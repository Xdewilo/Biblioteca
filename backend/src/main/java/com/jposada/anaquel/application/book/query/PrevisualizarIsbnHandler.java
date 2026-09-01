// by Jeremy Posada
package com.jposada.anaquel.application.book.query;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.ExternalBookLookupException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.infrastructure.openlibrary.ExternalBookData;
import com.jposada.anaquel.infrastructure.openlibrary.IsbnUtils;
import com.jposada.anaquel.infrastructure.openlibrary.LookupResult;
import com.jposada.anaquel.infrastructure.openlibrary.OpenLibraryClient;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.web.dto.BookLookupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 404 = el ISBN no existe en Open Library; 503 = la API no respondio. */
@Service
@RequiredArgsConstructor
public class PrevisualizarIsbnHandler implements UseCase<PrevisualizarIsbn, BookLookupResponse> {

    private final OpenLibraryClient openLibrary;
    private final BookRepository bookRepository;

    @Override
    @Transactional(readOnly = true)
    public BookLookupResponse handle(PrevisualizarIsbn consulta) {
        String isbn = IsbnUtils.normalize(consulta.isbn());
        if (!IsbnUtils.isValid(isbn)) {
            throw new BusinessRuleException("INVALID_ISBN", HttpStatus.BAD_REQUEST,
                    "'%s' no es un ISBN valido: revisa los digitos.".formatted(consulta.isbn()));
        }
        boolean yaRegistrado = bookRepository.existsByIsbn(isbn);

        LookupResult resultado = openLibrary.lookupByIsbn(isbn);

        if (resultado.isUnavailable()) {
            throw new ExternalBookLookupException(isbn);
        }
        if (!resultado.isFound()) {
            throw new ResourceNotFoundException(
                    "Open Library no tiene datos para el ISBN %s. Puedes registrar el libro a mano."
                            .formatted(isbn));
        }

        ExternalBookData datos = resultado.data();
        return new BookLookupResponse(isbn, datos.title(), datos.author(), datos.publicationYear(),
                datos.coverUrl(), datos.subjects(), "openlibrary.org", yaRegistrado);
    }
}
