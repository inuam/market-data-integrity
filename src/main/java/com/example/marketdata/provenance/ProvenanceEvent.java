package com.example.marketdata.provenance;

import com.example.marketdata.domain.SequenceDomain;

import java.time.Instant;

public record ProvenanceEvent(Instant occurredAt, String type, SequenceDomain domain,
                              Long sequenceFrom, Long sequenceTo, String sourceFile,
                              Long sourceOffset, String detail) {
}
