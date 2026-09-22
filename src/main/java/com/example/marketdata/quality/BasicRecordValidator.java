package com.example.marketdata.quality;

import com.example.marketdata.domain.MarketDataRecord;
import org.springframework.stereotype.Component;

/**
 * Structural checks only — no cross-record or domain-specific rules, since a per-record validator running
 * before sorting/grouping deliberately has no visibility into other records or session context.
 */
@Component
public class BasicRecordValidator implements RecordValidator {
    public ValidationResult validate(MarketDataRecord r) {
        if (r.sequence() < 0) return ValidationResult.invalid("negative sequence");
        if (r.instrument() == null || r.instrument().isBlank()) return ValidationResult.invalid("missing instrument");
        if (r.quantity() < 0) return ValidationResult.invalid("negative quantity");
        return ValidationResult.ok();
    }
}
