package com.example.marketdata.provenance;

/**
 * Persistence port; deliberately independent of venue adapters.
 */
public interface ProvenanceRepository {
    void append(ProvenanceEvent event);
}
