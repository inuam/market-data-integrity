package com.example.marketdata.sort;

import com.example.marketdata.domain.MarketDataRecord;

import java.util.Iterator;

public interface RecordSorter {
    Iterator<MarketDataRecord> sort(Iterator<MarketDataRecord> input) throws Exception;
}
