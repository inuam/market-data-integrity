package com.example.marketdata.quality;

import com.example.marketdata.domain.MarketDataRecord;

public interface RecordValidator { ValidationResult validate(MarketDataRecord record); }
