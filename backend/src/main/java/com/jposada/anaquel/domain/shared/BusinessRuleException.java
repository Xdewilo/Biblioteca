// by Jeremy Posada
package com.jposada.anaquel.domain.shared;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends BusinessException {

    public BusinessRuleException(String code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }

    public BusinessRuleException(String code, HttpStatus status, String message) {
        super(code, status, message);
    }
}
