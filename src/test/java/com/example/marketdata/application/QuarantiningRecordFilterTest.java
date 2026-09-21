package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;
import com.example.marketdata.provenance.ProvenanceEvent;
import com.example.marketdata.quality.ValidationResult;
import com.example.marketdata.quarantine.QuarantinedRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuarantiningRecordFilterTest {
    private final SequenceDomain d = new SequenceDomain("X", "1", "S");

    private MarketDataRecord r(long s) {
        return new MarketDataRecord(d, s, 0, "ABC", 100, 1, "f", s);
    }

    @Test
    void passesThroughOnlyRecordsTheValidatorAccepts() {
        var filter = new QuarantiningRecordFilter(
                List.of(r(1), r(2), r(3)).iterator(),
                rec -> rec.sequence() == 2 ? ValidationResult.invalid("bad") : ValidationResult.ok(),
                rec -> {
                },
                event -> {
                });

        List<Long> passed = new ArrayList<>();
        while (filter.hasNext()) passed.add(filter.next().sequence());

        assertThat(passed).containsExactly(1L, 3L);
    }

    @Test
    void quarantinesRejectedRecordsWithTheirReason() {
        List<QuarantinedRecord> quarantined = new ArrayList<>();
        var filter = new QuarantiningRecordFilter(
                List.of(r(1)).iterator(),
                rec -> ValidationResult.invalid("bad price"),
                quarantined::add,
                event -> {
                });

        assertThat(filter.hasNext()).isFalse();
        assertThat(quarantined).hasSize(1);
        assertThat(quarantined.get(0).reason()).isEqualTo("bad price");
        assertThat(quarantined.get(0).record().sequence()).isEqualTo(1);
    }

    @Test
    void appendsAProvenanceEventForEachRejectedRecord() {
        List<ProvenanceEvent> events = new ArrayList<>();
        var filter = new QuarantiningRecordFilter(
                List.of(r(1)).iterator(),
                rec -> ValidationResult.invalid("bad price"),
                rec -> {
                },
                events::add);

        filter.hasNext();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("RECORD_QUARANTINED");
        assertThat(events.get(0).detail()).isEqualTo("bad price");
    }

    @Test
    void tracksReadAndQuarantinedCounts() {
        var filter = new QuarantiningRecordFilter(
                List.of(r(1), r(2), r(3)).iterator(),
                rec -> rec.sequence() == 2 ? ValidationResult.invalid("bad") : ValidationResult.ok(),
                rec -> {
                },
                event -> {
                });

        while (filter.hasNext()) filter.next();

        assertThat(filter.readCount()).isEqualTo(3);
        assertThat(filter.quarantinedCount()).isEqualTo(1);
    }

    @Test
    void throwsWhenNextCalledWithNoValidRecordsRemaining() {
        var filter = new QuarantiningRecordFilter(
                List.of(r(1)).iterator(),
                rec -> ValidationResult.invalid("bad"),
                rec -> {
                },
                event -> {
                });

        assertThatThrownBy(filter::next).isInstanceOf(NoSuchElementException.class);
    }
}
