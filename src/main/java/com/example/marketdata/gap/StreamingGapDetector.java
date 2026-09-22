package com.example.marketdata.gap;

import com.example.marketdata.domain.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * O(1) scan memory apart from retained gap ranges. Input must contain one domain and be sequence-sorted.
 * Single trailing-pointer linear scan (the technique behind LeetCode 163 "Missing Ranges"): each record is
 * compared only to the previous distinct sequence number, so gaps and duplicates are found in one pass without
 * ever looking back further than that.
 */
@Component
public final class StreamingGapDetector implements GapDetector {

    @Override
    public QualityReport analyze(Iterator<MarketDataRecord> it, SessionBoundary boundary) {

        if (!it.hasNext()) throw new IllegalArgumentException("Empty domain");

        // Read outside the loop to seed domain/min/previous — the loop's comparisons are meaningless without a first value.
        MarketDataRecord first = it.next();
        SequenceDomain domain = first.domain();
        if (boundary != null && !boundary.domain().equals(domain))
            throw new IllegalArgumentException("Boundary domain mismatch");

        long min = first.sequence(), max = min, previous = min, total = 1, unique = 1, duplicates = 0, missing = 0, largest = 0;
        List<Gap> gaps = new ArrayList<>();

        // Start-of-session gap: nothing in the stream precedes the first record, so only the authoritative boundary can reveal this.
        if (boundary != null && min > boundary.firstExpectedSequence()) {
            Gap g = new Gap(domain, boundary.firstExpectedSequence(), min - 1);
            gaps.add(g);
            missing += g.missingCount();
            largest = Math.max(largest, g.missingCount());
        }
        while (it.hasNext()) {
            MarketDataRecord marketDataRecord = it.next();
            total++;
            if (!domain.equals(marketDataRecord.domain())) throw new IllegalArgumentException("Mixed sequence domains");
            long currentSeq = marketDataRecord.sequence();
            max = Math.max(max, currentSeq);
            if (currentSeq == previous) {
                // Counted for the report, but deliberately not folded into unique/previous/gap-detection state —
                // a repeat arrival must not look like a new distinct sequence number.
                duplicates++;
                continue;
            }

            if (currentSeq < previous) throw new IllegalArgumentException("Input not sorted: " + currentSeq + " after " + previous);

            unique++;
            if (currentSeq > previous + 1) {
                Gap g = new Gap(domain, previous + 1, currentSeq - 1);
                gaps.add(g);
                missing += g.missingCount();
                largest = Math.max(largest, g.missingCount());
            }
            previous = currentSeq;
        }
        // End-of-session gap: mirrors the start-of-session check, using the last sequence number actually observed.
        if (boundary != null && max < boundary.lastExpectedSequence()) {
            Gap g = new Gap(domain, max + 1, boundary.lastExpectedSequence());
            gaps.add(g);
            missing += g.missingCount();
            largest = Math.max(largest, g.missingCount());
        }
        return new QualityReport(domain,
                boundary == null ? null : boundary.firstExpectedSequence(), boundary == null ? null : boundary.lastExpectedSequence(),
                min, max, total, unique, duplicates, 0, missing, largest, List.copyOf(gaps));
    }
}
