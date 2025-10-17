package it.floro.dashboard.service;

import it.floro.dashboard.domain.Kpi;
import it.floro.dashboard.domain.SampleRecord;
import org.springframework.stereotype.Service;

@Service
public class KpiService {

    /** Calcola tutti i KPI derivati da un singolo SampleRecord. */
    public Kpi compute(SampleRecord r) {
        // KPI "classici"
        double yieldPerHa = safeDiv(r.yieldT(), r.surfaceHa());
        double waterEff   = safeDiv(r.yieldT() * 1000.0, r.waterM3()); // kg/m³
        double unitCost   = safeDiv(r.costEur(), r.yieldT());
        double unitMargin = r.priceEurT() - unitCost;

        // Fattori di rischio (ora temperatura dipendente dalla coltura)
        String crop = (r.crop() == null || r.crop().isBlank()) ? "Grano duro" : r.crop().trim();
        double riskTemp   = calculateRiskTemperature(r.tempC(), crop);
        double riskWater  = calculateRiskWaterStress(r.rainMm());
        double riskFrost  = calculateRiskFrost(r.tempC());

        // Indice aggregato
        double riskIndex  = calculateRiskIndex(riskTemp, riskWater, riskFrost);

        // --- NUOVA MODIFICA: Scomposizione dei costi per l'analisi di dettaglio ---
        double totalCost = r.costEur();
        double yield = r.yieldT();

        // Simuliamo una scomposizione del costo totale in percentuali.
        // Questi valori possono essere resi più complessi in futuro.
        double unitCostLabor     = safeDiv(totalCost * 0.5, yield); // 50% del costo è manodopera
        double unitCostMaterials = safeDiv(totalCost * 0.3, yield); // 30% del costo è materiali
        double unitCostWater     = safeDiv(totalCost * 0.2, yield); // 20% del costo è acqua/altro

        return new Kpi(
                yieldPerHa,
                waterEff,
                unitCost,
                unitMargin,
                riskIndex,
                riskTemp,
                riskWater,
                riskFrost,
                // Aggiunta dei nuovi campi per i costi
                unitCostLabor,
                unitCostMaterials,
                unitCostWater
        );
    }

    // --- Metodi di calcolo per i rischi ---

    /**
     * Rischio termico “smooth” per coltura:
     * - ~0 nella fascia ottimale [minOk..maxOk]
     * - cresce verso caldo e freddo con pesi 0.6 (hot) e 0.4 (cold)
     */
    private double calculateRiskTemperature(double t, String crop) {
        double minOk, maxOk, maxHot, minCold;
        switch (crop) {
            case "Mais" -> { minOk = 22; maxOk = 28; maxHot = 40; minCold = 0; }
            case "Olivo" -> { minOk = 20; maxOk = 28; maxHot = 42; minCold = 0; }
            case "Vite" -> { minOk = 18; maxOk = 26; maxHot = 40; minCold = 0; }
            default /* Grano duro */ -> { minOk = 16; maxOk = 24; maxHot = 38; minCold = 0; }
        }
        double cold = clamp01((minOk - t) / (minOk - minCold + 1e-9));
        double hot  = clamp01((t - maxOk) / (maxHot - maxOk + 1e-9));
        return clamp01(0.6 * hot + 0.4 * cold);
    }

    /** Stress idrico: se piove >= 10mm rischio ~0, altrimenti sale linearmente. */
    private double calculateRiskWaterStress(double rainMm) {
        final double MAX_RAIN_FOR_RISK = 10.0; // >= 10mm -> rischio 0
        if (rainMm >= MAX_RAIN_FOR_RISK) return 0.0;
        return clamp01(1.0 - (rainMm / MAX_RAIN_FOR_RISK));
    }

    /** Rischio gelo: sotto ~2°C cresce fino a 1 a 0°C. */
    private double calculateRiskFrost(double tempC) {
        final double MIN_TEMP_FROST = 0.0;
        final double MAX_TEMP_FROST = 2.0; // rischio sotto ~2°C
        if (tempC >= MAX_TEMP_FROST) return 0.0;
        if (tempC <= MIN_TEMP_FROST) return 1.0;
        double v = 1.0 - (tempC - MIN_TEMP_FROST) / (MAX_TEMP_FROST - MIN_TEMP_FROST);
        return clamp01(v);
    }

    /** Media pesata dei rischi componenti. */
    private double calculateRiskIndex(double riskTemp, double riskWater, double riskFrost) {
        final double WEIGHT_TEMP  = 0.4;
        final double WEIGHT_WATER = 0.4;
        final double WEIGHT_FROST = 0.2;
        double totalRisk = (riskTemp * WEIGHT_TEMP)
                + (riskWater * WEIGHT_WATER)
                + (riskFrost * WEIGHT_FROST);
        return clamp01(totalRisk);
    }

    // --- Helper ---

    private double safeDiv(double a, double b) {
        return (b == 0.0) ? 0.0 : a / b; // evita NaN/Infinity
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}