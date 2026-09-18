package com.example.marketdata.application;

import com.example.marketdata.domain.QualityReport;
import java.util.List;

public record ProcessingResult(long inputRecords, long rejectedRecords, List<QualityReport> reports) { }
