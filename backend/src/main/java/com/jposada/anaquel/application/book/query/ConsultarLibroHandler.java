// by Jeremy Posada
package com.jposada.anaquel.application.book.query;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.shared.ResourceNotFoundException;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.web.dto.BookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultarLibroHandler implements UseCase<ConsultarLibro, BookResponse> {

    private final BookRepository bookRepository;

    @Override
    @Transactional(readOnly = true)
    public BookResponse handle(ConsultarLibro consulta) {
        return bookRepository.findById(consulta.bookId())
                .map(BookResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Libro", consulta.bookId()));
    }
}
