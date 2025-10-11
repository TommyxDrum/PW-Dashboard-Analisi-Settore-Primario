package it.floro.dashboard.domain;

import java.time.LocalDate;

public record SampleRecord(
        LocalDate date,
        String area,
        String field,
        String crop,
        double surfaceHa,
        double tempC,
        double humidityPct,
        double rainMm,
        double solarIdx,
        double yieldT,
        double waterM3,
        double costEur,
        double priceEurT
) {}