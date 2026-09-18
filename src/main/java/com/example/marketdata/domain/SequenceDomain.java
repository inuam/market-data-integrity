package com.example.marketdata.domain;

import java.util.Objects;

public record SequenceDomain(String venue, String channel, String session) implements Comparable<SequenceDomain> {
    public SequenceDomain {
        Objects.requireNonNull(venue); Objects.requireNonNull(channel); Objects.requireNonNull(session);
    }
    @Override public int compareTo(SequenceDomain o) {
        int c = venue.compareTo(o.venue); if (c != 0) return c;
        c = channel.compareTo(o.channel); return c != 0 ? c : session.compareTo(o.session);
    }
}
