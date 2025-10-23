package it.floro.dashboard.web.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO (Data Transfer Object) che rappresenta uno snapshot dei dati meteorologici
 * attuali disaggregati per area geografica.
 *
 * Responsabilità:
 * - Incapsulare dati meteo reali o simulati per il frontend
 * - Fornire informazioni ambientali per ogni area (Nord, Centro, Sud)
 * - Offrire contesto meteo per interpretare i KPI agricoli
 * - Esporre timestamp per tracciare la freschezza dei dati
 *
 * Utilizzo nel dashboard:
 * - Visualizzazione di condizioni meteo attuali per area
 * - Correlazione tra meteo e performance agricola (KPI)
 * - Alert basati su condizioni meteorologiche estreme
 * - Componente informativa per decisioni irrigue
 *
 * Struttura:
 * - dataPerArea: mappa area geografica → dati ambientali completi
 * - timestamp: LocalDateTime di acquisizione/generazione dei dati
 */
public record WeatherSnapshot(

        /**
         * Mappa che associa ogni area geografica ai rispettivi dati ambientali.
         *
         * Chiavi: "Nord", "Centro", "Sud"
         * Valori: EnvironmentalData con parametri meteo completi
         *
         * Struttura della mappa:
         * ```json
         * {
         *   "Nord": {
         *     "temperaturaC": 18.5,
         *     "umiditaPct": 65,
         *     "precipitazioniMm": 0.0,
         *     "ventoKmh": 12.3,
         *     "radiazioneSolare": 650,
         *     "condizioni": "Soleggiato"
         *   },
         *   "Centro": {...},
         *   "Sud": {...}
         * }
         * ```
         *
         * Note:
         * - Tutte e tre le aree sono sempre presenti nella mappa
         * - Ogni area ha il suo profilo climatico indipendente
         * - I dati vengono aggiornati periodicamente (ogni 10 secondi da WeatherService)
         */
        Map<String, EnvironmentalData> dataPerArea,

        /**
         * Timestamp di acquisizione/generazione dei dati meteorologici.
         *
         * Formato: LocalDateTime (es. 2025-10-23T14:30:45)
         *
         * Utilizzo:
         * - Client-side: calcolare freschezza dei dati ("Aggiornato 2 minuti fa")
         * - Caching: validare se il dato meteo è ancora attuale
         * - Logging: tracciare quando sono stati acquisiti i dati
         * - Correlazione temporale: sincronizzare con snapshot KPI per analisi
         *
         * Precision: al secondo (secondi e nanosecondi)
         */
        LocalDateTime timestamp
) {

    // ========================================================================
    // NESTED RECORD: EnvironmentalData
    // ========================================================================

    /**
     * Record che incapsula tutti i parametri meteorologici per un'area geografica.
     *
     * Responsabilità:
     * - Contenere i 6 parametri ambientali principali
     * - Fornire dati completi per visualizzazione e calcoli
     * - Supportare correlazioni con performance agricola
     *
     * Origine dei dati:
     * - WeatherService (simulati realisticamente)
     * - In futuro: API meteo esterna (OpenWeatherMap, Meteo.it, etc.)
     */
    public record EnvironmentalData(

            /**
             * Temperatura dell'aria in gradi Celsius.
             *
             * Range tipico: [-5°C, 35°C] in Italia
             * Precisione: 1 decimale (es. 22.5°C)
             *
             * Impatto agricolo:
             * - Determina adattabilità delle colture (tempSuitability)
             * - Influenza evapotraspirazione (irrigation demand)
             * - Rilevante per rischi (gelate, ondate di calore)
             *
             * Variabilità circadiana: minima al mattino, picco pomeridiano
             */
            double temperaturaC,

            /**
             * Umidità relativa dell'aria in percentuale [0-100].
             *
             * Range tipico: [25%, 95%] in condizioni agricole reali
             * Precisione: interi (es. 65%)
             *
             * Impatto agricolo:
             * - Influenza parassiti (funghi proliferano con umidità > 80%)
             * - Correlata inversamente con temperatura
             * - Rilevante per prevenzione malattie fogliari
             *
             * Correlazione: quando piove, umidità sale; quando è secco, cala
             */
            double umiditaPct,

            /**
             * Precipitazioni misurate o stimate in millimetri.
             *
             * Range tipico: [0, 50] mm in un evento giornaliero
             * Precisione: 1 decimale (es. 2.3 mm)
             *
             * Impatto agricolo:
             * - Determina disponibilità d'acqua e fabbisogno irriguo
             * - Fattore chiave nella formula di resa (rainFactor)
             * - Eccesso causa ristagni; carenza causa stress idrico
             * - Influenza umidità e condizioni meteo (Pioggia vs Soleggiato)
             *
             * Note: 0 mm significa assenza di precipitazioni;
             * > 1 mm è considerata pioggia significativa
             */
            double precipitazioniMm,

            /**
             * Velocità del vento in chilometri orari.
             *
             * Range tipico: [0, 40] km/h in condizioni agricole normali
             * Precisione: 1 decimale (es. 12.5 km/h)
             *
             * Impatto agricolo:
             * - Influenza evapotraspirazione (più vento = più perdita d'acqua)
             * - Importante per applicazione pesticidi/fungicidi
             * - Rischi: vento forte può danneggiare colture (laminazione)
             * - Determina raffiche e turbolenza atmosferica
             *
             * Calma (< 1 km/h): condizioni ottimali per diserbi/trattamenti
             * Forte (> 25 km/h): rischio di danno fisico alle piante
             */
            double ventoKmh,

            /**
             * Radiazione solare in Watt per metro quadro (W/m²).
             *
             * Range tipico: [0, 1000] W/m² durante il giorno
             * Precisione: interi (es. 650 W/m²)
             *
             * Impatto agricolo:
             * - Determina fotosintesi e produttività (fSolar factor)
             * - Influenza ciclo biologico e fenologia delle piante
             * - Bassa radiazione (giornate nuvolose) riduce resa
             * - Picco a mezzogiorno in giornate serene
             *
             * Variabilità:
             * - 0 W/m² = notte o giorno completamente nuvoloso
             * - 400-600 W/m² = giorno moderatamente nuvoloso
             * - 800+ W/m² = giorno soleggiato con cielo sereno
             *
             * Correlazione: la pioggia riduce la radiazione (canopy coverage)
             */
            double radiazioneSolare,

            /**
             * Descrizione testuale sintetica delle condizioni meteorologiche.
             *
             * Valori possibili:
             * - "Soleggiato": cielo sereno, bassa umidità, no pioggia
             * - "Nuvoloso": cielo coperto, alta umidità, no precipitazioni significative
             * - "Pioggia": precipitazioni attive (> 1 mm)
             *
             * Logica di determinazione (da WeatherService):
             * ```
             * if (precipitazioni > 1 mm) → "Pioggia"
             * else if (umidità > 70%) → "Nuvoloso"
             * else → "Soleggiato"
             * ```
             *
             * Utilizzo nel dashboard:
             * - Visualizzazione iconografica semplice (icone sole/nuvola/pioggia)
             * - Descrizione in linguaggio naturale per end-user
             * - Supporto per decision-making irriguo (pioggia in arrivo?)
             *
             * Nota: Le tre categorie coprono lo spazio fenologico dei giorni agricoli
             */
            String condizioni
    ) {}
}