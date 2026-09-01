// by Jeremy Posada
package com.jposada.anaquel.application.account;

import com.jposada.anaquel.application.shared.Command;

public record CrearCuenta(String nombre, String correo, String contrasena) implements Command {
}
