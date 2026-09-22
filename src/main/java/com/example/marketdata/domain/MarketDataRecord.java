package com.example.marketdata.domain;

/**
 * One decoded market-data message, already tagged with the {@link SequenceDomain} it belongs to.
 *
 * @param domain         the (venue, channel, session) sequence space this record belongs to; sorting and
 *                        gap/duplicate detection are only ever performed within one domain, never across domains
 * @param sequence       venue-assigned message sequence number; the primary sort key, and the value
 *                        {@link com.example.marketdata.gap.StreamingGapDetector} scans to find gaps and duplicates
 * @param eventTimeNanos venue-reported event time in nanoseconds; carried through as payload only — not used by
 *                        sorting or gap detection
 * @param instrument     traded instrument identifier (e.g. ticker); must be non-blank, enforced by
 *                        {@link com.example.marketdata.quality.RecordValidator}
 * @param priceMantissa  fixed-point price, scaled by a venue-defined factor to avoid floating-point rounding;
 *                        payload only, not used by sorting or gap detection
 * @param quantity       traded/quoted quantity; must be non-negative, enforced by
 *                        {@link com.example.marketdata.quality.RecordValidator}
 * @param sourceFile     path of the archive file this record was decoded from; used as a sort tie-breaker and
 *                        recorded in quarantine/provenance entries for traceability
 * @param sourceOffset   logical position (e.g. line number) of this record within {@code sourceFile}; the final
 *                        sort tie-breaker, so records with equal sequence numbers stay in original file order
 */
public record MarketDataRecord(
        SequenceDomain domain,
        long sequence,
        long eventTimeNanos,
        String instrument,
        long priceMantissa,
        long quantity,
        String sourceFile,
        long sourceOffset) {
}
