package com.example.marketdata.quality;

import com.example.marketdata.domain.MarketDataRecord;

/**
 * Cheap per-record check run before the expensive external-sort step. A record that fails is diverted to
 * quarantine (see {@code QuarantiningRecordFilter}) instead of reaching the sorter or gap detector.
 */
public interface RecordValidator {
    ValidationResult validate(MarketDataRecord record);
}
