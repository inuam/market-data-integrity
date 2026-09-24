package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;

import java.util.Iterator;

/**
 * Single-item lookahead so the current record's domain can be inspected before consuming it.
 */
final class PeekingIterator implements Iterator<MarketDataRecord> {
    private final Iterator<MarketDataRecord> delegate;
    private MarketDataRecord nextItem;
    private boolean hasNextItem;

    PeekingIterator(Iterator<MarketDataRecord> delegate) {
        this.delegate = delegate;
    }

    MarketDataRecord peek() {
        if (!hasNextItem) {
            nextItem = delegate.next();
            hasNextItem = true;
        }
        return nextItem;
    }

    @Override
    public boolean hasNext() {
        return hasNextItem || delegate.hasNext();
    }

    @Override
    public MarketDataRecord next() {
        if (hasNextItem) {
            hasNextItem = false;
            return nextItem;
        }
        return delegate.next();
    }
}
