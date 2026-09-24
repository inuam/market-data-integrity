package com.example.marketdata.venue;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Demo adapter. CSV: venue,channel,session,sequence,eventTimeNanos,instrument,priceMantissa,quantity
 */
@Component
public class CsvVenueAdapter implements VenueAdapter {
    private static final Logger log = LoggerFactory.getLogger(CsvVenueAdapter.class);

    @Override
    public String venue() {
        return "CSV-DEMO";
    }

    @Override
    public boolean supports(Path path) {
        return path.getFileName().toString().endsWith(".csv");
    }

    @Override
    public Stream<MarketDataRecord> read(Path path) throws Exception {
        BufferedReader reader = Files.newBufferedReader(path);
        AtomicLong offset = new AtomicLong();

        // Streamed lazily line-by-line (never loaded fully into memory) — offset is the logical line number,
        // used as a sort tie-breaker (see ChunkedExternalSorter.ORDER) so equal-sequence records stay in file order.
        return reader.lines()
                .filter(s -> !s.isBlank() && !s.startsWith("#"))
                .map(line -> {
            long n = offset.getAndIncrement();
            String[] p = line.split(",", -1);

            if (p.length != 8) {
                log.error("Bad CSV record in {} at logical offset {}: expected 8 fields, got {}", path, n, p.length);
                throw new IllegalArgumentException("Bad CSV record at logical offset " + n);
            }

            return new MarketDataRecord(new SequenceDomain(p[0], p[1], p[2]), Long.parseLong(p[3]),
                    Long.parseLong(p[4]), p[5], Long.parseLong(p[6]), Long.parseLong(p[7]), path.toString(), n);
        }).onClose(() -> {
            // Ties the reader's lifetime to the returned stream's, so closing the stream (try-with-resources) closes the file.
            try {
                reader.close();
            } catch (Exception ignored) {
            }
        });
    }
}
