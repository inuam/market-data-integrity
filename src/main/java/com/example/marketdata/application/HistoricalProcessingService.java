package com.example.marketdata.application;

import com.example.marketdata.boundary.SessionBoundaryProvider;
import com.example.marketdata.domain.*;
import com.example.marketdata.gap.GapDetector;
import com.example.marketdata.provenance.ProvenanceRepository;
import com.example.marketdata.quality.RecordValidator;
import com.example.marketdata.quarantine.QuarantineRepository;
import com.example.marketdata.sort.RecordSorter;
import com.example.marketdata.venue.VenueAdapter;
import com.example.marketdata.venue.VenueAdapterRegistry;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Orchestrates the pipeline: validate+quarantine -> external sort -> group by {@link SequenceDomain} -> gap detection -> provenance.
 * The stage order in {@link #process} is a runtime invariant, not type-enforced — every stage takes/returns a plain
 * {@code Iterator<MarketDataRecord>}, so reordering or skipping a stage compiles fine and only fails deep inside
 * {@link com.example.marketdata.gap.StreamingGapDetector} with "Input not sorted".
 */
@Service
public final class HistoricalProcessingService {
    private final VenueAdapterRegistry adapters;
    private final RecordSorter sorter;
    private final GapDetector gapDetector;
    private final RecordValidator validator;
    private final SessionBoundaryProvider boundaries;
    private final ProvenanceRepository provenanceRepo;
    private final QuarantineRepository quarantineRepo;

    public HistoricalProcessingService(VenueAdapterRegistry adapters, RecordSorter sorter, GapDetector gapDetector,
                                       RecordValidator validator, SessionBoundaryProvider boundaries,
                                       ProvenanceRepository provenanceRepo, QuarantineRepository quarantineRepo) {
        this.adapters = adapters;
        this.sorter = sorter;
        this.gapDetector = gapDetector;
        this.validator = validator;
        this.boundaries = boundaries;
        this.provenanceRepo = provenanceRepo;
        this.quarantineRepo = quarantineRepo;
    }

    /**
     * Reads, validates, sorts, and analyzes {@code path} domain-by-domain. {@link PeekingIterator#peek()} lets each
     * loop iteration find the next domain's boundary without consuming its first record — that record is left
     * untouched for {@link DomainIterator} to hand to {@link GapDetector#analyze} as the group's actual first element.
     */
    public ProcessingResult process(Path path) throws Exception {
        VenueAdapter adapter = adapters.adapterFor(path);

        try (var stream = adapter.read(path)) { // opens a lazy stream (pointer to file)
            var filter = new QuarantiningRecordFilter(stream.iterator(), validator, quarantineRepo, provenanceRepo);

            Iterator<MarketDataRecord> sorted = sorter.sort(filter);
            PeekingIterator p = new PeekingIterator(sorted);

            List<QualityReport> reports = new ArrayList<>();

            while (p.hasNext()) {
                // DomainIterator streams just this domain's records — never buffers a domain into a list —
                // so memory stays O(1) per domain even when a single session is too large to fit in RAM.
                SequenceDomain d = p.peek().domain();
                SessionBoundary boundary = boundaries.findBoundary(d).orElse(null);
                provenanceRepo.append(ProvenanceEvents.domainAnalysisStarted(d, boundary, path));

                QualityReport report = gapDetector.analyze(new DomainIterator(p, d), boundary);
                reports.add(report);
                for (Gap g : report.gaps()) {
                    provenanceRepo.append(ProvenanceEvents.sequenceGap(d, g, path));
                }
                provenanceRepo.append(ProvenanceEvents.domainAnalysisCompleted(d, report, path));
            }

            return new ProcessingResult(filter.readCount(), filter.quarantinedCount(), List.copyOf(reports));
        }
    }
}
