package com.example.marketdata.quarantine;

import com.example.marketdata.domain.MarketDataRecord;
import java.time.Instant;

public record QuarantinedRecord(Instant quarantinedAt, MarketDataRecord record,
                                String reason, String validator) { }
