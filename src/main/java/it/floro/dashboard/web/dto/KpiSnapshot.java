package it.floro.dashboard.web.dto;

import java.util.Map;

public record KpiSnapshot(
        // KPI principali
        double resaMedia,
        double efficienzaIdrica,
        double costoUnitario,
        double margineUnitario,
        double rischioClimatico,

        // Dati per grafici (per area)
        Map<String, Double> resaPerArea,      // {"Nord": 5.2, "Centro": 4.8, ...}
        Map<String, Double> efficienzaPerArea,
        Map<String, Double> costoPerArea,
        Map<String, Double> marginePerArea,
        Map<String, Double> rischioPerArea,

        long timestamp
) {
    public KpiSnapshot(double resaMedia, double efficienzaIdrica,
                       double costoUnitario, double margineUnitario,
                       double rischioClimatico,
                       Map<String, Double> resaPerArea,
                       Map<String, Double> efficienzaPerArea,
                       Map<String, Double> costoPerArea,
                       Map<String, Double> marginePerArea,
                       Map<String, Double> rischioPerArea) {
        this(resaMedia, efficienzaIdrica, costoUnitario, margineUnitario,
                rischioClimatico, resaPerArea, efficienzaPerArea, costoPerArea,
                marginePerArea, rischioPerArea, System.currentTimeMillis());
    }
}