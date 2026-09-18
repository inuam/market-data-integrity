package com.example.marketdata.application;

import com.example.marketdata.domain.Gap;
import com.example.marketdata.domain.QualityReport;
import com.example.marketdata.domain.SequenceDomain;
import com.example.marketdata.domain.SessionBoundary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProvenanceEventsTest {
    private final SequenceDomain d = new SequenceDomain("X", "1", "S");
    private final Path path = Path.of("archive.csv");

    @Test void domainAnalysisStartedRecordsTheAuthoritativeBoundaryWhenPresent() {
        var boundary = new SessionBoundary(d, 100, 200);
        var event = ProvenanceEvents.domainAnalysisStarted(d, Optional.of(boundary), path);

        assertThat(event.type()).isEqualTo("DOMAIN_ANALYSIS_STARTED");
        assertThat(event.sequenceFrom()).isEqualTo(100L);
        assertThat(event.sequenceTo()).isEqualTo(200L);
        assertThat(event.detail()).isEqualTo("authoritative boundary available");
    }

    @Test void domainAnalysisStartedRecordsAbsenceOfABoundary() {
        var event = ProvenanceEvents.domainAnalysisStarted(d, Optional.empty(), path);

        assertThat(event.sequenceFrom()).isNull();
        assertThat(event.sequenceTo()).isNull();
        assertThat(event.detail()).isEqualTo("authoritative boundary unavailable");
    }

    @Test void sequenceGapCarriesTheGapsRangeAndMissingCount() {
        var gap = new Gap(d, 10, 12);
        var event = ProvenanceEvents.sequenceGap(d, gap, path);

        assertThat(event.type()).isEqualTo("SEQUENCE_GAP");
        assertThat(event.sequenceFrom()).isEqualTo(10L);
        assertThat(event.sequenceTo()).isEqualTo(12L);
        assertThat(event.detail()).isEqualTo("missing=3");
    }

    @Test void domainAnalysisCompletedSummarizesTheReport() {
        var report = new QualityReport(d, null, null, 1, 5, 5, 4, 1, 0, 0, 0, List.of());
        var event = ProvenanceEvents.domainAnalysisCompleted(d, report, path);

        assertThat(event.type()).isEqualTo("DOMAIN_ANALYSIS_COMPLETED");
        assertThat(event.sequenceFrom()).isEqualTo(1L);
        assertThat(event.sequenceTo()).isEqualTo(5L);
        assertThat(event.detail()).isEqualTo("records=5, duplicates=1, missing=0");
    }
}
