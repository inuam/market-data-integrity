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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

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
    private static final Logger log = LoggerFactory.getLogger(HistoricalProcessingService.class);

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
     * Reads, validates, sorts, and analyzes {@code path} domain-by-domain. {@link PeekingIterator} splits
     * the sorted stream into contiguous per-{@link SequenceDomain} groups via single-item lookahead, handing each
     * group's records to {@link GapDetector#analyze}.
     */
    public ProcessingResult process(Path path) throws Exception {
        VenueAdapter adapter = adapters.adapterFor(path);
        StopWatch stopWatch = new StopWatch(path.getFileName().toString());

        try (var stream = adapter.read(path)) { // opens a lazy stream (pointer to file)
            var filter = new QuarantiningRecordFilter(stream.iterator(), validator, quarantineRepo, provenanceRepo);

            stopWatch.start("sort (includes upstream read+validate)");
            Iterator<MarketDataRecord> sorted = sorter.sort(filter);
            stopWatch.stop();

            PeekingIterator domains = new PeekingIterator(sorted);

            List<QualityReport> reports = new ArrayList<>();

            while (domains.hasNext()) {
                stopWatch.start("group+boundary-lookup");
                DomainGroup group = domains.next();
                SessionBoundary boundary = boundaries.findBoundary(group.domain()).orElse(null);
                provenanceRepo.append(ProvenanceEvents.domainAnalysisStarted(group.domain(), boundary, path));
                stopWatch.stop();

                stopWatch.start("gap-detect (includes reading this domain's records)");
                QualityReport report = gapDetector.analyze(group.records(), boundary);
                stopWatch.stop();
                reports.add(report);

                stopWatch.start("provenance-write");
                for (Gap gap : report.gaps()) {
                    provenanceRepo.append(ProvenanceEvents.sequenceGap(group.domain(), gap, path));
                }
                provenanceRepo.append(ProvenanceEvents.domainAnalysisCompleted(group.domain(), report, path));
                stopWatch.stop();
            }

            if (log.isInfoEnabled()) {
                log.info("{}", stopWatch.prettyPrint());
            }

            return new ProcessingResult(filter.readCount(), filter.quarantinedCount(), List.copyOf(reports));
        }
    }
}
