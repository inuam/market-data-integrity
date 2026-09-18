package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PeekingIteratorTest {
    private final SequenceDomain d = new SequenceDomain("X", "1", "S");
    private MarketDataRecord r(long s) { return new MarketDataRecord(d, s, 0, "ABC", 100, 1, "f", s); }

    @Test void peekingReturnsTheNextElementWithoutConsumingIt() {
        var it = new PeekingIterator(List.of(r(1), r(2)).iterator());
        assertThat(it.peek().sequence()).isEqualTo(1);
        assertThat(it.peek().sequence()).isEqualTo(1);
        assertThat(it.next().sequence()).isEqualTo(1);
        assertThat(it.next().sequence()).isEqualTo(2);
    }

    @Test void hasNextIsTrueWhileAPeekedElementIsPending() {
        var it = new PeekingIterator(List.of(r(1)).iterator());
        it.peek();
        assertThat(it.hasNext()).isTrue();
        it.next();
        assertThat(it.hasNext()).isFalse();
    }

    @Test void behavesLikeAPlainIteratorWhenNeverPeeked() {
        var it = new PeekingIterator(List.of(r(1), r(2)).iterator());
        assertThat(it.next().sequence()).isEqualTo(1);
        assertThat(it.next().sequence()).isEqualTo(2);
        assertThat(it.hasNext()).isFalse();
    }
}
