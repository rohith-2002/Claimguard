package com.claimguard.stream;

/**
 * Thrown when a claim message cannot be parsed or is empty. It never includes the payload, because the
 * payload contains the claimant's mobile number.
 */
public class InvalidClaimMessageException extends RuntimeException {

    public InvalidClaimMessageException(String topic, int partition, long offset) {
        super("Unparseable or empty claim at %s-%d@%d".formatted(topic, partition, offset));
    }
}
