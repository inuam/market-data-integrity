package com.example.marketdata.application;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;

import java.util.Iterator;

record DomainGroup(SequenceDomain domain, Iterator<MarketDataRecord> records) {
}
