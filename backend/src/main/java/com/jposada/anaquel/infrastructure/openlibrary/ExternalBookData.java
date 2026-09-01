// by Jeremy Posada
package com.jposada.anaquel.infrastructure.openlibrary;

import java.io.Serializable;
import java.util.List;

public record ExternalBookData(
        String isbn,
        String title,
        String author,
        Integer publicationYear,
        String coverUrl,
        List<String> subjects
) implements Serializable {

    public boolean hasUsefulData() {
        return title != null && !title.isBlank();
    }
}
