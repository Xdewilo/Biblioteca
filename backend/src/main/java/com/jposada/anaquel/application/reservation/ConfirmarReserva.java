// by Jeremy Posada
package com.jposada.anaquel.application.reservation;

import com.jposada.anaquel.application.shared.Command;
import com.jposada.anaquel.domain.user.AppUser;

public record ConfirmarReserva(Long reservaId, AppUser quienConfirma) implements Command {
}
