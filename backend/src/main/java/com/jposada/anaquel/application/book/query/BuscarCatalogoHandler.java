// by Jeremy Posada
package com.jposada.anaquel.application.book.query;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.infrastructure.persistence.BookRepository;
import com.jposada.anaquel.web.dto.BookResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BuscarCatalogoHandler implements UseCase<BuscarCatalogo, Page<BookResponse>> {

    private final BookRepository bookRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<BookResponse> handle(BuscarCatalogo consulta) {
        return bookRepository.findAll(conFiltros(consulta), consulta.paginacion())
                .map(BookResponse::from);
    }

    private Specification<Book> conFiltros(BuscarCatalogo consulta) {
        return (root, cq, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (consulta.texto() != null && !consulta.texto().isBlank()) {
                String patron = "%" + consulta.texto().trim().toLowerCase() + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("title")), patron),
                        cb.like(cb.lower(root.get("author")), patron),
                        cb.like(cb.lower(root.get("isbn")), patron)));
            }
            if (consulta.estado() != null) {
                condiciones.add(cb.equal(root.get("status"), consulta.estado()));
            }
            return condiciones.isEmpty() ? cb.conjunction() : cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
