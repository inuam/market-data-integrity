package com.example.marketdata.gap;

import com.example.marketdata.domain.*;

import java.util.*;

public interface GapDetector {
    QualityReport analyze(Iterator<MarketDataRecord> sortedDomainRecords, SessionBoundary boundary);
}
