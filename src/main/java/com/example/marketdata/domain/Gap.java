package com.example.marketdata.domain;

public record Gap(SequenceDomain domain, long fromInclusive, long toInclusive) {
    public long missingCount() {
        return Math.addExact(Math.subtractExact(toInclusive, fromInclusive), 1);
    }
}
