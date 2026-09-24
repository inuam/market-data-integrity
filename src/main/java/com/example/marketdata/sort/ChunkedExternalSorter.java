package com.example.marketdata.sort;

import com.example.marketdata.domain.MarketDataRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        try {
            // Phase 1: chunk -> sort in memory -> spill to disk as its own sorted run file.
            while (input.hasNext()) {
                ArrayList<MarketDataRecord> chunk = new ArrayList<>(chunkRecords);

                for (int i = 0; i < chunkRecords && input.hasNext(); i++) {
                    chunk.add(input.next());
                }

                chunk.sort(ORDER);
                Path run = dir.resolve("run-%05d.bin".formatted(runs.size()));
                writeRun(run, chunk); // spill to disk
                runs.add(run);
            }
            return new MergedIterator(runs, dir);
        } catch (Exception e) {
            TempRuns.deleteTree(dir);
            throw e;
        }
    }

    private static void writeRun(Path file, List<MarketDataRecord> rows) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            for (MarketDataRecord r : rows) RecordCodec.write(out, r);
        }
    }
}
