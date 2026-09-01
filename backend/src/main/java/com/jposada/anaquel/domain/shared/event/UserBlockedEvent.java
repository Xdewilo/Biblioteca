// by Jeremy Posada
package com.jposada.anaquel.domain.shared.event;

import java.time.Instant;

public record UserBlockedEvent(Long userId, Instant blockedUntil, long lateReturns) {}
