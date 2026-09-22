package com.example.marketdata.venue;

import com.example.marketdata.domain.MarketDataRecord;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Extension point: add a Spring bean per venue/feed format.
 * Adapters know nothing about persistence, validation, or quality logic — they only decode a source file into
 * {@link MarketDataRecord}s. {@link VenueAdapterRegistry} picks the right adapter per input file via {@link #supports}.
 */
public interface VenueAdapter {
    /** Venue identifier attached to every record this adapter produces (part of {@code SequenceDomain}). */
    String venue();

    /** Whether this adapter can decode the given file — used by {@link VenueAdapterRegistry} to select an adapter. */
    boolean supports(Path path);

    /** Lazily decodes {@code path} into records; the returned stream should be closed by the caller (try-with-resources). */
    Stream<MarketDataRecord> read(Path path) throws Exception;
}
