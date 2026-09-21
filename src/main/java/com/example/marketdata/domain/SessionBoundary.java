package com.example.marketdata.domain;

/**
 * Authoritative expected sequence range for one independently sequenced domain.
 */
public record SessionBoundary(SequenceDomain domain, long firstExpectedSequence, long lastExpectedSequence) {
    public SessionBoundary {
        if (firstExpectedSequence < 0 || lastExpectedSequence < firstExpectedSequence) {
            throw new IllegalArgumentException("Invalid session boundary");
        }
    }
}
