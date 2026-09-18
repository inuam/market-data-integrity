package com.example.marketdata.application;

import com.example.marketdata.boundary.SessionBoundaryProvider;
import com.example.marketdata.domain.Gap;
import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.QualityReport;
import com.example.marketdata.domain.SequenceDomain;
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

@Service
public final class HistoricalProcessingService {
    private final VenueAdapterRegistry adapters;
    private final RecordSorter sorter;
    private final GapDetector gapDetector;
    private final RecordValidator validator;
    private final SessionBoundaryProvider boundaries;
    private final ProvenanceRepository provenance;
    private final QuarantineRepository quarantine;

    public HistoricalProcessingService(VenueAdapterRegistry adapters, RecordSorter sorter, GapDetector gapDetector,
                                       RecordValidator validator, SessionBoundaryProvider boundaries,
                                       ProvenanceRepository provenance, QuarantineRepository quarantine) {
        this.adapters = adapters;
        this.sorter = sorter;
        this.gapDetector = gapDetector;
        this.validator = validator;
        this.boundaries = boundaries;
        this.provenance = provenance;
        this.quarantine = quarantine;
    }

    public ProcessingResult process(Path path) throws Exception {
        VenueAdapter adapter = adapters.adapterFor(path);
        try (var stream = adapter.read(path)) {
            var filter = new QuarantiningRecordFilter(stream.iterator(), validator, quarantine, provenance);
            Iterator<MarketDataRecord> sorted = sorter.sort(filter);
            List<QualityReport> reports = new ArrayList<>();
            PeekingIterator p = new PeekingIterator(sorted);
            while (p.hasNext()) {
                SequenceDomain d = p.peek().domain();
                var boundary = boundaries.findBoundary(d);
                provenance.append(ProvenanceEvents.domainAnalysisStarted(d, boundary, path));
                QualityReport report = gapDetector.analyze(new DomainIterator(p, d), boundary.orElse(null));
                reports.add(report);
                for (Gap g : report.gaps()) provenance.append(ProvenanceEvents.sequenceGap(d, g, path));
                provenance.append(ProvenanceEvents.domainAnalysisCompleted(d, report, path));
            }
            return new ProcessingResult(filter.readCount(), filter.quarantinedCount(), List.copyOf(reports));
        }
    }
}
