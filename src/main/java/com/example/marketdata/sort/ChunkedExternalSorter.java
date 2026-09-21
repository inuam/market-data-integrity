package com.example.marketdata.sort;

import com.example.marketdata.domain.MarketDataRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Bounded-memory external merge sorter. Sort key: domain, then sequence, then source provenance.
 */
@Component
public class ChunkedExternalSorter implements RecordSorter {
    private static final Comparator<MarketDataRecord> ORDER = Comparator.comparing(MarketDataRecord::domain)
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
            while (input.hasNext()) {
                ArrayList<MarketDataRecord> chunk = new ArrayList<>(chunkRecords);

                for (int i = 0; i < chunkRecords && input.hasNext(); i++) {
                    chunk.add(input.next());
                }

                chunk.sort(ORDER);
                Path run = dir.resolve("run-%05d.bin".formatted(runs.size()));
                writeRun(run, chunk);
                runs.add(run);
            }
            return new MergedIterator(runs, dir);
        } catch (Exception e) {
            deleteTree(dir);
            throw e;
        }
    }

    private static void writeRun(Path file, List<MarketDataRecord> rows) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            for (MarketDataRecord r : rows) write(out, r);
        }
    }

    private static void write(DataOutput out, MarketDataRecord r) throws IOException {
        out.writeUTF(r.domain().venue());
        out.writeUTF(r.domain().channel());
        out.writeUTF(r.domain().session());
        out.writeLong(r.sequence());
        out.writeLong(r.eventTimeNanos());
        out.writeUTF(r.instrument());
        out.writeLong(r.priceMantissa());
        out.writeLong(r.quantity());
        out.writeUTF(r.sourceFile());
        out.writeLong(r.sourceOffset());
    }

    private static MarketDataRecord read(DataInput in) throws IOException {
        try {
            var d = new com.example.marketdata.domain.SequenceDomain(in.readUTF(), in.readUTF(), in.readUTF());
            return new MarketDataRecord(d, in.readLong(), in.readLong(), in.readUTF(), in.readLong(), in.readLong(), in.readUTF(), in.readLong());
        } catch (EOFException e) {
            return null;
        }
    }

    private static void deleteTree(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static final class Cursor implements Closeable {
        final int id;
        final DataInputStream in;
        MarketDataRecord value;

        Cursor(int id, Path p) throws IOException {
            this.id = id;
            this.in = new DataInputStream(new BufferedInputStream(Files.newInputStream(p)));
            value = read(in);
        }

        void advance() throws IOException {
            value = read(in);
        }

        public void close() throws IOException {
            in.close();
        }
    }

    private static final class MergedIterator implements Iterator<MarketDataRecord> {
        private final PriorityQueue<Cursor> heap = new PriorityQueue<>((a, b) -> {
            int c = ORDER.compare(a.value, b.value);
            return c != 0 ? c : Integer.compare(a.id, b.id);
        });
        private final List<Cursor> cursors = new ArrayList<>();
        private final Path dir;

        MergedIterator(List<Path> runs, Path dir) throws IOException {
            this.dir = dir;
            for (int i = 0; i < runs.size(); i++) {
                Cursor c = new Cursor(i, runs.get(i));
                cursors.add(c);
                if (c.value != null) heap.add(c);
            }
            if (heap.isEmpty()) cleanup();
        }

        public boolean hasNext() {
            return !heap.isEmpty();
        }

        public MarketDataRecord next() {
            if (heap.isEmpty()) throw new NoSuchElementException();
            Cursor c = heap.remove();
            MarketDataRecord out = c.value;
            try {
                c.advance();
                if (c.value != null) heap.add(c);
                else {
                    c.close();
                    if (heap.isEmpty()) cleanup();
                }
            } catch (IOException e) {
                cleanup();
                throw new UncheckedIOException(e);
            }
            return out;
        }

        private void cleanup() {
            for (Cursor c : cursors)
                try {
                    c.close();
                } catch (IOException ignored) {
                }
            deleteTree(dir);
        }
    }
}
