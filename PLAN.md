# PLAN.md

Production-readiness plan for `market-data-integrity`, derived from the README's "Recommended next production steps". Status reflects what's already landed (see README's "Architecture layer: boundaries, provenance, quarantine" section) versus what remains.

## Done

1. **Authoritative session-boundary metadata.** `SessionBoundaryProvider` supplies `expectedFirstSequence`/`expectedLastSequence` per `SequenceDomain`, so start/end gaps are detectable, not just internal ones. Default impl: `ConfiguredSessionBoundaryProvider` reading `config/session-boundaries.csv`.
2. **Quarantine sink for invalid records.** `QuarantineRepository` persists rejected records with validation reason and source provenance instead of only counting them. Default impl: `FileQuarantineRepository` writing `data/quarantine.tsv`.

## In progress / partial

3. **Persistent provenance/quality output.** `ProvenanceRepository` now writes an append-only audit log (`FileProvenanceRepository` -> `data/provenance.tsv`), but the quality report itself is still returned synchronously over HTTP rather than persisted. Remaining work:
   - Persist `QualityReport` (and its `Gap` list) to a queryable store instead of only the HTTP response body.
   - Replace the flat-file provenance/quarantine implementations with a production-grade store (Parquet, a database, or object storage) behind the existing `ProvenanceRepository`/`QuarantineRepository` interfaces — no venue/orchestration code should need to change.

## Remaining

4. **Job lifecycle APIs and async execution.** `ProcessingController.process` is currently synchronous over HTTP, unsuitable for multi-hour archive jobs.
   - Add a job submission endpoint returning a job ID immediately.
   - Add status/result polling endpoints (and/or a webhook/callback).
   - Run `HistoricalProcessingService.process` on a background executor; track job state (queued/running/completed/failed).

5. **Observability: Micrometer counters/timers.** No metrics are wired up yet beyond the default Actuator health/info/metrics endpoints.
   - Counters: records/sec, bytes/sec, gaps found, duplicates found, records rejected/quarantined.
   - Timers: external-sort run count and merge duration, end-to-end job duration.

6. **Venue-specific sequence semantics.** The current model assumes one monotonic sequence space per `SequenceDomain`.
   - Model packet-vs-message sequence numbers where a venue distinguishes them.
   - Handle sequence resets and wraparound.
   - Capture recovery/retransmission metadata so replayed records aren't misclassified as gaps or duplicates.

7. **Performance benchmarking.** Before tuning defaults for production scale:
   - Benchmark `market-data.sort.chunk-records` against representative record sizes and heap budgets.
   - Benchmark I/O buffer sizes for the external sort's run files.
   - Measure GC behavior and storage throughput under sustained load.
   - Use results to set production defaults and document guidance (currently `application.yml` only has a general "tune from heap budget" comment).

## Notes

- Items 4-7 are independent of each other and can be sequenced in any order; 5 (metrics) is cheap to land early and will make 7 (benchmarking) easier to evaluate.
- Any storage swap for item 3 should stay behind the existing `ProvenanceRepository`/`QuarantineRepository`/`SessionBoundaryProvider` interfaces, which were deliberately kept outside `venue/` for this reason.

## Design cleanups (from `HistoricalProcessingService` review)

Found during a SOLID/design review of `application/HistoricalProcessingService.java`. None are urgent; none block anything above.

8. **Hidden temporal coupling on `QuarantiningRecordFilter` counts.** `HistoricalProcessingService.process` reads `filter.readCount()`/`filter.quarantinedCount()` *after* `sorter.sort(filter)` returns. This is only correct because `ChunkedExternalSorter` happens to eagerly drain its whole input before returning a merged iterator — nothing in the `RecordSorter` interface guarantees that. A lazier/streaming sorter implementation would silently produce wrong counts.
   - Fix options: document the invariant loudly on `RecordSorter`, or have `sort()` return something that makes "fully consumed" explicit, or move counting to a stage that isn't order-dependent.
9. **Pipeline composition order isn't type-enforced.** The chain `QuarantiningRecordFilter -> sorter.sort(...) -> PeekingIterator -> DomainIterator` must be built in exactly that order. Getting it wrong compiles fine and only fails later, deep inside `StreamingGapDetector` (`"Input not sorted"`), far from the actual mistake.
   - Fix options: a small factory (e.g. `SortedDomainRecords.from(sortedIterator)`) that can only be constructed from something that's provably already sorted, or at least a code comment on `process()` spelling out the ordering invariant.
10. **`throws Exception` is too broad.** Both `HistoricalProcessingService.process` and `ProcessingController.process` declare `throws Exception`, so a bad CSV row, a sort I/O failure, and an unsupported venue all collapse into the same generic 500 response.
    - Fix: introduce a small exception hierarchy (e.g. `ProcessingException`, `UnsupportedVenueException`) and a `@ExceptionHandler` in `ProcessingController` (or a `@ControllerAdvice`) so callers can distinguish failure modes.
11. **Non-atomic side effects in `QuarantiningRecordFilter`.** On a rejected record it calls `quarantine.save(...)` then `provenance.append(...)` as two independent writes. If the second throws, the record is quarantined with no audit trail for it.
    - Fix: depends on what the real storage backends end up being (item 3) — worth revisiting together with that work rather than in isolation on the current flat-file implementations.
