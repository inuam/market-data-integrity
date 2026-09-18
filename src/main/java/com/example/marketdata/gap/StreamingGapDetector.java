package com.example.marketdata.gap;

import com.example.marketdata.domain.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * O(1) scan memory apart from retained gap ranges. Input must contain one domain and be sequence-sorted.
 */
@Component
public final class StreamingGapDetector implements GapDetector {
    @Override
    public QualityReport analyze(Iterator<MarketDataRecord> it, SessionBoundary boundary) {
        if (!it.hasNext()) throw new IllegalArgumentException("Empty domain");
        MarketDataRecord first = it.next();
        SequenceDomain domain = first.domain();
        if (boundary != null && !boundary.domain().equals(domain))
            throw new IllegalArgumentException("Boundary domain mismatch");

        long min = first.sequence(), max = min, previous = min, total = 1, unique = 1, duplicates = 0, missing = 0, largest = 0;
        List<Gap> gaps = new ArrayList<>();

        if (boundary != null && min > boundary.firstExpectedSequence()) {
            Gap g = new Gap(domain, boundary.firstExpectedSequence(), min - 1);
            gaps.add(g);
            missing += g.missingCount();
            largest = Math.max(largest, g.missingCount());
        }
        while (it.hasNext()) {
            MarketDataRecord r = it.next();
            total++;
            if (!domain.equals(r.domain())) throw new IllegalArgumentException("Mixed sequence domains");
            long seq = r.sequence();
            max = Math.max(max, seq);
            if (seq == previous) {
                duplicates++;
                continue;
            }
            if (seq < previous) throw new IllegalArgumentException("Input not sorted: " + seq + " after " + previous);
            unique++;
            if (seq > previous + 1) {
                Gap g = new Gap(domain, previous + 1, seq - 1);
                gaps.add(g);
                missing += g.missingCount();
                largest = Math.max(largest, g.missingCount());
            }
            previous = seq;
        }
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
