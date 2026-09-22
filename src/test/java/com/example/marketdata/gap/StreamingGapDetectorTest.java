package com.example.marketdata.gap;

import com.example.marketdata.domain.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingGapDetectorTest {
    private final SequenceDomain d = new SequenceDomain("X", "1", "S");

    private MarketDataRecord r(long s) {
        return new MarketDataRecord(d, s, 0, "ABC", 100, 1, "f", s);
    }

    @Test
    void shouldFindsGapsAndDuplicates() {
        // Given
        var report = new StreamingGapDetector().analyze(List.of(r(100), r(101), r(101), r(104), r(105), r(109)).iterator(), null);

        // When // Then
        assertThat(report.duplicates()).isEqualTo(1);
        assertThat(report.missingSequences()).isEqualTo(5);
        assertThat(report.gaps()).containsExactly(new Gap(d, 102, 103), new Gap(d, 106, 108));
    }

    @Test
    void shouldDetectMissingEdgesFromAuthoritativeBoundary() {
        // Given
        var boundary = new SessionBoundary(d, 98, 111);

        // When
        var report = new StreamingGapDetector().analyze(List.of(r(100), r(101), r(104), r(105), r(109)).iterator(), boundary);

        // Then
        assertThat(report.expectedFirst()).isEqualTo(98);
        assertThat(report.expectedLast()).isEqualTo(111);
        assertThat(report.gaps()).containsExactly(new Gap(d, 98, 99), new Gap(d, 102, 103), new Gap(d, 106, 108), new Gap(d, 110, 111));
        assertThat(report.missingSequences()).isEqualTo(9);
    }
}
