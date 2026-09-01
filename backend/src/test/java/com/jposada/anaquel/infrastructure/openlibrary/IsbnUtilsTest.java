// by Jeremy Posada
package com.jposada.anaquel.infrastructure.openlibrary;

import com.jposada.anaquel.infrastructure.openlibrary.IsbnUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IsbnUtils - normalizacion")
class IsbnUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "978-0-13-235088-4, 9780132350884",
            "9780132350884,    9780132350884",
            "'978 0 13 235088 4', 9780132350884",
            "0-306-40615-2,     0306406152",
            "080442957x,        080442957X"
    })
    @DisplayName("quita guiones y espacios y pasa la X a mayuscula")
    void normalizes(String raw, String expected) {
        assertThat(IsbnUtils.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9780132350884", "0306406152"})
    @DisplayName("acepta ISBN de 10 y 13 digitos")
    void acceptsValidLengths(String isbn) {
        assertThat(IsbnUtils.isPlausible(isbn)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "97801323508840000", ""})
    @DisplayName("rechaza longitudes que no son 10 ni 13")
    void rejectsInvalidLengths(String isbn) {
        assertThat(IsbnUtils.isPlausible(isbn)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9780132350884", "9780134685991", "9788497592581", "0306406152", "080442957X"})
    @DisplayName("acepta ISBN con digito de control correcto (10 y 13)")
    void acceptsValidCheckDigits(String isbn) {
        assertThat(IsbnUtils.isValid(isbn)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9780132350885", "9999999999999", "0306406153", "080442957A", "", "12345"})
    @DisplayName("rechaza ISBN con digito de control incorrecto o forma invalida")
    void rejectsInvalidCheckDigits(String isbn) {
        assertThat(IsbnUtils.isValid(isbn)).isFalse();
    }
}
