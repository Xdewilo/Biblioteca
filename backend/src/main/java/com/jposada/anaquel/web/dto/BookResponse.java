// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import com.jposada.anaquel.domain.book.Book;
import com.jposada.anaquel.domain.book.BookStatus;

import java.util.List;

public record BookResponse(
        Long id,
        String title,
        String author,
        String isbn,
        Integer publicationYear,
        BookStatus status,
        String coverUrl,
        List<String> subjects,
        boolean enrichedFromExternal
) {
    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                book.getPublicationYear(), book.getStatus(), book.getCoverUrl(),
                List.copyOf(book.getSubjects()), book.isEnrichedFromExternal());
    }
}
