package com.example.marketdata.boundary;

import com.example.marketdata.domain.SequenceDomain;
import com.example.marketdata.domain.SessionBoundary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Loads venue-neutral boundaries from a CSV file:
 * venue,channel,session,firstSequence,lastSequence
 * The file is optional; missing file means boundaries are unknown.
 */
@Component
public final class ConfiguredSessionBoundaryProvider implements SessionBoundaryProvider {
    private final Map<SequenceDomain, SessionBoundary> boundaries;

    public ConfiguredSessionBoundaryProvider(@Value("${market-data.boundaries-file:./config/session-boundaries.csv}") String file) {
        this.boundaries = load(Path.of(file));
    }

    @Override
    public Optional<SessionBoundary> findBoundary(SequenceDomain domain) {
        return Optional.ofNullable(boundaries.get(domain));
    }

    private static Map<SequenceDomain, SessionBoundary> load(Path path) {
        if (!Files.exists(path)) return Map.of();
        Map<SequenceDomain, SessionBoundary> result = new HashMap<>();
        try (var lines = Files.lines(path)) {
            lines.skip(1).filter(s -> !s.isBlank() && !s.startsWith("#")).forEach(line -> {
                String[] p = line.split(",", -1);
                if (p.length != 5) throw new IllegalArgumentException("Bad boundary row: " + line);
                SequenceDomain d = new SequenceDomain(p[0].trim(), p[1].trim(), p[2].trim());
                result.put(d, new SessionBoundary(d, Long.parseLong(p[3].trim()), Long.parseLong(p[4].trim())));
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read boundaries " + path, e);
        }
        return Map.copyOf(result);
    }
}
