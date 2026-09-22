package com.example.marketdata.application;

import com.example.marketdata.boundary.SessionBoundaryProvider;
import com.example.marketdata.domain.Gap;
import com.example.marketdata.domain.QualityReport;
import com.example.marketdata.domain.SequenceDomain;
import com.example.marketdata.domain.SessionBoundary;
import com.example.marketdata.gap.StreamingGapDetector;
import com.example.marketdata.provenance.ProvenanceEvent;
import com.example.marketdata.quality.BasicRecordValidator;
import com.example.marketdata.quarantine.QuarantinedRecord;
import com.example.marketdata.sort.ChunkedExternalSorter;
import com.example.marketdata.venue.CsvVenueAdapter;
import com.example.marketdata.venue.VenueAdapterRegistry;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test over a large (~13k record), multi-domain, randomly-ordered archive: real CSV
 * parsing, a real bounded-memory external sort forced into many spilled runs by a small chunk
 * size, real validation/quarantine, and real gap/duplicate detection - wired together exactly as
 * {@link HistoricalProcessingService#process} assembles them.
 */
class HistoricalProcessingServiceLargeArchiveIntegrationTest {

    private static final SequenceDomain LSE_EQUITIES = new SequenceDomain("LSE", "EQUITIES", "2026-09-22");
    private static final SequenceDomain NASDAQ_OPTIONS = new SequenceDomain("NASDAQ", "OPTIONS", "2026-09-22");

    @Test
    void shouldFindAllGapsAndDuplicatesAcrossDomainsGivenLargeShuffledArchiveWithBoundariesAndInvalidRecords(@TempDir Path tmp) throws Exception {
        // Given: domain A has a boundary-defined start gap, two internal gaps, a boundary-defined end gap, and 5 duplicated sequences
        List<Long> domainASequences = sequenceRange(5, 9990, List.of(new long[]{500, 509}, new long[]{2000, 2049}));
        List<Long> domainADuplicates = List.of(700L, 4000L, 8000L, 8001L, 9989L);

        // Domain B has no configured boundary: one internal gap only, plus 2 duplicated sequences
        List<Long> domainBSequences = sequenceRange(1, 3000, List.of(new long[]{1500, 1504}));
        List<Long> domainBDuplicates = List.of(50L, 2500L);

        List<String> rows = new ArrayList<>();
        appendRows(rows, LSE_EQUITIES, domainASequences, domainADuplicates);
        appendRows(rows, NASDAQ_OPTIONS, domainBSequences, domainBDuplicates);

        // A handful of structurally invalid records mixed in - must be quarantined before sorting and never reach gap detection
        rows.add(csvRow(LSE_EQUITIES, 999_001, "ABC", 100, -5));    // negative quantity
        rows.add(csvRow(LSE_EQUITIES, -1, "ABC", 100, 1));          // negative sequence
        rows.add(csvRow(NASDAQ_OPTIONS, 999_002, "", 100, 1));      // missing instrument

        Collections.shuffle(rows, new Random(42));
        Path archive = tmp.resolve("large-archive.csv");
        Files.write(archive, rows);

        List<ProvenanceEvent> recordedEvents = new ArrayList<>();
        List<QuarantinedRecord> quarantined = new ArrayList<>();
        var service = getHistoricalProcessingService(recordedEvents, quarantined);

        // When
        var result = service.process(archive);

        // Then: overall counts account for every row, including the 3 invalid ones
        assertThat(result.inputRecords()).isEqualTo(rows.size());
        assertThat(result.rejectedRecords()).isEqualTo(3);
        assertThat(quarantined).hasSize(3);
        assertThat(result.reports()).hasSize(2);

        QualityReport reportA = reportFor(result, LSE_EQUITIES);
        assertThat(reportA.expectedFirst()).isEqualTo(1);
        assertThat(reportA.expectedLast()).isEqualTo(10_000);
        assertThat(reportA.observedMin()).isEqualTo(5);
        assertThat(reportA.observedMax()).isEqualTo(9990);
        assertThat(reportA.uniqueSequences()).isEqualTo(domainASequences.size());
        assertThat(reportA.duplicates()).isEqualTo(domainADuplicates.size());
        assertThat(reportA.totalRecords()).isEqualTo(domainASequences.size() + domainADuplicates.size());
        assertThat(reportA.gaps()).containsExactlyInAnyOrder(
                new Gap(LSE_EQUITIES, 1, 4),
                new Gap(LSE_EQUITIES, 500, 509),
                new Gap(LSE_EQUITIES, 2000, 2049),
                new Gap(LSE_EQUITIES, 9991, 10_000));
        assertThat(reportA.missingSequences()).isEqualTo(4 + 10 + 50 + 10);
        assertThat(reportA.largestGap()).isEqualTo(50);

        QualityReport reportB = reportFor(result, NASDAQ_OPTIONS);
        assertThat(reportB.expectedFirst()).isNull();
        assertThat(reportB.expectedLast()).isNull();
        assertThat(reportB.observedMin()).isEqualTo(1);
        assertThat(reportB.observedMax()).isEqualTo(3000);
        assertThat(reportB.uniqueSequences()).isEqualTo(domainBSequences.size());
        assertThat(reportB.duplicates()).isEqualTo(domainBDuplicates.size());
        assertThat(reportB.gaps()).containsExactly(new Gap(NASDAQ_OPTIONS, 1500, 1504));
        assertThat(reportB.missingSequences()).isEqualTo(5);

        // And: every detected gap and every quarantined record left an audit trail
        long gapEvents = recordedEvents.stream().filter(e -> e.type().equals("SEQUENCE_GAP")).count();
        assertThat(gapEvents).isEqualTo(reportA.gaps().size() + reportB.gaps().size());
        long quarantineEvents = recordedEvents.stream().filter(e -> e.type().equals("RECORD_QUARANTINED")).count();
        assertThat(quarantineEvents).isEqualTo(3);
    }

    private static @NonNull HistoricalProcessingService getHistoricalProcessingService(List<ProvenanceEvent> recordedEvents, List<QuarantinedRecord> quarantined) {
        SessionBoundaryProvider boundaries = domain -> domain.equals(LSE_EQUITIES)
                ? Optional.of(new SessionBoundary(LSE_EQUITIES, 1, 10_000))
                : Optional.empty();

        return new HistoricalProcessingService(
                new VenueAdapterRegistry(List.of(new CsvVenueAdapter())),
                new ChunkedExternalSorter(400), // small chunk forces many spilled runs and a real k-way merge
                new StreamingGapDetector(),
                new BasicRecordValidator(),
                boundaries,
                recordedEvents::add,
                quarantined::add);
    }

    private static QualityReport reportFor(ProcessingResult result, SequenceDomain domain) {
        return result.reports().stream().filter(r -> r.domain().equals(domain)).findFirst()
                .orElseThrow(() -> new AssertionError("No report for " + domain));
    }

    /** Every sequence in [start, end] except those falling inside any of the given inclusive exclusion ranges. */
    private static List<Long> sequenceRange(long start, long end, List<long[]> excludedRanges) {
        return LongStream.rangeClosed(start, end)
                .filter(s -> excludedRanges.stream().noneMatch(r -> s >= r[0] && s <= r[1]))
                .boxed()
                .toList();
    }

    private static void appendRows(List<String> rows, SequenceDomain domain, List<Long> sequences, List<Long> duplicates) {
        for (long s : sequences) rows.add(csvRow(domain, s, "ABC", 100, 1));
        for (long s : duplicates) rows.add(csvRow(domain, s, "ABC", 100, 1));
    }

    private static String csvRow(SequenceDomain domain, long sequence, String instrument, long priceMantissa, long quantity) {
        return String.join(",", domain.venue(), domain.channel(), domain.session(),
                Long.toString(sequence), "0", instrument, Long.toString(priceMantissa), Long.toString(quantity));
    }
}
