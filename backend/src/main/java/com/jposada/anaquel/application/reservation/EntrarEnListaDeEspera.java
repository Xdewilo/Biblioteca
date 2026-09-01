// by Jeremy Posada
package com.jposada.anaquel.application.reservation;

import com.jposada.anaquel.application.shared.Command;
import com.jposada.anaquel.domain.user.AppUser;

public record EntrarEnListaDeEspera(Long bookId, String correoSolicitante, AppUser quienPide)
        implements Command {
}
