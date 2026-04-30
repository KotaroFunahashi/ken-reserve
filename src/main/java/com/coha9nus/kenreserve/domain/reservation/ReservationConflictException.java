package com.coha9nus.kenreserve.domain.reservation;

import com.coha9nus.kenreserve.exception.BaseBusinessException;

public class ReservationConflictException extends BaseBusinessException {
    public ReservationConflictException(String message) {
        super(message);
    }
}
