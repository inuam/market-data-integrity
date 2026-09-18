package com.example.marketdata.domain;

import java.util.List;

public record QualityReport(SequenceDomain domain,
                            Long expectedFirst, Long expectedLast,
                            long observedMin, long observedMax,
                            long totalRecords, long uniqueSequences, long duplicates,
                            long outOfOrder, long missingSequences, long largestGap,
                            List<Gap> gaps) { }
