package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;
import com.example.marketdata.gap.StreamingGapDetector;
import com.example.marketdata.quality.ValidationResult;
import com.example.marketdata.sort.RecordSorter;
import com.example.marketdata.venue.VenueAdapter;
import com.example.marketdata.venue.VenueAdapterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Experiment: does a "lazy" RecordSorter (one that does not drain its input upfront) break the
 *  input/rejected counts reported by HistoricalProcessingService? */
class HistoricalProcessingServiceCountingTest {
    private final SequenceDomain d = new SequenceDomain("X", "1", "S");
    private MarketDataRecord r(long s) { return new MarketDataRecord(d, s, 0, "ABC", 100, 1, "f", s); }

    /** Hands back the input iterator completely unconsumed instead of draining it upfront like ChunkedExternalSorter does. */
    private static final class LazyPassthroughSorter implements RecordSorter {
        @Override public Iterator<MarketDataRecord> sort(Iterator<MarketDataRecord> input) {
            return input;
        }
    }

    private static final class FixedVenueAdapter implements VenueAdapter {
        private final List<MarketDataRecord> records;
        FixedVenueAdapter(List<MarketDataRecord> records) { this.records = records; }
        @Override public String venue() { return "X"; }
        @Override public boolean supports(Path path) { return true; }
        @Override public Stream<MarketDataRecord> read(Path path) { return records.stream(); }
    }

    @Test void reportedCountsMatchActualInputEvenWithALazySorter() throws Exception {
        List<MarketDataRecord> records = List.of(r(1), r(2), r(3), r(4), r(5));
        var service = new HistoricalProcessingService(
                new VenueAdapterRegistry(List.of(new FixedVenueAdapter(records))),
                new LazyPassthroughSorter(),
                new StreamingGapDetector(),
                rec -> ValidationResult.ok(),
                domain -> Optional.empty(),
                event -> { },
                rejected -> { });

        var result = service.process(Path.of("irrelevant.csv"));

        assertThat(result.inputRecords()).isEqualTo(5);
        assertThat(result.rejectedRecords()).isEqualTo(0);
    }
}
