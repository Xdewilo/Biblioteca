// by Jeremy Posada
package com.jposada.anaquel.application.book;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;
import com.jposada.anaquel.domain.shared.BusinessRuleException;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EliminarLibroHandler implements UseCase<EliminarLibro, Void> {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    @Override
    @Transactional
    public Void handle(EliminarLibro comando) {
        Book libro = bookRepository.findById(comando.bookId())
                .orElseThrow(() -> new ResourceNotFoundException("Libro", comando.bookId()));

        if (libro.getStatus() != BookStatus.DISPONIBLE) {
            throw new BusinessRuleException("BOOK_NOT_DELETABLE",
                    "Solo se pueden eliminar libros DISPONIBLES. '%s' esta en estado %s."
                            .formatted(libro.getTitle(), libro.getStatus()));
        }
        // Doble comprobacion: el estado podria estar desincronizado por una correccion manual.
        if (loanRepository.existsByBookIdAndReturnDateIsNull(comando.bookId())) {
            throw new BusinessRuleException("BOOK_NOT_DELETABLE",
                    "El libro '%s' tiene un prestamo activo.".formatted(libro.getTitle()));
        }

        bookRepository.delete(libro);
        log.info("Libro eliminado: id={} isbn={}", comando.bookId(), libro.getIsbn());
        return null;
    }
}
