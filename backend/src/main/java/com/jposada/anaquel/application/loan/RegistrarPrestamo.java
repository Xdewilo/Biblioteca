// by Jeremy Posada
package com.jposada.anaquel.application.loan;

import com.jposada.anaquel.application.shared.Command;
import com.jposada.anaquel.domain.user.AppUser;

/** prestatario solo puede diferir de quienPide si este es ADMIN. */
public record RegistrarPrestamo(
        Long bookId,
        String prestatarioNombre,
        String prestatarioCorreo,
        AppUser quienPide
) implements Command {

    public static RegistrarPrestamo paraSiMismo(Long bookId, AppUser usuario) {
        return new RegistrarPrestamo(bookId, null, null, usuario);
    }
}
