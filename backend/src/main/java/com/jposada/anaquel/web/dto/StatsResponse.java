// by Jeremy Posada
package com.jposada.anaquel.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "StatsResponse", description = "Fotografia del estado de la biblioteca. Solo ADMIN.")
public record StatsResponse(
        long totalBooks,
        long availableBooks,
        long loanedBooks,
        long reservedBooks,
        long activeLoans,
        long overdueLoans,
        long dueSoonLoans,
        long totalUsers,
        long blockedUsers,
        long pendingReservations,
        List<UserResponse> blockedAccounts
) {}
