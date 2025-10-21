package it.floro.dashboard.web.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record WeatherSnapshot(
        Map<String, EnvironmentalData> dataPerArea, // {"Nord": {...}, "Centro": {...}}
        LocalDateTime timestamp
) {
    public record EnvironmentalData(
            double temperaturaC,
            double umiditaPct,
            double precipitazioniMm,
            double ventoKmh,
            double radiazioneSolare,
            String condizioni // "Soleggiato", "Nuvoloso", "Pioggia"
    ) {}
}