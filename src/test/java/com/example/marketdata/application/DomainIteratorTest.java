package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainIteratorTest {
    private final SequenceDomain a = new SequenceDomain("A", "1", "S");
    private final SequenceDomain b = new SequenceDomain("B", "1", "S");

    private MarketDataRecord r(SequenceDomain d, long s) {
        return new MarketDataRecord(d, s, 0, "ABC", 100, 1, "f", s);
    }

    @Test
    void yieldsOnlyRecordsBelongingToTheGivenDomain() {
        var peeking = new PeekingIterator(List.of(r(a, 1), r(a, 2), r(b, 1)).iterator());
        var domainIt = new DomainIterator(peeking, a);

        List<Long> got = new ArrayList<>();
        while (domainIt.hasNext()) got.add(domainIt.next().sequence());

        assertThat(got).containsExactly(1L, 2L);
    }

    @Test
    void leavesTheNextDomainsFirstRecordUnconsumedForTheCaller() {
        var peeking = new PeekingIterator(List.of(r(a, 1), r(b, 1)).iterator());
        var domainIt = new DomainIterator(peeking, a);
        while (domainIt.hasNext()) domainIt.next();

        assertThat(peeking.peek().domain()).isEqualTo(b);
    }

    @Test
    void throwsWhenAskedForMoreThanTheDomainContains() {
        var peeking = new PeekingIterator(List.of(r(a, 1)).iterator());
        var domainIt = new DomainIterator(peeking, a);
        domainIt.next();

        assertThatThrownBy(domainIt::next).isInstanceOf(NoSuchElementException.class);
    }
}
