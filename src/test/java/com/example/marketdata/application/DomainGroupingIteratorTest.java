package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainGroupingIteratorTest {
    private final SequenceDomain a = new SequenceDomain("A", "1", "S");
    private final SequenceDomain b = new SequenceDomain("B", "1", "S");

    private MarketDataRecord r(SequenceDomain d, long s) {
        return new MarketDataRecord(d, s, 0, "ABC", 100, 1, "f", s);
    }

    @Test
    void shouldYieldOnlyRecordsBelongingToTheGroupsDomainGivenAGroupsIterator() {
        var groups = new DomainGroupingIterator(List.of(r(a, 1), r(a, 2), r(b, 1)).iterator());

        DomainGroup group = groups.next();

        List<Long> got = new ArrayList<>();
        while (group.records().hasNext()) got.add(group.records().next().sequence());

        assertThat(group.domain()).isEqualTo(a);
        assertThat(got).containsExactly(1L, 2L);
    }

    @Test
    void shouldExposeTheNextDomainsFirstRecordGivenThePreviousGroupWasFullyDrained() {
        var groups = new DomainGroupingIterator(List.of(r(a, 1), r(b, 1)).iterator());
        DomainGroup first = groups.next();
        while (first.records().hasNext()) first.records().next();

        assertThat(groups.hasNext()).isTrue();
        DomainGroup second = groups.next();

        assertThat(second.domain()).isEqualTo(b);
        assertThat(second.records().next().sequence()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenAskedForMoreThanTheGroupsDomainContains() {
        var groups = new DomainGroupingIterator(List.of(r(a, 1)).iterator());
        DomainGroup group = groups.next();
        group.records().next();

        assertThatThrownBy(group.records()::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldYieldEachDomainExactlyOnceGivenMultipleContiguousDomains() {
        var groups = new DomainGroupingIterator(List.of(r(a, 1), r(a, 2), r(b, 1), r(b, 2)).iterator());

        List<SequenceDomain> seen = new ArrayList<>();
        while (groups.hasNext()) {
            DomainGroup group = groups.next();
            seen.add(group.domain());
            while (group.records().hasNext()) group.records().next();
        }

        assertThat(seen).containsExactly(a, b);
    }

    @Test
    void shouldThrowWhenAskedForMoreGroupsThanTheSourceContains() {
        var groups = new DomainGroupingIterator(List.of(r(a, 1)).iterator());
        DomainGroup group = groups.next();
        while (group.records().hasNext()) group.records().next();

        assertThat(groups.hasNext()).isFalse();
        assertThatThrownBy(groups::next).isInstanceOf(NoSuchElementException.class);
    }
}
