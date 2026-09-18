package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Yields records from a PeekingIterator only while they still belong to one SequenceDomain. */
final class DomainIterator implements Iterator<MarketDataRecord> {
    private final PeekingIterator source;
    private final SequenceDomain domain;

    DomainIterator(PeekingIterator source, SequenceDomain domain) {
        this.source = source; this.domain = domain;
    }

    @Override public boolean hasNext() { return source.hasNext() && source.peek().domain().equals(domain); }
    @Override public MarketDataRecord next() {
        if (!hasNext()) throw new NoSuchElementException();
        return source.next();
    }
}
