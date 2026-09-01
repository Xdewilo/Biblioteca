// by Jeremy Posada
package com.jposada.anaquel.application.loan.query;

import com.jposada.anaquel.application.shared.Query;
import com.jposada.anaquel.domain.user.AppUser;

public record MisPrestamos(AppUser usuario) implements Query {
}
