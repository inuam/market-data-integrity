package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;

import java.util.Iterator;

/**
 * Single-item lookahead so the current record's domain can be inspected before consuming it.
 */
final class PeekingIterator implements Iterator<MarketDataRecord> {
    private final Iterator<MarketDataRecord> delegate;
    private MarketDataRecord peeked;
    private boolean hasPeeked;

    PeekingIterator(Iterator<MarketDataRecord> delegate) {
        this.delegate = delegate;
    }

    MarketDataRecord peek() {
        if (!hasPeeked) {
            peeked = delegate.next();
            hasPeeked = true;
        }
        return peeked;
    }

    @Override
    public boolean hasNext() {
        return hasPeeked || delegate.hasNext();
    }

    @Override
    public MarketDataRecord next() {
        if (hasPeeked) {
            hasPeeked = false;
            return peeked;
        }
        return delegate.next();
    }
}
