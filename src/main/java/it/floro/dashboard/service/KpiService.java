package it.floro.dashboard.service;

import it.floro.dashboard.domain.SampleRecord;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Service centralizzato per il calcolo dei KPI agricoli e delle serie temporali.
 *
 * Fornisce tre livelli di analisi per ogni KPI:
 * 1. Valore aggregato: singolo valore su tutto il dataset (metodi calcola*)
 * 2. Serie temporale giornaliera: mappa date → valori medi giornalieri (metodi serieGiornaliera)
 * 3. Serie temporale annuale: mappa anni → valori medi annuali (metodi serieAnnuale)
 *
 * KPI supportati:
 * - RESA: tonnellate per ettaro (t/ha)
 * - EFFICIENZA IDRICA: kg di prodotto per metro cubo d'acqua (kg/m³)
 * - COSTO UNITARIO: euro per tonnellata (€/t)
 * - MARGINE UNITARIO: profitto per tonnellata (€/t)
 * - RISCHIO CLIMATICO: indice aggregato [0..1] da fattori meteo
 *
 * Offre inoltre metodi di aggregazione totale (somme, medie di prezzo).
 */
@Service
public class KpiService {

    // ========================================================================
    // SEZIONE 1: RESA PER ETTARO (t/ha)
    // ========================================================================

    /**
     * Calcola la resa media sul dataset: tonnellate di prodotto per ettaro.
     *
     * Formula: media( yieldT / surfaceHa ) per ogni record con surfaceHa > 0
     *
     * @param records Lista di campioni
     * @return Resa media in t/ha (0.0 se dataset vuoto)
     */
    public double calcolaResaMedia(List<SampleRecord> records) {
        return safeAverage(records, r -> {
            double ha = r.surfaceHa();
            return ha > 0 ? (r.yieldT() / ha) : Double.NaN;
        });
    }

