package com.example.marketdata.provenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Append-only TSV audit log. Replace with JDBC/Kafka/object storage without changing pipeline code.
 */
@Repository
public final class FileProvenanceRepository implements ProvenanceRepository {
    private static final Logger log = LoggerFactory.getLogger(FileProvenanceRepository.class);

    private final Path path;

    public FileProvenanceRepository(@Value("${market-data.provenance-file:./data/provenance.tsv}") String file) {
        this.path = Path.of(file);
    }

    @Override
    public synchronized void append(ProvenanceEvent e) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            String row = String.join("\t", esc(e.occurredAt()), esc(e.type()), esc(e.domain().venue()), esc(e.domain().channel()),
                    esc(e.domain().session()), esc(e.sequenceFrom()), esc(e.sequenceTo()), esc(e.sourceFile()), esc(e.sourceOffset()), esc(e.detail())) + "\n";
            Files.writeString(path, row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.debug("Provenance event appended: type={} domain={} seqFrom={} seqTo={}", e.type(), e.domain(), e.sequenceFrom(), e.sequenceTo());
        } catch (IOException ex) {
            log.error("Failed to append provenance event type={} domain={} to {}", e.type(), e.domain(), path, ex);
            throw new UncheckedIOException(ex);
        }
    }

    private static String esc(Object v) {
        return v == null ? "" : v.toString().replace("\t", " ").replace("\n", " ");
    }
}
