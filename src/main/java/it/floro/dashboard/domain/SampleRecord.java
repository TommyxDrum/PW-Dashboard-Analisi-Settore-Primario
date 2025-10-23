package it.floro.dashboard.domain;

import java.time.LocalDate;

/**
 * Record che rappresenta un campione di dati raccolti da un campo agricolo
 * in una specifica data.
 *
 * Raggruppa informazioni meteorologiche, produttive ed economiche relative
 * a una singola misurazione per supportare l'analisi storica e il calcolo dei KPI.
 */
public record SampleRecord(
        // ============ INFORMAZIONI DI TRACCIAMENTO ============
        LocalDate date,                     // Data del campionamento
        String area,                        // Area geografica: "Nord", "Centro", "Sud"
        String field,                       // Identificatore univoco del campo
        String crop,                        // Tipo di coltura monitorata

        // ============ CARATTERISTICHE DEL CAMPO ============
        double surfaceHa,                   // Superficie del campo in ettari

        // ============ DATI METEOROLOGICI ============
        double tempC,                       // Temperatura media: gradi Celsius
        double humidityPct,                 // Umidità relativa: percentuale [0..100]
        double rainMm,                      // Precipitazioni: millimetri
        double solarIdx,                    // Indice di irraggiamento solare: valore normalizzato

        // ============ DATI DI PRODUZIONE ============
        double yieldT,                      // Resa/Prodotto raccolto: tonnellate
        double waterM3,                     // Acqua utilizzata: metri cubi

        // ============ DATI ECONOMICI ============
        double costEur,                     // Costo totale di coltivazione: euro
        double priceEurT                    // Prezzo di mercato: euro per tonnellata
) {}