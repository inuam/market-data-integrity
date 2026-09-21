package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.provenance.ProvenanceEvent;
import com.example.marketdata.provenance.ProvenanceRepository;
import com.example.marketdata.quality.RecordValidator;
import com.example.marketdata.quarantine.QuarantineRepository;
import com.example.marketdata.quarantine.QuarantinedRecord;

import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Validates each record, routing failures to quarantine + provenance instead of passing them on.
 */
final class QuarantiningRecordFilter implements Iterator<MarketDataRecord> {
    private final Iterator<MarketDataRecord> source;
    private final RecordValidator validator;
    private final QuarantineRepository quarantine;
    private final ProvenanceRepository provenance;
    private long readCount;
    private long quarantinedCount;
    private MarketDataRecord next;
    private boolean ready;

    QuarantiningRecordFilter(Iterator<MarketDataRecord> source, RecordValidator validator,
                             QuarantineRepository quarantine, ProvenanceRepository provenance) {
        this.source = source;
        this.validator = validator;
        this.quarantine = quarantine;
        this.provenance = provenance;
    }

    long readCount() {
        return readCount;
    }

    long quarantinedCount() {
        return quarantinedCount;
    }

    private void prepare() {
        while (!ready && source.hasNext()) {
            var r = source.next();
            readCount++;
            var result = validator.validate(r);
            if (result.valid()) {
                next = r;
                ready = true;
            } else {
                quarantinedCount++;
                quarantine.save(new QuarantinedRecord(Instant.now(), r, result.reason(), validator.getClass().getSimpleName()));
                provenance.append(new ProvenanceEvent(Instant.now(), "RECORD_QUARANTINED", r.domain(),
                        r.sequence(), r.sequence(), r.sourceFile(), r.sourceOffset(), result.reason()));
            }
        }
    }

    @Override
    public boolean hasNext() {
        prepare();
        return ready;
    }

    @Override
    public MarketDataRecord next() {
        prepare();
        if (!ready) throw new NoSuchElementException();
        ready = false;
        return next;
    }
}
