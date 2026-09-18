package com.example.marketdata.venue;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class VenueAdapterRegistry {
    private final List<VenueAdapter> adapters;

    public VenueAdapterRegistry(List<VenueAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public VenueAdapter adapterFor(Path path) {
        return adapters.stream().filter(a -> a.supports(path)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No VenueAdapter supports " + path));
    }
}
