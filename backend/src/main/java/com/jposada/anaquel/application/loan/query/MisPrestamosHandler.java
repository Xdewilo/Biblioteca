// by Jeremy Posada
package com.jposada.anaquel.application.loan.query;

import com.jposada.anaquel.application.shared.UseCase;
import com.jposada.anaquel.infrastructure.persistence.LoanRepository;
import com.jposada.anaquel.web.dto.LoanResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MisPrestamosHandler implements UseCase<MisPrestamos, List<LoanResponse>> {

    private final LoanRepository loanRepository;

    @Override
    @Transactional(readOnly = true)
    public List<LoanResponse> handle(MisPrestamos consulta) {
        return loanRepository.findByBorrowerEmail(consulta.usuario().getEmail()).stream()
                .map(LoanResponse::from)
                .toList();
    }
}
