package com.example.marketdata.sort;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;

/**
 * Binary encoding for one run file's records. Explicitly demo-grade (writeUTF-based); replace with a
 * fixed-width/columnar codec for production scale.
 */
final class RecordCodec {
    private RecordCodec() {
    }

    static void write(DataOutput out, MarketDataRecord r) throws IOException {
        out.writeUTF(r.domain().venue());
        out.writeUTF(r.domain().channel());
        out.writeUTF(r.domain().session());
        out.writeLong(r.sequence());
        out.writeLong(r.eventTimeNanos());
        out.writeUTF(r.instrument());
        out.writeLong(r.priceMantissa());
        out.writeLong(r.quantity());
        out.writeUTF(r.sourceFile());
        out.writeLong(r.sourceOffset());
    }

    static MarketDataRecord read(DataInput in) throws IOException {
        try {
            var d = new SequenceDomain(in.readUTF(), in.readUTF(), in.readUTF());
            return new MarketDataRecord(d, in.readLong(), in.readLong(), in.readUTF(), in.readLong(), in.readLong(), in.readUTF(), in.readLong());
        } catch (EOFException e) {
            return null;
        }
    }
}
