package com.example.marketdata.api;

import com.example.marketdata.application.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.*;

@RestController
@RequestMapping("/api/v1/historical")
public class ProcessingController {
    private final HistoricalProcessingService service;

    public ProcessingController(HistoricalProcessingService service) {
        this.service = service;
    }

    public record ProcessRequest(@NotBlank String path) {
    }

    @PostMapping("/process")
    public ResponseEntity<ProcessingResult> process(@RequestBody ProcessRequest request) throws Exception {
        Path p = Path.of(request.path()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(p)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(service.process(p));
    }
}
