package com.example.marketdata.sort;

import com.example.marketdata.domain.MarketDataRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Bounded-memory external merge sorter. Sort key: domain, then sequence, then source provenance.
 * Classic two-phase external merge sort: (1) {@link #sort} reads the input in bounded chunks, sorts each chunk
 * in memory, and spills it to disk as a "run"; (2) {@link MergedIterator} k-way merges all runs, so the fully
 * sorted output is produced without ever holding more than one chunk (phase 1) or one record per run (phase 2)
 * in memory at once.
 */
@Component
public class ChunkedExternalSorter implements RecordSorter {
    private static final Logger log = LoggerFactory.getLogger(ChunkedExternalSorter.class);

    static final Comparator<MarketDataRecord> ORDER = Comparator.comparing(MarketDataRecord::domain)
            .thenComparingLong(MarketDataRecord::sequence)
            .thenComparing(MarketDataRecord::sourceFile)
            .thenComparingLong(MarketDataRecord::sourceOffset);

    private final int chunkRecords;

    public ChunkedExternalSorter(@Value("${market-data.sort.chunk-records:1000000}") int chunkRecords) {
        if (chunkRecords < 1) throw new IllegalArgumentException("chunkRecords must be positive");
        this.chunkRecords = chunkRecords;
    }

    @Override
    public Iterator<MarketDataRecord> sort(Iterator<MarketDataRecord> input) throws Exception {
        Path dir = Files.createTempDirectory("md-sort-");
        List<Path> runs = new ArrayList<>();
        StopWatch stopWatch = new StopWatch("chunk-sort-phase1");
        try {
            // Phase 1: chunk -> sort in memory -> spill to disk as its own sorted run file.
            while (input.hasNext()) {
                stopWatch.start("read-chunk (includes upstream validate)");
                List<MarketDataRecord> chunk = new ArrayList<>(chunkRecords);

                for (int i = 0; i < chunkRecords && input.hasNext(); i++) {
                    chunk.add(input.next());
                }
                stopWatch.stop();

                MarketDataRecord[] sorted = chunk.toArray(new MarketDataRecord[0]);
                stopWatch.start("parallel-sort");
                Arrays.parallelSort(sorted, ORDER); // fork/join across cores; falls back to sequential for small chunks
                stopWatch.stop();

                stopWatch.start("write-run");
                Path run = dir.resolve("run-%05d.bin".formatted(runs.size()));
                writeRun(run, Arrays.asList(sorted)); // spill to disk
                stopWatch.stop();

                runs.add(run);
            }
            if (log.isInfoEnabled()) {
                log.info("{}", stopWatch.prettyPrint());
            }
            return new MergedIterator(runs, dir);
        } catch (Exception e) {
            TempRuns.deleteTree(dir);
            throw e;
        }
    }

    private static void writeRun(Path file, List<MarketDataRecord> rows) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            RecordCodec.writeRun(out, rows);
        }
    }
}
