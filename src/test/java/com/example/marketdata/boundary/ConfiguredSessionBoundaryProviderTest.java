package com.example.marketdata.boundary;

import com.example.marketdata.domain.SequenceDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredSessionBoundaryProviderTest {

    @TempDir
    Path tempDir;

    private Path csv(String... lines) throws IOException {
        Path file = tempDir.resolve("session-boundaries.csv");
        Files.writeString(file, String.join("\n", lines));
        return file;
    }

    @Test
    void shouldReturnConfiguredBoundaryForAKnownDomain() throws IOException {
        // Given
        Path file = csv(
                "venue,channel,session,firstSequence,lastSequence",
                "LSE,A,2026-09-14,100,200");
        var provider = new ConfiguredSessionBoundaryProvider(file.toString());

        // When
        var boundary = provider.findBoundary(new SequenceDomain("LSE", "A", "2026-09-14"));

        // Then
        assertThat(boundary).isPresent();
        assertThat(boundary.get().firstExpectedSequence()).isEqualTo(100);
        assertThat(boundary.get().lastExpectedSequence()).isEqualTo(200);
    }

    @Test
    void shouldReturnEmptyWhenBoundariesFileDoesNotExist() {
        // Given
        Path missing = tempDir.resolve("does-not-exist.csv");
        var provider = new ConfiguredSessionBoundaryProvider(missing.toString());

        // When
        var boundary = provider.findBoundary(new SequenceDomain("LSE", "A", "2026-09-14"));

        // Then
        assertThat(boundary).isEmpty();
    }

    @Test
    void shouldReturnEmptyForADomainNotPresentInTheFile() throws IOException {
        // Given
        Path file = csv(
                "venue,channel,session,firstSequence,lastSequence",
                "LSE,A,2026-09-14,100,200");
        var provider = new ConfiguredSessionBoundaryProvider(file.toString());

        // When
        var boundary = provider.findBoundary(new SequenceDomain("NYSE", "B", "2026-09-14"));

        // Then
        assertThat(boundary).isEmpty();
    }

    @Test
    void shouldKeepDistinctDomainsIndependent() throws IOException {
        // Given
        Path file = csv(
                "venue,channel,session,firstSequence,lastSequence",
                "LSE,A,2026-09-14,100,200",
                "NYSE,B,2026-09-14,50,75");
        var provider = new ConfiguredSessionBoundaryProvider(file.toString());

        // When
        var lse = provider.findBoundary(new SequenceDomain("LSE", "A", "2026-09-14"));
        var nyse = provider.findBoundary(new SequenceDomain("NYSE", "B", "2026-09-14"));

        // Then
        assertThat(lse).isPresent();
        assertThat(lse.get().firstExpectedSequence()).isEqualTo(100);
        assertThat(nyse).isPresent();
        assertThat(nyse.get().firstExpectedSequence()).isEqualTo(50);
    }

    @Test
    void shouldSkipBlankLinesAndCommentLines() throws IOException {
        // Given
        Path file = csv(
                "venue,channel,session,firstSequence,lastSequence",
                "",
                "# a comment describing the row below",
                "LSE,A,2026-09-14,100,200",
                "   ");
        var provider = new ConfiguredSessionBoundaryProvider(file.toString());

        // When
        var boundary = provider.findBoundary(new SequenceDomain("LSE", "A", "2026-09-14"));

        // Then
        assertThat(boundary).isPresent();
    }

    @Test
    void shouldTrimWhitespaceAroundFieldValues() throws IOException {
        // Given
        Path file = csv(
                "venue,channel,session,firstSequence,lastSequence",
                " LSE , A , 2026-09-14 , 100 , 200 ");
        var provider = new ConfiguredSessionBoundaryProvider(file.toString());

        // When
        var boundary = provider.findBoundary(new SequenceDomain("LSE", "A", "2026-09-14"));

        // Then
        assertThat(boundary).isPresent();
        assertThat(boundary.get().firstExpectedSequence()).isEqualTo(100);
    }

    @Test
    void shouldAlwaysTreatTheFirstLineAsAHeaderAndSkipIt() throws IOException {
        // Given: no real header row - the first data row is unconditionally dropped as if it were one
        Path file = csv(
                "LSE,A,2026-09-14,100,200",
                "NYSE,B,2026-09-14,50,75");
        var provider = new ConfiguredSessionBoundaryProvider(file.toString());

        // When
        var lse = provider.findBoundary(new SequenceDomain("LSE", "A", "2026-09-14"));
        var nyse = provider.findBoundary(new SequenceDomain("NYSE", "B", "2026-09-14"));

        // Then
        assertThat(lse).isEmpty();
        assertThat(nyse).isPresent();
    }

    @Test
    void shouldFailFastOnAMalformedRow() throws IOException {
        // Given
        Path file = csv(
                "venue,channel,session,firstSequence,lastSequence",
                "LSE,A,2026-09-14,100");

        // When / Then
        assertThatThrownBy(() -> new ConfiguredSessionBoundaryProvider(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bad boundary row");
    }
}
