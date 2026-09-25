package com.example.marketdata;

import com.example.marketdata.application.ProcessingResult;
import com.example.marketdata.api.ProcessingController;
import com.example.marketdata.domain.QualityReport;
import com.example.marketdata.domain.SequenceDomain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the full Spring application and drives it purely over HTTP through {@link ProcessingController},
 * against a ~1,000,000-record archive spanning five domains (different venues, channels, and session days),
 * each carrying its own mix of internal gaps, duplicates, and (for some) a start/end boundary gap.
 * <p>
 * Not part of the default {@code mvn test} run (its class name doesn't match Surefire's default
 * {@code *Test.java} pattern) — run it explicitly:
 * <pre>{@code mvn test -Dtest=HistoricalProcessingPerformanceIT}</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoricalProcessingPerformanceIT {

    private static final SequenceDomain NYSE_DAY1 = new SequenceDomain("NYSE", "EQUITIES", "2026-09-14");
    private static final SequenceDomain NYSE_DAY2 = new SequenceDomain("NYSE", "EQUITIES", "2026-09-15");
    private static final SequenceDomain LSE_EQUITIES = new SequenceDomain("LSE", "EQUITIES", "2026-09-14");
    private static final SequenceDomain LSE_OPTIONS = new SequenceDomain("LSE", "OPTIONS", "2026-09-14");
    private static final SequenceDomain NASDAQ_DAY = new SequenceDomain("NASDAQ", "EQUITIES", "2026-09-16");

    private static final int RECORDS_PER_DOMAIN = 200_000;

    @TempDir
    static Path tempDir;

    private static Path archive;
    private static int expectedRowCount;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void overridePipelineFiles(DynamicPropertyRegistry registry) {
        // Force many spilled runs and a real multi-run k-way merge instead of one in-memory chunk.
        registry.add("market-data.sort.chunk-records", () -> 100_000);
        registry.add("market-data.boundaries-file", () -> tempDir.resolve("session-boundaries.csv").toString());
        registry.add("market-data.provenance-file", () -> tempDir.resolve("provenance.tsv").toString());
        registry.add("market-data.quarantine-file", () -> tempDir.resolve("quarantine.tsv").toString());
    }

    @BeforeAll
    static void generateArchiveAndBoundaries() throws Exception {
        writeBoundaries(tempDir.resolve("session-boundaries.csv"));

        List<String> rows = new ArrayList<>(RECORDS_PER_DOMAIN * 5 + 100);

        // NYSE/day1: data covers the full boundary range exactly -> internal gaps only.
        appendDomain(rows, NYSE_DAY1, sequenceRange(1, RECORDS_PER_DOMAIN,
                List.of(new long[]{1_000, 1_049}, new long[]{80_000, 80_099}, new long[]{150_000, 150_199})),
                List.of(500L, 60_000L, 199_000L));

        // NYSE/day2: same venue, next day -> a separate domain entirely, its own gaps.
        appendDomain(rows, NYSE_DAY2, sequenceRange(1, RECORDS_PER_DOMAIN,
                List.of(new long[]{2_000, 2_099}, new long[]{120_000, 120_299})),
                List.of(1_000L, 100_000L));

        // LSE/EQUITIES: data starts late and ends early -> boundary reveals a start gap AND an end gap.
        appendDomain(rows, LSE_EQUITIES, sequenceRange(51, RECORDS_PER_DOMAIN - 50,
                List.of(new long[]{50_000, 50_249})),
                List.of(75_000L));

        // LSE/OPTIONS: same venue, different channel -> different domain; no boundary configured for it.
        appendDomain(rows, LSE_OPTIONS, sequenceRange(1, RECORDS_PER_DOMAIN,
                List.of(new long[]{30_000, 30_399})),
                List.of());

        // NASDAQ: a third venue, its own day.
        appendDomain(rows, NASDAQ_DAY, sequenceRange(1, RECORDS_PER_DOMAIN,
                List.of(new long[]{10_000, 10_009}, new long[]{190_000, 190_499})),
                List.of(5_000L, 5_001L, 150_000L));

        // A handful of structurally invalid rows mixed in, quarantined rather than crashing the run.
        rows.add(csvRow(NYSE_DAY1, 999_001, "ABC", 100, -5));    // negative quantity
        rows.add(csvRow(NYSE_DAY1, -1, "ABC", 100, 1));          // negative sequence
        rows.add(csvRow(NASDAQ_DAY, 999_002, "", 100, 1));       // missing instrument

        Collections.shuffle(rows, new Random(42));
        expectedRowCount = rows.size();

        archive = tempDir.resolve("million-record-archive.csv");
        Files.write(archive, rows);
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void shouldProcessAMillionRecordArchiveWithGapsAcrossMultipleDomainsWithinTheHttpApi() {
        var request = new ProcessingController.ProcessRequest(archive.toAbsolutePath().toString());
        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

        long startNanos = System.nanoTime();
        ProcessingResult result = client.post()
                .uri("/api/v1/historical/process")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProcessingResult.class)
                .returnResult()
                .getResponseBody();
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(result).isNotNull();

        double recordsPerSecond = expectedRowCount / Math.max(1.0, elapsedMillis / 1000.0);
        System.out.printf("[perf] %,d records via HTTP in %,d ms (%,.0f records/sec)%n",
                expectedRowCount, elapsedMillis, recordsPerSecond);

        // Correctness, not just speed: every row accounted for, and the known-shape gaps were actually found.
        assertThat(result.inputRecords()).isEqualTo(expectedRowCount);
        assertThat(result.rejectedRecords()).isEqualTo(3);
        assertThat(result.reports()).hasSize(5);

        QualityReport nyseDay1 = reportFor(result, NYSE_DAY1);
        assertThat(nyseDay1.gaps()).hasSize(3);
        assertThat(nyseDay1.duplicates()).isEqualTo(3);

        QualityReport lseEquities = reportFor(result, LSE_EQUITIES);
        assertThat(lseEquities.expectedFirst()).isEqualTo(1);
        assertThat(lseEquities.expectedLast()).isEqualTo(RECORDS_PER_DOMAIN);
        assertThat(lseEquities.gaps()).hasSize(3); // start gap + 1 internal gap + end gap

        QualityReport lseOptions = reportFor(result, LSE_OPTIONS);
        assertThat(lseOptions.expectedFirst()).isNull(); // no boundary configured for this domain
        assertThat(lseOptions.gaps()).hasSize(1);
    }

    private static QualityReport reportFor(ProcessingResult result, SequenceDomain domain) {
        return result.reports().stream().filter(r -> r.domain().equals(domain)).findFirst()
                .orElseThrow(() -> new AssertionError("No report for " + domain));
    }

    private static void writeBoundaries(Path path) throws Exception {
        List<String> lines = List.of(
                "venue,channel,session,firstSequence,lastSequence",
                boundaryRow(NYSE_DAY1), boundaryRow(NYSE_DAY2), boundaryRow(LSE_EQUITIES), boundaryRow(NASDAQ_DAY)
                // LSE_OPTIONS deliberately has no configured boundary.
        );
        Files.write(path, lines);
    }

    private static String boundaryRow(SequenceDomain d) {
        return String.join(",", d.venue(), d.channel(), d.session(), "1", String.valueOf(RECORDS_PER_DOMAIN));
    }

    /** Every sequence in [start, end] except those falling inside any of the given inclusive exclusion ranges. */
    private static List<Long> sequenceRange(long start, long end, List<long[]> excludedRanges) {
        return LongStream.rangeClosed(start, end)
                .filter(s -> excludedRanges.stream().noneMatch(r -> s >= r[0] && s <= r[1]))
                .boxed()
                .toList();
    }

    private static void appendDomain(List<String> rows, SequenceDomain domain, List<Long> sequences, List<Long> duplicates) {
        for (long s : sequences) rows.add(csvRow(domain, s, "ABC", 100, 1));
        for (long s : duplicates) rows.add(csvRow(domain, s, "ABC", 100, 1));
    }

    private static String csvRow(SequenceDomain domain, long sequence, String instrument, long priceMantissa, long quantity) {
        return String.join(",", domain.venue(), domain.channel(), domain.session(),
                Long.toString(sequence), "0", instrument, Long.toString(priceMantissa), Long.toString(quantity));
    }
}
