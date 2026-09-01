// by Jeremy Posada
package com.jposada.anaquel.application.admin;

import com.jposada.anaquel.application.shared.Command;

public record LevantarBloqueo(Long usuarioId) implements Command {
}
