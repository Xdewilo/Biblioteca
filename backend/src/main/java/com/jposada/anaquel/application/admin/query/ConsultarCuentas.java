// by Jeremy Posada
package com.jposada.anaquel.application.admin.query;

import com.jposada.anaquel.application.shared.Query;

public record ConsultarCuentas(boolean soloBloqueadas) implements Query {
}
