package com.example.marketdata.application;

import com.example.marketdata.domain.Gap;
import com.example.marketdata.domain.QualityReport;
import com.example.marketdata.domain.SequenceDomain;
import com.example.marketdata.domain.SessionBoundary;
import com.example.marketdata.provenance.ProvenanceEvent;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Builds the ProvenanceEvents emitted during per-domain gap analysis.
 */
final class ProvenanceEvents {
    private ProvenanceEvents() {
    }

    static ProvenanceEvent domainAnalysisStarted(SequenceDomain domain, Optional<SessionBoundary> boundary, Path path) {
        return new ProvenanceEvent(Instant.now(), "DOMAIN_ANALYSIS_STARTED", domain,
                boundary.map(SessionBoundary::firstExpectedSequence).orElse(null),
                boundary.map(SessionBoundary::lastExpectedSequence).orElse(null),
                path.toString(), null,
                boundary.isPresent() ? "authoritative boundary available" : "authoritative boundary unavailable");
    }

    static ProvenanceEvent sequenceGap(SequenceDomain domain, Gap gap, Path path) {
        return new ProvenanceEvent(Instant.now(), "SEQUENCE_GAP", domain, gap.fromInclusive(), gap.toInclusive(),
                path.toString(), null, "missing=" + gap.missingCount());
    }

    static ProvenanceEvent domainAnalysisCompleted(SequenceDomain domain, QualityReport report, Path path) {
        return new ProvenanceEvent(Instant.now(), "DOMAIN_ANALYSIS_COMPLETED", domain,
                report.observedMin(), report.observedMax(), path.toString(), null,
                "records=" + report.totalRecords() + ", duplicates=" + report.duplicates() + ", missing=" + report.missingSequences());
    }
}
