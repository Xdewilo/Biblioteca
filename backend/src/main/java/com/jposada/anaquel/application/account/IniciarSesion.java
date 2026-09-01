// by Jeremy Posada
package com.jposada.anaquel.application.account;

import com.jposada.anaquel.application.shared.Command;

public record IniciarSesion(String correo, String contrasena) implements Command {
}
