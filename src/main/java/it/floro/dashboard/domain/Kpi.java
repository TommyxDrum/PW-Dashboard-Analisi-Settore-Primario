package it.floro.dashboard.domain;

/**
 * Record che rappresenta gli indicatori chiave di performance (KPI)
 * per il monitoraggio agricolo di una coltura.
 *
 * Raggruppa metriche di produttività, efficienza, profittabilità e rischi climatici
 * in un'unica struttura immutabile per facilità di trasporto e analisi.
 */
public record Kpi(
        // ============ METRICHE DI PRODUTTIVITÀ ============
        double yieldPerHa,                  // Resa: tonnellate per ettaro

        // ============ METRICHE DI EFFICIENZA ============
        double waterEfficiencyKgPerM3,      // Efficienza idrica: kg di prodotto per metro cubo d'acqua

        // ============ METRICHE ECONOMICHE ============
        double unitCostEurPerT,             // Costo unitario: euro per tonnellata di prodotto
        double unitMarginEurPerT,           // Margine unitario: euro per tonnellata di prodotto
        double unitCostLabor,               // Componente costo: manodopera (euro/tonnellata)
        double unitCostMaterials,           // Componente costo: materiali (euro/tonnellata)
        double unitCostWater,               // Componente costo: acqua (euro/tonnellata)

        // ============ METRICHE DI RISCHIO CLIMATICO ============
        double climateRiskIdx,              // Indice di rischio aggregato: intervallo [0.0 .. 1.0] (0=nessun rischio, 1=rischio massimo)
        double riskTemperature,             // Componente radar: rischio anomalie termiche
        double riskWaterStress,             // Componente radar: rischio stress idrico (siccità/allagamenti)
        double riskFrost                    // Componente radar: rischio gelate/brinate
) {}