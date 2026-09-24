# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Instructions
1. Always ask for approval when make changes providing the diffs
2. Keep code clean with clear separation of concerns
3. Use SOLID principles to keep code clean
4. Always write unit and integration that test behaviour and not methods. Write the in a BDD style
5. 

## Commands

```bash
mvn test                 # run all tests (JUnit 5 + AssertJ)
mvn test -Dtest=StreamingGapDetectorTest              # run one test class
mvn test -Dtest=StreamingGapDetectorTest#findsGapsAndDuplicates  # run one test method
mvn spring-boot:run       # run the app locally (port 8080)
```

This project builds with `release 25` (see `pom.xml`). If the default JDK on `PATH`/`JAVA_HOME` is older, point Maven at a JDK 25 install for every command, e.g.:

```bash
JAVA_HOME="C:\dev\java\25" PATH="C:\dev\java\25\bin:$PATH" mvn test
```

Trigger a processing run against a local file:

```bash
curl -X POST http://localhost:8080/api/v1/historical/process \
  -H 'Content-Type: application/json' \
  -d '{"path":"/absolute/path/to/data.csv"}'
```

## Architecture

Fixed pipeline, one direction, no cycles:

```
VenueAdapter -> RecordValidator -> quarantine (invalid records) -> RecordSorter (external merge)
             -> group by SequenceDomain -> SessionBoundaryProvider lookup -> GapDetector
             -> ProvenanceRepository (append-only audit) -> QualityReport
```

`HistoricalProcessingService` (`application/`) wires this together. It lazily filters invalid records into quarantine via a custom `Iterator` wrapper *before* they reach the sorter, then feeds the sorted stream through `DomainGroupingIterator`, which splits it into contiguous per-`SequenceDomain` groups (via single-item lookahead) for the gap detector — this is the key mechanism to understand before touching that class.

A `SequenceDomain` is `(venue, channel, session)`. Sequence numbers are only meaningful within one domain; unrelated domains must never be compared or merged.

### Package boundaries (each is a one-way extension point)

- `venue/` — `VenueAdapter` implementations decode a source format into `MarketDataRecord`. Add a new feed by implementing this interface as a Spring bean and registering it in `VenueAdapterRegistry`. Adapters know nothing about persistence, validation, or quality logic.
- `quality/` — `RecordValidator` runs cheap per-record checks *before* the expensive sort.
- `sort/` — `ChunkedExternalSorter` is a bounded-memory external merge sort: input is chunked to `market-data.sort.chunk-records` records, each chunk sorted and spilled to a temp binary run (`DataOutputStream.writeUTF`-based codec, explicitly noted as demo-only — replace with a fixed-width/columnar codec for production scale), then k-way merged via a `PriorityQueue` of one-record-per-run cursors. Sort key is `(domain, sequence, sourceFile, sourceOffset)`.
- `gap/` — `StreamingGapDetector` does an O(1)-memory-apart-from-gaps scan over one already-sorted, single-domain iterator. It throws `IllegalArgumentException` if it observes mixed domains or unsorted input — callers (i.e. `HistoricalProcessingService`) are responsible for that guarantee, the detector does not defend against it.
- `boundary/` — `SessionBoundaryProvider` supplies authoritative `(firstExpectedSequence, lastExpectedSequence)` per domain, so start/end gaps are detectable, not just internal ones. Default impl (`ConfiguredSessionBoundaryProvider`) reads `config/session-boundaries.csv`.
- `provenance/` — `ProvenanceRepository` is an append-only audit log of processing events (quarantine, domain start/complete, gaps found). Default impl writes `data/provenance.tsv`.
- `quarantine/` — `QuarantineRepository` persists rejected records with validation reason. Default impl writes `data/quarantine.tsv`.
- `application/` — orchestration only (`HistoricalProcessingService`); no venue/format/storage knowledge.
- `api/` — HTTP boundary only (`ProcessingController`); no pipeline logic.
- `domain/` — immutable records (`MarketDataRecord`, `SequenceDomain`, `Gap`, `SessionBoundary`, `QualityReport`) and provenance fields.

The `boundary/provenance/quarantine` interfaces live outside `venue/` deliberately, so file-backed implementations can later be swapped for JDBC/Kafka/object-storage without touching venue adapters or the orchestration contract.

## Config

`src/main/resources/application.yml` — `market-data.sort.chunk-records`, `market-data.boundaries-file`, `market-data.provenance-file`, `market-data.quarantine-file`. Tune `chunk-records` from heap budget and record size; it bounds each in-memory sort run.

## CSV demo format

`venue,channel,session,sequence,eventTimeNanos,instrument,priceMantissa,quantity` — see `sample.csv` for examples and `CsvVenueAdapter` for the parser.

## Known production gaps

Tracked in [PLAN.md](PLAN.md). Summary:

- No job lifecycle/async API for multi-hour archive jobs — `process` is synchronous over HTTP.
- No Micrometer metrics wired up yet (records/sec, gaps, duplicates, run count, merge duration).
- No venue-specific sequence semantics (resets, wraparound, packet-vs-message sequence) — the current model assumes a single monotonic sequence space per domain.
- `QualityReport` is returned over HTTP rather than persisted — can be large for big archives. Provenance/quarantine are already persisted (flat-file), but not yet in a production-grade store.
- No performance benchmarking done yet for `chunk-records`, I/O buffer sizes, GC, or storage throughput.
