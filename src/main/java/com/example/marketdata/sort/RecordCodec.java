package com.example.marketdata.sort;

import com.example.marketdata.domain.MarketDataRecord;
import com.example.marketdata.domain.SequenceDomain;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binary encoding for one run file: a string dictionary header, followed by fixed-width rows that reference
 * the dictionary by index instead of repeating strings inline. {@code venue}/{@code channel}/{@code session}/
 * {@code sourceFile} are typically shared by many records in a chunk (often the entire chunk, for
 * {@code sourceFile}), so writing each distinct string once — rather than on every record — cuts both the
 * bytes written and the per-record UTF-8 encode/decode cost. Fixed-width rows are also a prerequisite for
 * O(1) seeking to record N, should that ever be needed.
 */
final class RecordCodec {
    private RecordCodec() {
    }

    /**
     * Writes {@code rows} (already sorted) as a dictionary header followed by one fixed-width row per record.
     */
    static void writeRun(DataOutputStream out, List<MarketDataRecord> rows) throws IOException {
        Map<String, Integer> dictionary = new LinkedHashMap<>();
        for (MarketDataRecord r : rows) {
            internAll(dictionary, r);
        }

        out.writeInt(dictionary.size());
        for (String value : dictionary.keySet()) {
            out.writeUTF(value);
        }

        for (MarketDataRecord r : rows) {
            out.writeInt(dictionary.get(r.domain().venue()));
            out.writeInt(dictionary.get(r.domain().channel()));
            out.writeInt(dictionary.get(r.domain().session()));
            out.writeLong(r.sequence());
            out.writeLong(r.eventTimeNanos());
            out.writeInt(dictionary.get(r.instrument()));
            out.writeLong(r.priceMantissa());
            out.writeLong(r.quantity());
            out.writeInt(dictionary.get(r.sourceFile()));
            out.writeLong(r.sourceOffset());
        }
    }

    private static void internAll(Map<String, Integer> dictionary, MarketDataRecord r) {
        intern(dictionary, r.domain().venue());
        intern(dictionary, r.domain().channel());
        intern(dictionary, r.domain().session());
        intern(dictionary, r.instrument());
        intern(dictionary, r.sourceFile());
    }

    private static void intern(Map<String, Integer> dictionary, String value) {
        dictionary.putIfAbsent(value, dictionary.size());
    }

    /**
     * Reads a run file's dictionary header. Must be called exactly once, before any {@link #read}.
     */
    static String[] readDictionary(DataInputStream in) throws IOException {
        int size = in.readInt();
        String[] dictionary = new String[size];
        for (int i = 0; i < size; i++) {
            dictionary[i] = in.readUTF();
        }
        return dictionary;
    }

    /**
     * Reads one fixed-width row, resolving string fields against {@code dictionary}. Returns {@code null} at
     * end of file.
     */
    static MarketDataRecord read(DataInputStream in, String[] dictionary) throws IOException {
        int venueId;
        try {
            venueId = in.readInt();
        } catch (EOFException e) {
            return null;
        }
        int channelId = in.readInt();
        int sessionId = in.readInt();
        long sequence = in.readLong();
        long eventTimeNanos = in.readLong();
        int instrumentId = in.readInt();
        long priceMantissa = in.readLong();
        long quantity = in.readLong();
        int sourceFileId = in.readInt();
        long sourceOffset = in.readLong();

        var domain = new SequenceDomain(dictionary[venueId], dictionary[channelId], dictionary[sessionId]);
        return new MarketDataRecord(domain, sequence, eventTimeNanos, dictionary[instrumentId], priceMantissa,
                quantity, dictionary[sourceFileId], sourceOffset);
    }
}
