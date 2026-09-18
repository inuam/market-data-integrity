package com.example.marketdata.quality;

public record ValidationResult(boolean valid, String reason) {
    public static ValidationResult ok(){ return new ValidationResult(true, "OK"); }
    public static ValidationResult invalid(String reason){ return new ValidationResult(false, reason); }
}
