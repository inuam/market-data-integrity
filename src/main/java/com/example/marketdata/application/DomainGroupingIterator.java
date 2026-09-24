package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Splits one sorted stream into consecutive per-domain groups, using single-item lookahead to detect
 * a domain boundary without consuming the next group's first record. Each group's iterator streams
 * lazily — never buffers a domain into a list — so memory stays O(1) per domain even when a single
 * session is too large to fit in RAM.
 * <p>
 * The caller must fully drain each group's iterator before calling {@link #next()} again; an abandoned
 * group leaves its unread records stuck behind it.
 */
final class DomainGroupingIterator implements Iterator<DomainGroup> {
    private final Iterator<MarketDataRecord> source;
    private MarketDataRecord nextItem;
    private boolean hasNextItem;

    DomainGroupingIterator(Iterator<MarketDataRecord> source) {
        this.source = source;
    }

    private MarketDataRecord peek() {
        if (!hasNextItem) {
            nextItem = source.next();
            hasNextItem = true;
        }
        return nextItem;
    }

    @Override
    public boolean hasNext() {
        return hasNextItem || source.hasNext();
    }

    @Override
    public DomainGroup next() {
        if (!hasNext()) throw new NoSuchElementException();
        SequenceDomain domain = peek().domain();

        return new DomainGroup(domain, new Iterator<>() {
            @Override
            public boolean hasNext() {
                return DomainGroupingIterator.this.hasNext() && peek().domain().equals(domain);
            }

            @Override
            public MarketDataRecord next() {
                if (!hasNext()) throw new NoSuchElementException();
                if (hasNextItem) {
                    hasNextItem = false;
                    return nextItem;
                }
                return source.next();
            }
        });
    }
}
