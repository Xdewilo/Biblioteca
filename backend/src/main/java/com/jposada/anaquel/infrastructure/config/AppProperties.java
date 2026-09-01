// by Jeremy Posada
package com.jposada.anaquel.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rules")
public record AppProperties(
        int loanPeriodDays,
        int maxLateReturns,
        int lateReturnsWindowDays,
        int blockDurationDays,
        int reminderDaysBefore,
        /** Horas que se le guarda el libro a quien fue notificado antes de pasar el turno. */
        int reservationHoldHours
) {
    public AppProperties {
        if (loanPeriodDays <= 0) loanPeriodDays = 14;
        if (maxLateReturns <= 0) maxLateReturns = 3;
        if (lateReturnsWindowDays <= 0) lateReturnsWindowDays = 90;
        if (blockDurationDays <= 0) blockDurationDays = 7;
        if (reminderDaysBefore <= 0) reminderDaysBefore = 2;
        if (reservationHoldHours <= 0) reservationHoldHours = 48;
    }
}