    /**
     * Genera una serie temporale giornaliera della resa.
     *
     * @param records Lista di campioni
     * @return TreeMap<Data, ResaMedia> ordinate per data crescente
     */
    public Map<LocalDate, Double> serieResaGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, r -> {
            double ha = r.surfaceHa();
            return ha > 0 ? (r.yieldT() / ha) : Double.NaN;
        });
    }

    /**
     * Genera una serie temporale annuale della resa.
     *
     * @param records Lista di campioni
     * @return TreeMap<Anno, ResaMedia> ordinate per anno crescente
     */
    public Map<Integer, Double> serieResaAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, r -> {
            double ha = r.surfaceHa();
            return ha > 0 ? (r.yieldT() / ha) : Double.NaN;
        });
    }

    // ========================================================================
    // SEZIONE 2: EFFICIENZA IDRICA (kg/m³)
    // ========================================================================

    /**
     * Calcola l'efficienza idrica media: kg di prodotto per metro cubo d'acqua.
     *
     * Formula: media( (yieldT * 1000) / waterM3 ) per ogni record con waterM3 > 0
     * (moltiplicazione per 1000 per convertire da t a kg)
     *
     * @param records Lista di campioni
     * @return Efficienza idrica media in kg/m³ (0.0 se dataset vuoto)
     */
    public double calcolaEfficienzaIdrica(List<SampleRecord> records) {
        return safeAverage(records, r -> {
            double w = r.waterM3();
            return w > 0 ? (r.yieldT() * 1000.0 / w) : Double.NaN;
        });
    }

    /**
     * Genera una serie temporale giornaliera dell'efficienza idrica.
     *
     * @param records Lista di campioni
     * @return TreeMap<Data, EfficienzaMedia> ordinate per data crescente
     */
    public Map<LocalDate, Double> serieEfficienzaIdricaGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, r -> {
            double w = r.waterM3();
            return w > 0 ? (r.yieldT() * 1000.0 / w) : Double.NaN;
        });
    }

    /**
     * Genera una serie temporale annuale dell'efficienza idrica.
     *
     * @param records Lista di campioni
     * @return TreeMap<Anno, EfficienzaMedia> ordinate per anno crescente
     */
    public Map<Integer, Double> serieEfficienzaIdricaAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, r -> {
            double w = r.waterM3();
            return w > 0 ? (r.yieldT() * 1000.0 / w) : Double.NaN;
        });
    }

    // ========================================================================
    // SEZIONE 3: COSTO UNITARIO (€/t)
    // ========================================================================

    /**
     * Calcola il costo unitario medio: euro di spesa per tonnellata di prodotto.
     *
     * Formula: media( costEur / yieldT ) per ogni record con yieldT > 0
     *
     * @param records Lista di campioni
     * @return Costo unitario medio in €/t (0.0 se dataset vuoto)
     */
    public double calcolaCostoUnitario(List<SampleRecord> records) {
        return safeAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.costEur() / y) : Double.NaN;
        });
    }

    /**
     * Genera una serie temporale giornaliera del costo unitario.
     *
     * @param records Lista di campioni
     * @return TreeMap<Data, CostoMedia> ordinate per data crescente
     */
    public Map<LocalDate, Double> serieCostoUnitarioGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.costEur() / y) : Double.NaN;
        });
    }

    /**
     * Genera una serie temporale annuale del costo unitario.
     *
     * @param records Lista di campioni
     * @return TreeMap<Anno, CostoMedia> ordinate per anno crescente
     */
    public Map<Integer, Double> serieCostoUnitarioAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.costEur() / y) : Double.NaN;
        });
    }

    // ========================================================================
    // SEZIONE 4: MARGINE UNITARIO (€/t)
    // ========================================================================

    /**
     * Calcola il margine unitario medio: profitto per tonnellata di prodotto.
     *
     * Formula: media( priceEurT - (costEur / yieldT) ) per ogni record con yieldT > 0
     *
     * Interpetrazione:
     * - Margine positivo: il prezzo supera il costo, utile in euro per tonnellata
     * - Margine negativo: perdita, il costo supera il prezzo
     *
     * @param records Lista di campioni
     * @return Margine unitario medio in €/t (0.0 se dataset vuoto)
     */
    public double calcolaMargineUnitario(List<SampleRecord> records) {
        return safeAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.priceEurT() - (r.costEur() / y)) : Double.NaN;
        });
    }

    /**
     * Genera una serie temporale giornaliera del margine unitario.
     *
     * @param records Lista di campioni
     * @return TreeMap<Data, MargineMedia> ordinate per data crescente
     */
    public Map<LocalDate, Double> serieMargineUnitarioGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.priceEurT() - (r.costEur() / y)) : Double.NaN;
        });
    }

    /**
     * Genera una serie temporale annuale del margine unitario.
     *
     * @param records Lista di campioni
     * @return TreeMap<Anno, MargineMedia> ordinate per anno crescente
     */
    public Map<Integer, Double> serieMargineUnitarioAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, r -> {
            double y = r.yieldT();
            return y > 0 ? (r.priceEurT() - (r.costEur() / y)) : Double.NaN;
        });
    }

    // ========================================================================
    // SEZIONE 5: RISCHIO CLIMATICO (indice 0–1)
    // ========================================================================

    /**
     * Calcola l'indice di rischio climatico medio aggregato su tutto il dataset.
     *
     * Fattori considerati:
     * - Temperatura: valori lontani da [10°C, 40°C] aumentano il rischio
     * - Umidità: valori lontani da [30%, 90%] aumentano il rischio
     * - Precipitazioni: valori lontani da [0mm, 40mm] aumentano il rischio
     * - Irraggiamento solare: elevata radiazione aumenta il rischio
     *
     * @param records Lista di campioni
     * @return Indice di rischio medio [0.0 .. 1.0] (0.0 = nessun rischio, 1.0 = rischio massimo)
     */
    public double calcolaRischioClimatico(List<SampleRecord> records) {
        return safeAverage(records, this::rischioClimaticoPoint);
    }

    /**
     * Genera una serie temporale giornaliera dell'indice di rischio climatico.
     *
     * @param records Lista di campioni
     * @return TreeMap<Data, RischioMedio> ordinate per data crescente
     */
    public Map<LocalDate, Double> serieRischioClimaticoGiornaliera(List<SampleRecord> records) {
        return seriesAverage(records, this::rischioClimaticoPoint);
    }

    /**
     * Genera una serie temporale annuale dell'indice di rischio climatico.
     *
     * @param records Lista di campioni
     * @return TreeMap<Anno, RischioMedio> ordinate per anno crescente
     */
    public Map<Integer, Double> serieRischioClimaticoAnnuale(List<SampleRecord> records) {
        return seriesAnnualAverage(records, this::rischioClimaticoPoint);
    }

    // ========================================================================
    // SEZIONE 6: AGGREGAZIONI TOTALI
    // ========================================================================

    /**
     * Calcola la somma totale della produzione su tutto il dataset.
     *
     * @param records Lista di campioni
     * @return Totale produzione in tonnellate (0.0 se dataset vuoto)
     */
    public double sommaProduzione(List<SampleRecord> records) {
        return records.stream()
                .mapToDouble(SampleRecord::yieldT)
                .filter(Double::isFinite)      // Esclude NaN e infinito
                .sum();
    }

    /**
     * Calcola la somma totale delle superfici coltivate.
     *
     * @param records Lista di campioni
     * @return Totale superficie in ettari (0.0 se dataset vuoto)
     */
    public double sommaSuperficie(List<SampleRecord> records) {
        return records.stream()
                .mapToDouble(SampleRecord::surfaceHa)
                .filter(Double::isFinite)      // Esclude NaN e infinito
                .sum();
    }

    /**
     * Calcola la somma totale dell'acqua utilizzata.
     *
     * @param records Lista di campioni
     * @return Totale acqua in metri cubi (0.0 se dataset vuoto)
     */
    public double sommaAcqua(List<SampleRecord> records) {
        return records.stream()
                .mapToDouble(SampleRecord::waterM3)
                .filter(Double::isFinite)      // Esclude NaN e infinito
                .sum();
    }

    /**
     * Calcola il prezzo medio di mercato sul dataset.
     *
     * @param records Lista di campioni
     * @return Prezzo medio in €/t (0.0 se dataset vuoto)
     */
    public double prezzoMedio(List<SampleRecord> records) {
        return safeAverage(records, SampleRecord::priceEurT);
    }

    // ========================================================================
    // SEZIONE 7: METODI HELPER PRIVATI
    // ========================================================================

    /**
     * Calcola l'indice di rischio climatico per un singolo campione.
     *
     * Algoritmo (weighted scoring):
     * - Temperatura (40%): normalizza in [10°C, 40°C], 0=normale, 1=estremo
     * - Umidità (30%): inversa, 0 indica umidità ottimale [30%, 90%]
     * - Precipitazioni (20%): inversa, 0 indica pioggia ottimale [0mm, 40mm]
     * - Irraggiamento (10%): valore diretto, alto = più rischio
     * - Score finale: clamp in [0.0, 1.0]
     *
     * @param r SampleRecord
     * @return Indice di rischio [0.0 .. 1.0]
     */
    private double rischioClimaticoPoint(SampleRecord r) {
        double temp  = r.tempC();
        double hum   = r.humidityPct();
        double rain  = r.rainMm();
        double solar = r.solarIdx();

        // Calcolo score ponderato da 4 fattori
        double score = 0.0;
        score += normalize(temp, 10, 40) * 0.4;           // Temp: se fuori [10,40]°C, rischio sale
        score += (1 - normalize(hum, 30, 90)) * 0.3;      // Umidità: se fuori [30,90]%, rischio sale
        score += (1 - normalize(rain, 0, 40)) * 0.2;      // Pioggia: se fuori [0,40]mm, rischio sale
        score += clamp01(solar) * 0.1;                    // Sole: radiazione elevata = rischio

        return clamp01(score);
    }

    /**
     * Normalizza un valore in un range [min, max] a [0.0, 1.0].
     *
     * Comportamento:
     * - value <= min → ritorna 0.0
     * - value >= max → ritorna 1.0
     * - min < value < max → ritorna (value - min) / (max - min)
     * - NaN → ritorna 0.0
     *
     * @param value Valore da normalizzare
     * @param min Limite inferiore del range
     * @param max Limite superiore del range
     * @return Valore normalizzato in [0.0, 1.0]
     */
    private static double normalize(double value, double min, double max) {
        if (Double.isNaN(value)) return 0;
        if (value <= min) return 0;
        if (value >= max) return 1;
        return (value - min) / (max - min);
    }

    /**
     * Vincola un valore double in [0.0, 1.0].
     *
     * Comportamento:
     * - NaN o infinito → ritorna 0.0
     * - v < 0.0 → ritorna 0.0
     * - v > 1.0 → ritorna 1.0
     * - altrimenti → ritorna v
     *
     * @param v Valore da vincolare
     * @return Valore in [0.0, 1.0]
     */
    private static double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return Math.max(0, Math.min(1, v));
    }

    /**
     * Calcola la media in modo sicuro, escludendo NaN e infiniti.
     *
     * Procedura:
     * 1. Applica la funzione di trasformazione f a ogni record
     * 2. Filtra risultati non finiti (NaN, infinito)
     * 3. Calcola la media aritmetica
     * 4. Se lista vuota dopo filtro, ritorna 0.0
     *
     * @param records Lista di campioni
     * @param f Funzione di trasformazione record → double
     * @return Media aritmetica (0.0 se nessun dato valido)
     */
    private static double safeAverage(List<SampleRecord> records, ToDoubleFunction<SampleRecord> f) {
        return records.stream()
                .mapToDouble(f)
                .filter(d -> !Double.isNaN(d) && !Double.isInfinite(d))
                .average()
                .orElse(0.0);
    }

    /**
     * Genera una serie temporale giornaliera aggregando per data.
     *
     * Procedura:
     * 1. Raggruppa i record per data (groupingBy)
     * 2. Per ogni data, calcola la media della metrica (applicando f)
     * 3. Ritorna TreeMap ordinata per data crescente
     *
     * @param records Lista di campioni
     * @param f Funzione di trasformazione record → double
     * @return TreeMap<Data, Media> ordinata per data, vuota se nessun dato
     */
    private static Map<LocalDate, Double> seriesAverage(List<SampleRecord> records, ToDoubleFunction<SampleRecord> f) {
        // Raggruppa per data
        Map<LocalDate, List<SampleRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(SampleRecord::date));

        // Calcola media per ogni data
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

    /**
     * Genera una serie temporale annuale aggregando per anno.
     *
     * Procedura:
     * 1. Raggruppa i record per anno (estraendo year da r.date())
     * 2. Per ogni anno, calcola la media della metrica (applicando f)
     * 3. Ritorna TreeMap ordinata per anno crescente
     *
     * @param records Lista di campioni
     * @param f Funzione di trasformazione record → double
     * @return TreeMap<Anno, Media> ordinata per anno, vuota se nessun dato
     */
    private static Map<Integer, Double> seriesAnnualAverage(List<SampleRecord> records, ToDoubleFunction<SampleRecord> f) {
        // Raggruppa per anno
        Map<Integer, List<SampleRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(r -> r.date().getYear()));

        // Calcola media per ogni anno
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