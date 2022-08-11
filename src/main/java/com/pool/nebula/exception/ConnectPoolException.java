package com.pool.nebula.exception;

/**
 * 自定义异常
 * @author Ayuan
 */
public class ConnectPoolException extends RuntimeException {

    public ConnectPoolException() {
    }

    public ConnectPoolException(String message) {
        super(message);
    }

    public ConnectPoolException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectPoolException(Throwable cause) {
        super(cause);
    }

    public ConnectPoolException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
