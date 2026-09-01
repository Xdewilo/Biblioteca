// by Jeremy Posada
package com.jposada.anaquel.application.loan;

import com.jposada.anaquel.application.shared.Command;
import com.jposada.anaquel.domain.user.AppUser;

public record DevolverPrestamo(Long prestamoId, AppUser quienDevuelve) implements Command {
}
