package com.example.marketdata.quality;

import com.example.marketdata.domain.MarketDataRecord;
import org.springframework.stereotype.Component;

@Component
public class BasicRecordValidator implements RecordValidator {
    public ValidationResult validate(MarketDataRecord r) {
        if (r.sequence() < 0) return ValidationResult.invalid("negative sequence");
        if (r.instrument() == null || r.instrument().isBlank()) return ValidationResult.invalid("missing instrument");
        if (r.quantity() < 0) return ValidationResult.invalid("negative quantity");
        return ValidationResult.ok();
    }
}
