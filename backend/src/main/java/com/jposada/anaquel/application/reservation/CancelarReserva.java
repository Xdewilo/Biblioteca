// by Jeremy Posada
package com.jposada.anaquel.application.reservation;

import com.jposada.anaquel.application.shared.Command;
import com.jposada.anaquel.domain.user.AppUser;

public record CancelarReserva(Long reservaId, AppUser quienCancela) implements Command {
}
