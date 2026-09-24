package com.example.marketdata.sort;

import com.example.marketdata.domain.MarketDataRecord;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

/**
 * Phase 2: k-way merge the sorted run files back into one stream. At any moment, we keep only ONE record
 * per run file in memory (via its {@link Cursor}) — never a whole file's worth. So memory use depends on
 * how many run files there are, not how many total records they contain, keeping merge-time memory
 * independent of input size.
 */
final class MergedIterator implements Iterator<MarketDataRecord> {
    private final PriorityQueue<Cursor> heap = new PriorityQueue<>((a, b) -> {
        int c = ChunkedExternalSorter.ORDER.compare(a.value, b.value);
        // Tie-break by run id so the heap has a deterministic total order even if two runs' current records tie.
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
        // Pop the smallest record, advance that run's cursor, and re-insert it only if it still has data.
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
        TempRuns.deleteTree(dir);
    }

    // One run file's current read position: the next unread record from that run, or null once it's exhausted.
    private static final class Cursor implements Closeable {
        final int id;
        final DataInputStream in;
        MarketDataRecord value;

        Cursor(int id, Path p) throws IOException {
            this.id = id;
            this.in = new DataInputStream(new BufferedInputStream(Files.newInputStream(p)));
            value = RecordCodec.read(in);
        }

        void advance() throws IOException {
            value = RecordCodec.read(in);
        }

        public void close() throws IOException {
            in.close();
        }
    }
}
