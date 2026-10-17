package com.shbean.settlement;

/** A settlement step did not complete; the Kafka retry topic retries it and the DLT handler compensates. */
public class SettlementException extends RuntimeException {

    public SettlementException(String message) {
        super(message);
    }

    public SettlementException(String message, Throwable cause) {
        super(message, cause);
    }
}
