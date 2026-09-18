package com.example.marketdata.venue;

import com.example.marketdata.domain.MarketDataRecord;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Extension point: add a Spring bean per venue/feed format. */
public interface VenueAdapter {
    String venue();
    boolean supports(Path path);
    Stream<MarketDataRecord> read(Path path) throws Exception;
}
