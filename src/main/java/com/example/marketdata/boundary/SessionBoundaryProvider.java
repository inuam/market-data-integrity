package com.example.marketdata.boundary;

import com.example.marketdata.domain.SequenceDomain;
import com.example.marketdata.domain.SessionBoundary;

import java.util.Optional;

/**
 * Venue-neutral source of authoritative session sequence boundaries.
 */
public interface SessionBoundaryProvider {
    Optional<SessionBoundary> findBoundary(SequenceDomain domain);
}
