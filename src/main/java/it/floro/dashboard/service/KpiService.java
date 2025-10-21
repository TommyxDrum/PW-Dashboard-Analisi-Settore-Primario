package it.floro.dashboard.service;

import it.floro.dashboard.domain.SampleRecord;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Service concreto che centralizza i calcoli dei KPI e le serie temporali.
 * ARRICCHITO con metodi per serie annuali e aggregazioni totali.
 */
@Service
public class KpiService {

    // =========================
    // RESA per ettaro (t/ha)
    // =========================

    public double calcolaResaMedia(List<SampleRecord> records) {
        return safeAverage(records, r -> {
            double ha = r.surfaceHa();
            return ha > 0 ? (r.yieldT() / ha) : Double.NaN;
        });
    }

    public Map<LocalDate, Double> serieResaGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, r -> {
            double ha = r.surfaceHa();
            return ha > 0 ? (r.yieldT() / ha) : Double.NaN;
        });
    }

    public Map<Integer, Double> serieResaAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, r -> {
            double ha = r.surfaceHa();
            return ha > 0 ? (r.yieldT() / ha) : Double.NaN;
        });
    }

    // =========================
    // EFFICIENZA IDRICA (kg/m³)
    // =========================

    public double calcolaEfficienzaIdrica(List<SampleRecord> records) {
        return safeAverage(records, r -> {
            double w = r.waterM3();
            return w > 0 ? (r.yieldT() * 1000.0 / w) : Double.NaN;
        });
    }

    public Map<LocalDate, Double> serieEfficienzaIdricaGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, r -> {
            double w = r.waterM3();
            return w > 0 ? (r.yieldT() * 1000.0 / w) : Double.NaN;
        });
    }

    public Map<Integer, Double> serieEfficienzaIdricaAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, r -> {
            double w = r.waterM3();
            return w > 0 ? (r.yieldT() * 1000.0 / w) : Double.NaN;
        });
    }

    // =========================
    // COSTO UNITARIO (€/t)
    // =========================

    public double calcolaCostoUnitario(List<SampleRecord> records) {
        return safeAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.costEur() / y) : Double.NaN;
        });
    }

    public Map<LocalDate, Double> serieCostoUnitarioGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.costEur() / y) : Double.NaN;
        });
    }

    public Map<Integer, Double> serieCostoUnitarioAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.costEur() / y) : Double.NaN;
        });
    }

    // =========================
    // MARGINE UNITARIO (€/t)
    // =========================

    public double calcolaMargineUnitario(List<SampleRecord> records) {
        return safeAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.priceEurT() - (r.costEur() / y)) : Double.NaN;
        });
    }

    public Map<LocalDate, Double> serieMargineUnitarioGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.priceEurT() - (r.costEur() / y)) : Double.NaN;
        });
    }

    public Map<Integer, Double> serieMargineUnitarioAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.priceEurT() - (r.costEur() / y)) : Double.NaN;
        });
    }

    // =========================
    // RISCHIO CLIMATICO (0–1)
    // =========================

    public double calcolaRischioClimatico(List<SampleRecord> records) {
        return safeAverage(records, this::rischioClimaticoPoint);
    }

    public Map<LocalDate, Double> serieRischioClimaticoGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, this::rischioClimaticoPoint);
    }

    public Map<Integer, Double> serieRischioClimaticoAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, this::rischioClimaticoPoint);
    }

    // =========================
    // AGGREGAZIONI TOTALI
    // =========================

    public double sommaProduzione(List<SampleRecord> records) {
        return records.stream()
                .mapToDouble(SampleRecord::yieldT)
                .filter(Double::isFinite)
                .sum();
    }

    public double sommaSuperficie(List<SampleRecord> records) {
        return records.stream()
                .mapToDouble(SampleRecord::surfaceHa)
                .filter(Double::isFinite)
                .sum();
    }

    public double sommaAcqua(List<SampleRecord> records) {
        return records.stream()
                .mapToDouble(SampleRecord::waterM3)
                .filter(Double::isFinite)
                .sum();
    }

    public double prezzoMedio(List<SampleRecord> records) {
        return safeAverage(records, SampleRecord::priceEurT);
    }

    // =========================
    // Helpers interni
    // =========================

    private double rischioClimaticoPoint(SampleRecord r) {
        double temp  = r.tempC();
        double hum   = r.humidityPct();
        double rain  = r.rainMm();
        double solar = r.solarIdx();

        double score = 0.0;
        score += normalize(temp, 10, 40) * 0.4;
        score += (1 - normalize(hum, 30, 90)) * 0.3;
        score += (1 - normalize(rain, 0, 40)) * 0.2;
        score += clamp01(solar) * 0.1;

        return clamp01(score);
    }

    private static double normalize(double value, double min, double max) {
        if (Double.isNaN(value)) return 0;
        if (value <= min) return 0;
        if (value >= max) return 1;
        return (value - min) / (max - min);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return Math.max(0, Math.min(1, v));
    }

    private static double safeAverage(List<SampleRecord> records, ToDoubleFunction<SampleRecord> f) {
        return records.stream()
                .mapToDouble(f)
                .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                .average()
                .orElse(0.0);
    }

    private static Map<LocalDate, Double> seriesAverage(List<SampleRecord> records, ToDoubleFunction<SampleRecord> f) {
        Map<LocalDate, List<SampleRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(SampleRecord::date));

        Map<LocalDate, Double> out = new TreeMap<>();
        for (Map.Entry<LocalDate, List<SampleRecord>> e : grouped.entrySet()) {
            double avg = e.getValue().stream()
                    .mapToDouble(f)
                    .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                    .average()
                    .orElse(0.0);
            out.put(e.getKey(), avg);
        }
        return out;
    }

    private static Map<Integer, Double> seriesAnnualAverage(List<SampleRecord> records, ToDoubleFunction<SampleRecord> f) {
        Map<Integer, List<SampleRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(r -> r.date().getYear()));

        Map<Integer, Double> out = new TreeMap<>();
        for (Map.Entry<Integer, List<SampleRecord>> e : grouped.entrySet()) {
            double avg = e.getValue().stream()
                    .mapToDouble(f)
                    .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                    .average()
                    .orElse(0.0);
            out.put(e.getKey(), avg);
        }
        return out;
    }
}