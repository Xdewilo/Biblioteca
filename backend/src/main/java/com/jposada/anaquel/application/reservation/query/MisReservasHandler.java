// by Jeremy Posada
package com.jposada.anaquel.application.reservation.query;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.domain.reservation.ReservationQueue;
import com.jposada.anaquel.infrastructure.persistence.ReservationRepository;
import com.jposada.anaquel.web.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MisReservasHandler implements UseCase<MisReservas, List<ReservationResponse>> {

    private final ReservationRepository reservationRepository;
    private final ReservationQueue queue;

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> handle(MisReservas consulta) {
        return reservationRepository.findByRequesterEmail(consulta.usuario().getEmail()).stream()
                .map(r -> ReservationResponse.from(r, queue.queuePosition(r), queue.holdHours()))
                .toList();
    }
}
