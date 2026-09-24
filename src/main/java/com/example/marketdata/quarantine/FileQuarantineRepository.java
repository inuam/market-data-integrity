package com.example.marketdata.quarantine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Repository
public final class FileQuarantineRepository implements QuarantineRepository {
    private static final Logger log = LoggerFactory.getLogger(FileQuarantineRepository.class);

    private final Path path;

    public FileQuarantineRepository(@Value("${market-data.quarantine-file:./data/quarantine.tsv}") String file) {
        this.path = Path.of(file);
    }

    @Override
    public synchronized void save(QuarantinedRecord q) {
        var r = q.record();
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            String row = String.join("\t", esc(q.quarantinedAt()), esc(r.domain().venue()), esc(r.domain().channel()), esc(r.domain().session()),
                    esc(r.sequence()), esc(r.eventTimeNanos()), esc(r.instrument()), esc(r.priceMantissa()), esc(r.quantity()), esc(r.sourceFile()),
                    esc(r.sourceOffset()), esc(q.reason()), esc(q.validator())) + "\n";
            Files.writeString(path, row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.warn("Record quarantined: domain={} sequence={} reason={} validator={}", r.domain(), r.sequence(), q.reason(), q.validator());
        } catch (IOException ex) {
            log.error("Failed to persist quarantined record: domain={} sequence={} to {}", r.domain(), r.sequence(), path, ex);
            throw new UncheckedIOException(ex);
        }
    }

    private static String esc(Object v) {
        return v == null ? "" : v.toString().replace("\t", " ").replace("\n", " ");
    }
}
