package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.QualityReport;
import com.example.marketdata.domain.SequenceDomain;
import com.example.marketdata.domain.SessionBoundary;
import com.example.marketdata.gap.GapDetector;
import com.example.marketdata.quality.ValidationResult;
import com.example.marketdata.sort.ChunkedExternalSorter;
import com.example.marketdata.venue.VenueAdapter;
import com.example.marketdata.venue.VenueAdapterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real, reproducible version of the "unenforced full-consumption" coupling: HistoricalProcessingService
 * assumes GapDetector.analyze() fully drains the DomainIterator it is given. Nothing in the GapDetector
 * interface requires that.
 */
class HistoricalProcessingServiceDomainGroupingTest {
    private final SequenceDomain d = new SequenceDomain("X", "1", "S");

    private MarketDataRecord r(long s) {
        return new MarketDataRecord(d, s, 0, "ABC", 100, 1, "f", s);
    }

    /**
     * A GapDetector that only ever reads the first record it is handed, then stops - a valid implementation
     * of the interface, which says nothing about needing to consume everything.
     */
    private static final class FirstRecordOnlyGapDetector implements GapDetector {
        @Override
        public QualityReport analyze(Iterator<MarketDataRecord> it, SessionBoundary boundary) {
            MarketDataRecord first = it.next();
            return new QualityReport(first.domain(), null, null, first.sequence(), first.sequence(), 1, 1, 0, 0, 0, 0, List.of());
        }
    }

    private record FixedVenueAdapter(List<MarketDataRecord> records) implements VenueAdapter {

        @Override
            public String venue() {
                return "X";
            }

            @Override
            public boolean supports(Path path) {
                return true;
            }

            @Override
            public Stream<MarketDataRecord> read(Path path) {
                return records.stream();
            }
        }

    /**
     * Characterization test: documents a known bug (PLAN.md item 8), it does not assert desired behavior.
     * One domain with 3 records SHOULD produce one QualityReport. Because FirstRecordOnlyGapDetector never
     * consumes more than one record per analyze() call, HistoricalProcessingService's outer loop mistakes
     * each leftover record for a fresh occurrence of the same domain, producing 3 partial reports instead.
     * If this test starts failing because reports() now has size 1, item 8 has been fixed - update this
     * test (and PLAN.md) to reflect the fix rather than reverting the fix.
     */
    @Test
    void detectorNotFullyConsumingADomainSilentlySplitsItIntoMultiplePartialReports() throws Exception {
        // Given
        List<MarketDataRecord> records = List.of(r(1), r(2), r(3));
        var service = new HistoricalProcessingService(
                new VenueAdapterRegistry(List.of(new FixedVenueAdapter(records))),
                new ChunkedExternalSorter(100),
                new FirstRecordOnlyGapDetector(),
                rec -> ValidationResult.ok(),
                domainArg -> Optional.empty(),
                event -> {
                },
                rejected -> {
                });

        // When
        var result = service.process(Path.of("irrelevant.csv"));

        assertThat(result.reports()).hasSize(3);
        assertThat(result.reports()).allMatch(report -> report.domain().equals(d));
    }
}
