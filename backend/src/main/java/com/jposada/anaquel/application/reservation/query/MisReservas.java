// by Jeremy Posada
package com.jposada.anaquel.application.reservation.query;

import com.jposada.anaquel.application.shared.Query;
import com.jposada.anaquel.domain.user.AppUser;

public record MisReservas(AppUser usuario) implements Query {
}
