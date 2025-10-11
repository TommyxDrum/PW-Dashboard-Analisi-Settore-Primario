
package it.floro.dashboard.domain;

public record Kpi(
        double yieldPerHa, //
        double waterEfficiencyKgPerM3,
        double unitCostEurPerT, //ok
        double unitMarginEurPerT,
        double climateRiskIdx
) {}
