package com.coha9nus.kenreserve.exception;

public abstract class BaseBusinessException extends RuntimeException {
    protected BaseBusinessException(String message) {
        super(message);
    }
}
