package it.floro.dashboard.domain;

public record Kpi(
        double yieldPerHa,
        double waterEfficiencyKgPerM3,
        double unitCostEurPerT,
        double unitMarginEurPerT,
        double climateRiskIdx,   // indice aggregato [0..1]
        double riskTemperature,  // componente radar
        double riskWaterStress,  // componente radar
        double riskFrost,         // componente radar
        double unitCostLabor,
        double unitCostMaterials,
        double unitCostWater
) {}
