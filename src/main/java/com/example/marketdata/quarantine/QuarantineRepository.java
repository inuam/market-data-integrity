package com.example.marketdata.quarantine;

/** Persistence port for rejected/suspect source records; venue-neutral. */
public interface QuarantineRepository {
    void save(QuarantinedRecord record);
}
