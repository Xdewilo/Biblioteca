// by Jeremy Posada
package com.jposada.anaquel.infrastructure.openlibrary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Respuesta cruda de GET https://openlibrary.org/isbn/{isbn}.json */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenLibraryResponse(
        String title,
        String subtitle,
        @JsonProperty("publish_date") String publishDate,
        List<Integer> covers,
        List<KeyRef> authors,
        List<KeyRef> works,
        List<String> subjects,
        List<String> publishers,
        @JsonProperty("number_of_pages") Integer numberOfPages
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeyRef(String key) {}

    /** GET /authors/{id}.json */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorResponse(String name, @JsonProperty("personal_name") String personalName) {
        public String bestName() {
            return name != null && !name.isBlank() ? name : personalName;
        }
    }

    /** GET /works/{id}.json. Ojo: aqui los autores vienen como {"author": {"key": ...}}, no {"key": ...}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkResponse(String title, List<String> subjects, List<WorkAuthor> authors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkAuthor(KeyRef author) {}
}
