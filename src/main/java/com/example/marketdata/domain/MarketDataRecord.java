package com.example.marketdata.domain;

public record MarketDataRecord(
        SequenceDomain domain,
        long sequence,
        long eventTimeNanos,
        String instrument,
        long priceMantissa,
        long quantity,
        String sourceFile,
        long sourceOffset) {
}
